package com.lucyago.instinctpassive;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/**
 * PREPARATORY CODE ONLY. NOT wired to MainActivity.
 *
 * No pairing, NFC, authentication, characteristic reads/writes or notifications.
 * A real GATT connection can disrupt the clinically active MiniMed Go receiver.
 * The compiled feature gate is intentionally false; do not flip it for an active sensor.
 */
public final class RetiredSensorGattLab {
    private static final boolean ENABLE_TEST_SENSOR_CONNECTION = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private final List<String> events = new ArrayList<>();
    private final Runnable timeout = this::close;

    public boolean isEnabled() { return ENABLE_TEST_SENSOR_CONNECTION; }

    /** Only for a retired/test sensor after explicit validation and gate review. */
    public synchronized boolean inspect(Context context, BluetoothDevice device,
                                         boolean retiredSensorConfirmed) {
        if (!ENABLE_TEST_SENSOR_CONNECTION || !retiredSensorConfirmed || device == null)
            return false;
        if (gatt != null) return false;
        events.clear();
        try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) return false;
            handler.postDelayed(timeout, 15000L);
            return true;
        } catch (SecurityException ex) {
            events.add("Bluetooth permission missing");
            close();
            return false;
        }
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt current, int status, int state) {
            if (current != gatt) return;
            if (status != BluetoothGatt.GATT_SUCCESS || state == BluetoothProfile.STATE_DISCONNECTED) {
                events.add("Disconnected; status=" + status);
                close();
            } else if (state == BluetoothProfile.STATE_CONNECTED) {
                events.add("Connected; requesting service discovery");
                try { if (!current.discoverServices()) close(); }
                catch (SecurityException ex) { close(); }
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt current, int status) {
            if (current != gatt) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService service : current.getServices()) {
                    events.add("Service " + service.getUuid());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        events.add("  Characteristic " + characteristic.getUuid()
                                + " properties=0x" + Integer.toHexString(characteristic.getProperties()));
                    }
                }
            } else events.add("Service discovery failed: " + status);
            close(); // One-shot inspection; never keep connection open.
        }
    };

    public synchronized List<String> snapshot() { return new ArrayList<>(events); }

    public synchronized void close() {
        handler.removeCallbacks(timeout);
        BluetoothGatt old = gatt;
        gatt = null;
        if (old != null) {
            try { old.disconnect(); } catch (SecurityException ignored) {}
            try { old.close(); } catch (Exception ignored) {}
        }
    }
}
