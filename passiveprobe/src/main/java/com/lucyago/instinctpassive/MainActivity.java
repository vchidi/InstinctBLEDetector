package com.lucyago.instinctpassive;

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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String LIBRE3_SERVICE = "089810cc-ef89-11e9-81b4-2a2ae2dbcce4";
    private static final int REQ_SCAN = 100;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> seen = new HashSet<>();
    private BluetoothLeScanner scanner;
    private TextView output;
    private Button start;
    private boolean running;
    private int count;
    private int matching;
    private final Runnable stopLater = this::stopScan;
    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) { process(result); }
        @Override public void onBatchScanResults(java.util.List<ScanResult> results) {
            for (ScanResult r : results) process(r);
        }
        @Override public void onScanFailed(int errorCode) {
            runOnUiThread(() -> { stopScan(); append("Error de escaneo BLE: " + errorCode); });
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 32, 28, 24);
        TextView title = new TextView(this);
        title.setText("Instinct Go — exploración BLE pasiva");
        title.setTextSize(21);
        layout.addView(title);
        TextView description = new TextView(this);
        description.setText("\nSolo observa anuncios Bluetooth durante 90 segundos. NO se conecta al sensor, NO solicita servicios GATT, NO escribe datos y NO obtiene glucosa. Si MiniMed Go ya está conectado, puede no haber anuncios visibles.\n");
        layout.addView(description);
        start = new Button(this);
        start.setText("Escanear 90 segundos");
        layout.addView(start);
        output = new TextView(this);
        output.setText("Esperando. No hace falta desconectar el iPhone.");
        layout.addView(output);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
        start.setOnClickListener(v -> begin());
    }
    private void append(String message) {
        output.append("\n" + message);
    }
    private void begin() {
        if (running) return;
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            output.setText("Activa Bluetooth en el Samsung."); return;
        }
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQ_SCAN);
            return;
        }
        if (Build.VERSION.SDK_INT < 31 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_SCAN);
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { output.setText("No se puede iniciar el escáner BLE."); return; }
        seen.clear(); count=0; matching=0;
        output.setText("Escaneando anuncios BLE (90 s). Sin conexiones.");
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        try {
            scanner.startScan(null, settings, callback);
            running=true; start.setEnabled(false);
            handler.postDelayed(stopLater, 90000);
        } catch (SecurityException | IllegalStateException e) {
            output.setText("No se pudo escanear: " + e.getClass().getSimpleName());
        }
    }
    private void process(ScanResult r) {
        if (!running || r.getScanRecord()==null) return;
        count++;
        boolean match = false;
        if (r.getScanRecord().getServiceUuids()!=null) {
            for (android.os.ParcelUuid u : r.getScanRecord().getServiceUuids())
                if (UUID.fromString(LIBRE3_SERVICE).equals(u.getUuid())) match = true;
        }
        // MAC is not displayed or exported. Android may randomize addresses.
        String key = r.getDevice().getAddress();
        if (match && seen.add(key)) {
            matching++;
            final int rssi=r.getRssi();
            final int num=matching;
            runOnUiThread(() -> append("Posible sensor #" + num + " | RSSI " + rssi +
                    " dBm | servicio Libre 3 anunciado. No confirma Instinct Go."));
        }
    }
    private void stopScan() {
        handler.removeCallbacks(stopLater);
        if (running) {
            running=false;
            try { if (scanner!=null) scanner.stopScan(callback); }
            catch (SecurityException ignored) {}
            start.setEnabled(true);
            append("Finalizado. Anuncios observados: " + count +
                    ". Dispositivos con UUID compatible: " + matching +
                    ". Ninguna conexión realizada.");
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQ_SCAN) {
            if (grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) begin();
            else output.setText("Permiso de escaneo denegado.");
        }
    }
    @Override protected void onPause() { super.onPause(); stopScan(); }
    @Override protected void onDestroy() { stopScan(); super.onDestroy(); }
}
