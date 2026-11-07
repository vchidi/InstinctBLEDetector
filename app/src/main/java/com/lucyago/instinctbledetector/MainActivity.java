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
import android.util.SparseArray;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Passive BLE advertisement recorder. No pairing, GATT connection, or writes to sensor.
 * Diagnostic only: NOT a glucose monitor. */
public class MainActivity extends Activity {
    private static final String TAG = "InstinctBLE";
    private static final String UUID_TARGET = "089810cc-ef89-11e9-81b4-2a2ae2dbcce4";
    private static final String MAC_TARGET = "E4:64:47:A3:0C:A0";
    private static final String NAME_PREFIX = "E46447A3";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final HashSet<String> seen = new HashSet<>();
    private final HashSet<String> savedSignatures = new HashSet<>();
    private static final int MAX_SAVED_ADVERTISEMENTS = 1500;
    private BluetoothLeScanner scanner;
    private BufferedWriter writer;
    private File logFile;
    private boolean scanning;
    private boolean pendingUi;
    private int count;
    private int totalAdvertisements;
    private int savedAdvertisements;
    private String lastOther = "Sin anuncios de otros dispositivos.";
    private String last = "Todavia no se ha recibido ningun anuncio del sensor.";
    private TextView status, results;
    private Button button;
    private final Runnable timeout = this::stop;

    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult r) { capture(r); }
        @Override public void onBatchScanResults(List<ScanResult> list) {
            for (ScanResult r : list) capture(r);
        }
        @Override public void onScanFailed(int error) {
            Log.e(TAG, "SCAN_FAILED " + error);
            runOnUiThread(() -> {
                stop();
                status.setText("Error de escaneo: " + error);
            });
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 28, 30, 24);
        root.setBackgroundColor(0xff101820);
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(15);
        status.setTextColor(0xffffffff);
        status.setText("Instinct BLE 2.1 | Diagnostico pasivo");
        root.addView(status);
        button = new Button(this);
        button.setText("ESCANEAR 90 S");
        button.setOnClickListener(v -> { if (scanning) stop(); else start(); });
        root.addView(button);
        ScrollView scroll = new ScrollView(this);
        results = new TextView(this);
        results.setTextSize(12);
        results.setTextColor(0xffc7e8ff);
        results.setText("Guarda hasta 1500 anuncios BLE de dispositivos cercanos en un CSV local.\nNo conecta con el sensor. No mide glucosa.");
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void start() {
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, 9);
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            status.setText("Activa Bluetooth");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { status.setText("Escaner no disponible"); return; }
        File dir = getExternalFilesDir(null);
        if (dir == null) { status.setText("No hay almacenamiento disponible"); return; }
        String date = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        logFile = new File(dir, "instinct_ble_" + date + ".csv");
        try {
            synchronized (lock) {
                writer = new BufferedWriter(new FileWriter(logFile));
                writer.write("timestamp_local,possible_instinct,name,mac,rssi,uuids,raw_scan_record_hex,service_data_hex,manufacturer_data_hex\n");
                writer.flush();
            }
        } catch (IOException ex) {
            Log.e(TAG, "FILE_OPEN", ex);
            status.setText("No se pudo crear el CSV");
            return;
        }
        count = 0;
        totalAdvertisements = 0;
        savedAdvertisements = 0;
        seen.clear();
        savedSignatures.clear();
        lastOther = "Sin anuncios de otros dispositivos.";
        last = "Esperando anuncios del Instinct...";
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build();
        try {
            scanning = true;
            scanner.startScan(null, settings, callback);
            button.setText("DETENER");
            status.setText("Escaneando...");
            refresh();
            handler.postDelayed(timeout, 90000);
            Log.i(TAG, "SCAN_STARTED passive=true");
        } catch (SecurityException | IllegalStateException ex) {
            scanning = false;
            closeLog();
            status.setText("No se pudo iniciar escaneo");
            Log.e(TAG, "SCAN_START", ex);
        }
    }

    private void capture(ScanResult r) {
        if (!scanning || r == null || r.getDevice() == null) return;
        ScanRecord record = r.getScanRecord();
        String mac = r.getDevice().getAddress();
        String name = record == null ? null : record.getDeviceName();
        if (name == null) name = "(sin nombre)";
        String uuids = record != null && record.getServiceUuids() != null
                ? record.getServiceUuids().toString() : "[]";
        String serviceUuids = record != null && record.getServiceData() != null
                ? record.getServiceData().keySet().toString() : "[]";
        boolean target = MAC_TARGET.equalsIgnoreCase(mac)
                || name.toUpperCase(Locale.ROOT).contains(NAME_PREFIX)
                || uuids.toLowerCase(Locale.ROOT).contains(UUID_TARGET)
                || serviceUuids.toLowerCase(Locale.ROOT).contains(UUID_TARGET);
        String raw = record == null ? "" : hex(record.getBytes());
        synchronized (lock) {
            if (!scanning || writer == null) return;
            totalAdvertisements++;
            seen.add(mac);
            if (target) {
                count++;
                last = "Nombre: " + name + "\nMAC: " + mac + "\nRSSI: " + r.getRssi()
                        + "\nUUID: " + uuids + "\nService UUID: " + serviceUuids
                        + "\nHEX: " + raw;
                Log.i(TAG, "TARGET_ADV n=" + count + " mac=" + mac
                        + " rssi=" + r.getRssi() + " raw=" + raw);
            } else {
                lastOther = "Nombre: " + name + "\nMAC: " + mac
                        + "\nUUID: " + uuids + "\nService UUID: " + serviceUuids;
            }
            // Keep one copy of each distinct non-target packet, plus all
            // target packets. Bounded file size and passive scan only.
            String signature = mac + "|" + name + "|" + raw;
            boolean shouldSave = target || savedSignatures.add(signature);
            if (shouldSave && savedAdvertisements < MAX_SAVED_ADVERTISEMENTS) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                        Locale.US).format(new Date());
                try {
                    writer.write(csv(timestamp) + "," + (target ? "1" : "0") + ","
                            + csv(name) + "," + csv(mac) + "," + r.getRssi() + ","
                            + csv(uuids) + "," + csv(raw) + ","
                            + csv(serviceData(record)) + ","
                            + csv(manufacturerData(record)) + "\n");
                    writer.flush();
                    savedAdvertisements++;
                } catch (IOException ex) { Log.e(TAG, "FILE_WRITE", ex); }
            }
        }
        scheduleUi();
    }

    private void scheduleUi() {
        if (pendingUi) return;
        pendingUi = true;
        handler.postDelayed(() -> { pendingUi = false; refresh(); }, 600);
    }

    private void refresh() {
        results.setText("Dispositivos: " + seen.size()
                + "\nAnuncios recibidos: " + totalAdvertisements
                + "\nAnuncios guardados: " + savedAdvertisements
                + "\nPosibles Instinct: " + count
                + "\n\nUltimo Instinct:\n" + last
                + "\n\nUltimo otro BLE:\n" + lastOther
                + "\n\nArchivo: " + (logFile == null ? "-" : logFile.getName()));
    }

    private void stop() {
        handler.removeCallbacks(timeout);
        scanning = false;
        if (scanner != null) {
            try { scanner.stopScan(callback); }
            catch (SecurityException | IllegalStateException ignored) { }
        }
        closeLog();
        button.setText("ESCANEAR 90 S");
        refresh();
        status.setText("Detenido | " + count + " posibles Instinct");
        Log.i(TAG, "SCAN_STOPPED target_packets=" + count);
    }

    private void closeLog() {
        synchronized (lock) {
            if (writer != null) {
                try { writer.flush(); writer.close(); }
                catch (IOException ex) { Log.e(TAG, "FILE_CLOSE", ex); }
                writer = null;
            }
        }
    }

    private static String hex(byte[] bytes) {
        if (bytes == null) return "";
        final char[] digits = "0123456789ABCDEF".toCharArray();
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            s.append(digits[(b >> 4) & 15]).append(digits[b & 15]);
        }
        return s.toString();
    }

    private static String serviceData(ScanRecord r) {
        if (r == null || r.getServiceData() == null) return "";
        StringBuilder s = new StringBuilder();
        for (Map.Entry<ParcelUuid, byte[]> entry : r.getServiceData().entrySet()) {
            if (s.length() > 0) s.append(';');
            s.append(entry.getKey()).append(':').append(hex(entry.getValue()));
        }
        return s.toString();
    }

    private static String manufacturerData(ScanRecord r) {
        if (r == null) return "";
        SparseArray<byte[]> a = r.getManufacturerSpecificData();
        if (a == null) return "";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < a.size(); i++) {
            if (s.length() > 0) s.append(';');
            s.append(a.keyAt(i)).append(':').append(hex(a.valueAt(i)));
        }
        return s.toString();
    }

    private static String csv(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 9 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            start();
        } else status.setText("Permiso Bluetooth necesario");
    }

    @Override protected void onDestroy() {
        stop();
        super.onDestroy();
    }
}
