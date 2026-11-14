package com.lucyago.instinctbledetector;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import android.util.SparseArray;

/** Passive BLE advertisement scanner. NEVER calls connectGatt(), pairing or writes. */
public class MainActivity extends Activity {
    private static final String TAG = "InstinctBLE";
    private static final String TARGET_UUID = "089810cc-ef89-11e9-81b4-2a2ae2dbcce4";
    private static final String TARGET_PREFIX = "E46447A3";
    private static final String TARGET_MAC = "E4:64:47:A3:0C:A0";
    // Candidate from iPhone Bluetooth list; identity not yet confirmed.
    private static final String NEW_CANDIDATE_MAC = "F4:FB:F5:D5:15:1F";
    private static final int PERMISSION_REQUEST = 9;
    private static final long[] SCAN_DURATIONS_MS = {90000L, 300000L, 600000L, 1800000L};
    private static final String[] SCAN_LABELS = {"90 segundos", "5 minutos", "10 minutos", "30 minutos"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, String> devices = new LinkedHashMap<>();
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private TextView status, results, summary;
    private Button button;
    private Spinner durationSelector;
    private long activeDurationMs = 90000L;
    private BufferedWriter csvWriter;
    private String csvName = "-";
    private int csvCaptures = 0;
    private int totalAdvertisements = 0;
    private int candidateAdvertisements = 0;
    private static final int MAX_CSV_ROWS = 10000;
    private long lastUiUpdateMs = 0;
    private String previousCandidate = null;
    private final Runnable timeout = this::stopScan;

    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            process(result);
        }
        @Override public void onBatchScanResults(java.util.List<ScanResult> batch) {
            for (ScanResult result : batch) process(result);
        }
        @Override public void onScanFailed(int errorCode) {
            Log.e(TAG, "SCAN_FAILED code=" + errorCode);
            runOnUiThread(() -> { status.setText("Error de escaneo: " + errorCode); stopScan(); });
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 36, 36, 20);
        root.setBackgroundColor(0xff101820);
        status = new TextView(this);
        status.setTextColor(0xffffffff);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setText("Listo para escanear");
        root.addView(status);
        durationSelector = new Spinner(this);
        ArrayAdapter<String> durationAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SCAN_LABELS);
        durationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        durationSelector.setAdapter(durationAdapter);
        durationSelector.setSelection(0);
        root.addView(durationSelector);
        button = new Button(this);
        button.setText("ESCANEAR");
        button.setOnClickListener(v -> { if (scanning) stopScan(); else startScan(); });
        root.addView(button);
        summary = new TextView(this);
        summary.setTextColor(0xffffffff);
        summary.setTextSize(14);
        summary.setGravity(Gravity.CENTER);
        summary.setText("Dispositivos: 0 | Instinct: 0\nAnuncios: 0 | CSV: 0");
        root.addView(summary);
        ScrollView scroll = new ScrollView(this);
        results = new TextView(this);
        results.setTextColor(0xffc7e8ff);
        results.setTextSize(13);
        results.setText("Los dispositivos aparecerán aquí.\n");
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void startScan() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_REQUEST);
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { status.setText("Activa Bluetooth"); return; }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { status.setText("Escáner BLE no disponible"); return; }
        if (!openCsv()) return;
        activeDurationMs = SCAN_DURATIONS_MS[durationSelector.getSelectedItemPosition()];
        durationSelector.setEnabled(false);
        devices.clear();
        csvCaptures = 0;
        totalAdvertisements = 0;
        candidateAdvertisements = 0;
        lastUiUpdateMs = 0;
        summary.setText("Dispositivos: 0 | Instinct: 0\nAnuncios: 0 | CSV: 0");
        results.setText("Buscando anuncios BLE...\n");
        // The previous match remains available on screen even if scan restarts.
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        try {
            scanner.startScan(null, settings, callback);
            scanning = true;
            button.setText("DETENER");
            status.setText("Escaneando: " + SCAN_LABELS[durationSelector.getSelectedItemPosition()]);
            Log.i(TAG, "SCAN_STARTED passive=true duration_ms=" + activeDurationMs);
            handler.postDelayed(timeout, activeDurationMs);
        } catch (SecurityException | IllegalStateException ex) {
            status.setText("No se pudo escanear: " + ex.getClass().getSimpleName());
            closeCsv();
            durationSelector.setEnabled(true);
            Log.e(TAG, "SCAN_EXCEPTION", ex);
        }
    }

    private void process(ScanResult r) {
        if (r == null || r.getDevice() == null) return;
        String mac = r.getDevice().getAddress();
        String name = (r.getScanRecord() != null ? r.getScanRecord().getDeviceName() : null);
        if (name == null) name = "(sin nombre)";
        String uuids = (r.getScanRecord() != null && r.getScanRecord().getServiceUuids() != null)
                ? r.getScanRecord().getServiceUuids().toString() : "[]";
        String serviceData = (r.getScanRecord() != null && r.getScanRecord().getServiceData() != null)
                ? r.getScanRecord().getServiceData().keySet().toString() : "[]";
        String manufacturerIds = (r.getScanRecord() != null && r.getScanRecord().getManufacturerSpecificData() != null)
                ? manufacturerIds(r) : "[]";
        boolean candidate = name.toUpperCase(Locale.ROOT).contains(TARGET_PREFIX)
                || mac.equalsIgnoreCase(TARGET_MAC)
                || mac.equalsIgnoreCase(NEW_CANDIDATE_MAC)
                || uuids.toLowerCase(Locale.ROOT).contains(TARGET_UUID)
                || serviceData.toLowerCase(Locale.ROOT).contains(TARGET_UUID);
        String line = (candidate ? (mac.equalsIgnoreCase(NEW_CANDIDATE_MAC) ? "*** CANDIDATO NUEVO (sin confirmar) *** " : "*** POSIBLE INSTINCT *** ") : "")
                + "name=" + name + " mac=" + mac + " rssi=" + r.getRssi()
                + " uuids=" + uuids + " serviceData=" + serviceData
                + " manufacturers=" + manufacturerIds;
        if (candidate) {
            previousCandidate = line;
            candidateAdvertisements++;
        }
        totalAdvertisements++;
        if (csvCaptures < MAX_CSV_ROWS) saveCandidate(r, name, mac, uuids, serviceData, manufacturerIds);
        // One log entry per device/changed data, rather than every advertisement.
        String old = devices.put(mac, line);
        if (!line.equals(old)) Log.i(TAG, line);
        if (devices.size() > 150) { String first = devices.keySet().iterator().next(); devices.remove(first); }
        if (System.currentTimeMillis() - lastUiUpdateMs > 1000) {
            lastUiUpdateMs = System.currentTimeMillis();
            runOnUiThread(this::updateDisplay);
        }
    }

    private String manufacturerIds(ScanResult r) {
        android.util.SparseArray<byte[]> array = r.getScanRecord().getManufacturerSpecificData();
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) out.append(',');
            out.append(array.keyAt(i));
        }
        return out.append(']').toString();
    }

    private void updateDisplay() {
        int matches = 0;
        StringBuilder all = new StringBuilder();
        for (String line : devices.values()) {
            if (line.startsWith("***")) { matches++; all.insert(0, line + "\n\n"); }
        }
        if (matches == 0 && previousCandidate != null) {
            all.append("ULTIMO INSTINCT (escaneo anterior):\n")
                    .append(previousCandidate).append("\n\n");
        }
        int shown = 0;
        for (String line : devices.values()) {
            if (!line.startsWith("***") && shown++ < 20) all.append(line).append("\n\n");
        }
        status.setText(scanning ? "Escaneando..." : "Finalizado");
        summary.setText("Dispositivos: " + devices.size() + " | Instinct: " + matches
                + "\nAnuncios: " + totalAdvertisements + " | CSV: " + csvCaptures
                + "\nCandidatos: " + candidateAdvertisements);
        all.append("CSV: ").append(csvName)
                .append(" | anuncios BLE guardados: ").append(csvCaptures)
                .append("\nLímite CSV: ").append(MAX_CSV_ROWS);
        results.setText(all.toString());
    }

    private void stopScan() {
        handler.removeCallbacks(timeout);
        if (scanning && scanner != null) {
            try { scanner.stopScan(callback); } catch (SecurityException ignored) { }
        }
        scanning = false;
        durationSelector.setEnabled(true);
        closeCsv();
        button.setText("ESCANEAR");
        updateDisplay();
        status.setText("Finalizado");
        Log.i(TAG, "SCAN_STOPPED count=" + devices.size());
    }


    // Capture all nearby BLE advertisements, including unknown sensor identities.
    // Passive scan only; limit CSV rows to bound storage usage.
    private boolean openCsv() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            status.setText("No hay almacenamiento para CSV");
            return false;
        }
        csvName = "ble_todos_v13_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ".csv";
        try {
            csvWriter = new BufferedWriter(new FileWriter(new File(dir, csvName)));
            csvWriter.write("timestamp_local,name,mac,rssi,uuids,service_uuids,manufacturer_ids,raw_scan_record_hex,service_data_hex,manufacturer_data_hex\n");
            csvWriter.flush();
            return true;
        } catch (IOException e) {
            csvWriter = null;
            status.setText("Error al crear CSV");
            Log.e(TAG, "CSV_OPEN", e);
            return false;
        }
    }

    private synchronized void saveCandidate(ScanResult r, String name, String mac, String uuids,
                               String serviceUuids, String manufacturerIds) {
        if (csvWriter == null) return;
        ScanRecord rec = r.getScanRecord();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
        String[] cells = {
                timestamp, name, mac, Integer.toString(r.getRssi()), uuids,
                serviceUuids, manufacturerIds,
                rec == null ? "" : hex(rec.getBytes()),
                serviceDataHex(rec), manufacturerDataHex(rec)
        };
        try {
            for (int i = 0; i < cells.length; i++) {
                if (i != 0) csvWriter.write(',');
                csvWriter.write(csv(cells[i]));
            }
            csvWriter.write("\n");
            csvWriter.flush();
            csvCaptures++;
            Log.i(TAG, "INSTINCT_CSV_CAPTURE " + csvName + " n=" + csvCaptures);
        } catch (IOException e) {
            Log.e(TAG, "CSV_WRITE", e);
        }
    }

    private synchronized void closeCsv() {
        if (csvWriter != null) {
            try { csvWriter.flush(); csvWriter.close(); }
            catch (IOException e) { Log.e(TAG, "CSV_CLOSE", e); }
            csvWriter = null;
        }
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String hex(byte[] bytes) {
        if (bytes == null) return "";
        final char[] digits = "0123456789ABCDEF".toCharArray();
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(digits[(b >> 4) & 15]).append(digits[b & 15]);
        }
        return out.toString();
    }

    private static String serviceDataHex(ScanRecord rec) {
        if (rec == null || rec.getServiceData() == null) return "";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<ParcelUuid, byte[]> e : rec.getServiceData().entrySet()) {
            if (out.length() > 0) out.append(';');
            out.append(e.getKey()).append(':').append(hex(e.getValue()));
        }
        return out.toString();
    }

    private static String manufacturerDataHex(ScanRecord rec) {
        if (rec == null) return "";
        SparseArray<byte[]> data = rec.getManufacturerSpecificData();
        if (data == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < data.size(); i++) {
            if (out.length() > 0) out.append(';');
            out.append(data.keyAt(i)).append(':').append(hex(data.valueAt(i)));
        }
        return out.toString();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == PERMISSION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startScan();
        else status.setText("Permiso Bluetooth necesario");
    }
    @Override protected void onDestroy() { stopScan(); super.onDestroy(); }
}
