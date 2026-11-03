package com.lucyago.instinctbledetector;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Passive BLE advertisement scanner. NEVER calls connectGatt(), pairing or writes. */
public class MainActivity extends Activity {
    private static final String TAG = "InstinctBLE";
    private static final String TARGET_UUID = "089810cc-ef89-11e9-81b4-2a2ae2dbcce4";
    private static final String TARGET_PREFIX = "E46447A3";
    private static final String TARGET_MAC = "E4:64:47:A3:0C:A0";
    private static final int PERMISSION_REQUEST = 9;
    private static final long SCAN_DURATION_MS = 90000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, String> devices = new LinkedHashMap<>();
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private TextView status, results;
    private Button button;
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
        button = new Button(this);
        button.setText("ESCANEAR 90 s");
        button.setOnClickListener(v -> { if (scanning) stopScan(); else startScan(); });
        root.addView(button);
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
        devices.clear();
        results.setText("Buscando anuncios BLE...\n");
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        try {
            scanner.startScan(null, settings, callback);
            scanning = true;
            button.setText("DETENER");
            status.setText("Escaneando (90 s máximo)");
            Log.i(TAG, "SCAN_STARTED passive=true duration=90s");
            handler.postDelayed(timeout, SCAN_DURATION_MS);
        } catch (SecurityException | IllegalStateException ex) {
            status.setText("No se pudo escanear: " + ex.getClass().getSimpleName());
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
                || uuids.toLowerCase(Locale.ROOT).contains(TARGET_UUID)
                || serviceData.toLowerCase(Locale.ROOT).contains(TARGET_UUID);
        String line = (candidate ? "*** POSIBLE INSTINCT *** " : "")
                + "name=" + name + " mac=" + mac + " rssi=" + r.getRssi()
                + " uuids=" + uuids + " serviceData=" + serviceData
                + " manufacturers=" + manufacturerIds;
        // One log entry per device/changed data, rather than every advertisement.
        String old = devices.put(mac, line);
        if (!line.equals(old)) Log.i(TAG, line);
        if (devices.size() > 150) { String first = devices.keySet().iterator().next(); devices.remove(first); }
        runOnUiThread(this::updateDisplay);
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
        if (devices.isEmpty()) return;
        int matches = 0;
        StringBuilder all = new StringBuilder();
        for (String line : devices.values()) {
            if (line.startsWith("***")) { matches++; all.insert(0, line + "\n\n"); }
        }
        int shown = 0;
        for (String line : devices.values()) {
            if (!line.startsWith("***") && shown++ < 20) all.append(line).append("\n\n");
        }
        status.setText((scanning ? "Escaneando" : "Detenido") + " | " + devices.size() + " dispositivos | " + matches + " posibles");
        results.setText(all.toString());
    }

    private void stopScan() {
        handler.removeCallbacks(timeout);
        if (scanning && scanner != null) {
            try { scanner.stopScan(callback); } catch (SecurityException ignored) { }
        }
        scanning = false;
        button.setText("ESCANEAR 90 s");
        status.setText("Detenido | " + devices.size() + " dispositivos");
        Log.i(TAG, "SCAN_STOPPED count=" + devices.size());
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == PERMISSION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startScan();
        else status.setText("Permiso Bluetooth necesario");
    }
    @Override protected void onDestroy() { stopScan(); super.onDestroy(); }
}
