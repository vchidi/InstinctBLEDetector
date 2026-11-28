package com.lucyago.instinctpassive;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.app.AlertDialog;
import android.content.Intent;
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
    private Button inspect;
    private Button share;
    private BluetoothDevice candidate;
    private long lastConnectableMs;
    private long minGap=Long.MAX_VALUE,maxGap=0;
    private boolean waitingFresh;
    private final Runnable freshTimeout=()->{ append("Sin anuncio nuevo en 90 s."); cancelFresh(); };
    private int connectablePackets;
    private long connectionStartMs;
    private BluetoothGatt activeGatt;
    private final java.util.ArrayDeque<BluetoothGattCharacteristic> metadataQueue=new java.util.ArrayDeque<>();
    private BluetoothGattCharacteristic pendingRead;
    private boolean gattBusy;
    private boolean gattAttempted;
    private final Runnable gattTimeout = () -> { append("Tiempo límite GATT (20 s)."); closeGatt(); };
    private boolean running;
    private int count;
    private int matching;
    private int compatiblePackets;
    private int minRssi=127, maxRssi=-127;
    private long sumRssi;
    private int sampleCount;
    private final Set<Integer> manufacturerIds = new HashSet<>();
    private final Set<String> serviceDataShapes = new HashSet<>();
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
        title.setText("Instinct Go — laboratorio sensor retirado v5");
        title.setTextSize(21);
        layout.addView(title);
        TextView description = new TextView(this);
        description.setText("\n1. Escaneo pasivo 90 s. 2. Opcional: conexión GATT experimental de 20 s SOLO con sensor antiguo retirado y aislado del activo. Puede interrumpir MiniMed Go. NO autentica ni obtiene glucosa. No probar con el sensor nuevo.\n");
        layout.addView(description);
        start = new Button(this);
        start.setText("Escanear 90 segundos");
        layout.addView(start);
        inspect = new Button(this);
        inspect.setText("2. Explorar GATT (solo sensor retirado y aislado)");
        inspect.setEnabled(false);
        layout.addView(inspect);
        share = new Button(this);
        share.setText("Compartir informe");
        layout.addView(share);
        output = new TextView(this);
        output.setText("Esperando. No hace falta desconectar el iPhone.");
        layout.addView(output);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
        start.setOnClickListener(v -> begin());
        inspect.setOnClickListener(v -> confirmGatt());
        share.setOnClickListener(v -> shareReport());
    }
    private void append(String message) {
        output.append("\n" + message);
    }
    private void begin() {
        if (running || gattBusy || waitingFresh) return;
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
        candidate=null; lastConnectableMs=0; connectablePackets=0; minGap=Long.MAX_VALUE; maxGap=0; inspect.setEnabled(false);
        seen.clear(); count=0; matching=0; compatiblePackets=0;
        minRssi=127; maxRssi=-127; sumRssi=0; sampleCount=0;
        manufacturerIds.clear(); serviceDataShapes.clear();
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
        if (match) {
            compatiblePackets++;
            if(r.isConnectable()) { long now=r.getTimestampNanos()/1000000L; if(lastConnectableMs>0 && now>=lastConnectableMs){long gap=now-lastConnectableMs; minGap=Math.min(minGap,gap); maxGap=Math.max(maxGap,gap);} connectablePackets++; lastConnectableMs=now; }
            int rssi=r.getRssi();
            minRssi=Math.min(minRssi,rssi);
            maxRssi=Math.max(maxRssi,rssi);
            sumRssi+=rssi;
            sampleCount++;
            android.bluetooth.le.ScanRecord record=r.getScanRecord();
            android.util.SparseArray<byte[]> manufacturer=record.getManufacturerSpecificData();
            if (manufacturer!=null) {
                for(int i=0;i<manufacturer.size();i++) manufacturerIds.add(manufacturer.keyAt(i));
            }
            java.util.Map<android.os.ParcelUuid,byte[]> serviceData=record.getServiceData();
            if(serviceData!=null) {
                for(java.util.Map.Entry<android.os.ParcelUuid,byte[]> entry:serviceData.entrySet()) {
                    byte[] data=entry.getValue();
                    serviceDataShapes.add(entry.getKey().toString()+": "+(data==null?0:data.length)+" bytes");
                }
            }
            if (seen.add(key)) {
                matching++;
                if (matching==1) candidate=r.getDevice(); else candidate=null;
                final int num=matching;
                runOnUiThread(() -> append("Dispositivo compatible #" + num +
                    " | primer RSSI " + rssi + " dBm. Identidad sin confirmar."));
            }
        }
    }
    private void stopScan() {
        handler.removeCallbacks(stopLater);
        if (running) {
            running=false;
            try { if (scanner!=null) scanner.stopScan(callback); }
            catch (SecurityException ignored) {}
            start.setEnabled(true);
            append("Finalizado. Anuncios totales: " + count +
                    ". Dispositivos con UUID compatible: " + matching +
                    ". Anuncios compatibles: " + compatiblePackets +
                    ". Ninguna conexión realizada.");
            if (sampleCount>0) {
                append("RSSI compatibles: mínimo " + minRssi + " dBm, máximo " +
                    maxRssi + " dBm, media " + (sumRssi/sampleCount) + " dBm.");
                append("Identificadores públicos de fabricante BLE (no números de serie): " +
                    (manufacturerIds.isEmpty() ? "ninguno" : manufacturerIds.toString()));
                append("Longitudes de datos de servicio: " +
                    (serviceDataShapes.isEmpty() ? "ninguna" : serviceDataShapes.toString()));
                append("Escaneo: no se guardan MAC ni datos binarios de anuncios.");
                append("Anuncios conectables: "+connectablePackets+" de "+compatiblePackets);
                if(minGap!=Long.MAX_VALUE) append("Intervalos entre anuncios (ms): mínimo "+minGap+", máximo "+maxGap);
                if(matching==1 && candidate!=null && !gattAttempted && connectablePackets>0) {
                    inspect.setEnabled(true);
                    append("Un único candidato: puedes elegir la prueba GATT experimental.");
                } else append("GATT deshabilitado: candidato no único, no conectable o intento ya realizado.");
            }
        }
    }

    private void confirmGatt() {
        if (running || gattBusy || gattAttempted || matching!=1 || candidate==null) return;
        new AlertDialog.Builder(this)
            .setTitle("Conexión experimental: posible pérdida de MiniMed Go")
            .setMessage("SOLO con sensor antiguo retirado y aislado físicamente del sensor activo. La conexión puede interrumpir la monitorización. Esta prueba descubre UUID y lee metadatos estándar y estado; NO lee glucosa ni autentica. No la ejecutes con el sensor nuevo. ¿Continuar?")
            .setNegativeButton("Cancelar", (d,w)->{})
            .setPositiveButton("Esperar anuncio nuevo", (d,w)->connectGattTest())
            .show();
    }
    private void connectGattTest() {
        if (running || gattBusy || gattAttempted || candidate==null || matching!=1 || connectablePackets==0) return;
        if (Build.VERSION.SDK_INT>=31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},101);
            append("Concede el permiso y vuelve a pulsar explorar GATT.");
            return;
        }
        waitingFresh=true;
        inspect.setEnabled(false); start.setEnabled(false);
        append("Esperando anuncio nuevo del mismo candidato (90 s máximo).");
        try {
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),freshCallback);
            handler.postDelayed(freshTimeout,90000);
        } catch(SecurityException | IllegalStateException ex) { append("Error al esperar anuncio: "+ex.getClass().getSimpleName()); cancelFresh(); }
    }
    private final ScanCallback freshCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult r){ handler.post(()->freshResult(r)); }
        @Override public void onScanFailed(int code){handler.post(()->{if(waitingFresh){append("Error de escaneo nuevo: "+code);cancelFresh();}});}
    };
    private void cancelFresh(){
        handler.removeCallbacks(freshTimeout);
        if(waitingFresh){waitingFresh=false;try{scanner.stopScan(freshCallback);}catch(Exception ignored){}}
        start.setEnabled(!running && !gattBusy);
        inspect.setEnabled(false);
    }
    private void freshResult(ScanResult r){
        if(!waitingFresh || r.getScanRecord()==null || r.getScanRecord().getServiceUuids()==null) return;
        boolean match=false;
        for(android.os.ParcelUuid u:r.getScanRecord().getServiceUuids()) if(UUID.fromString(LIBRE3_SERVICE).equals(u.getUuid())) match=true;
        if(!match) return;
        if(!r.getDevice().equals(candidate)){append("Candidato distinto: prueba cancelada.");cancelFresh();return;}
        if(!r.isConnectable()) return;
        long age=android.os.SystemClock.elapsedRealtime()-r.getTimestampNanos()/1000000L;
        if(age<0 || age>2000) return;
        append("Anuncio nuevo: edad "+age+" ms, RSSI "+r.getRssi()+" dBm.");
        cancelFresh();
        candidate=r.getDevice();
        openGatt();
    }
    private void openGatt(){
        connectionStartMs=android.os.SystemClock.elapsedRealtime();
        gattAttempted=true;
        gattBusy=true;
        inspect.setEnabled(false);
        start.setEnabled(false);
        append("Intentando conexión GATT (20 s máximo). Sin emparejar, escribir ni suscribirse. Lectura limitada a metadatos estándar y estado.");
        handler.postDelayed(gattTimeout,20000);
        try {
            activeGatt=candidate.connectGatt(this,false,gattCallback,BluetoothDevice.TRANSPORT_LE);
            if(activeGatt==null) { append("No se pudo iniciar GATT."); closeGatt(); }
        } catch (SecurityException | IllegalStateException ex) {
            append("Error de conexión: "+ex.getClass().getSimpleName());
            closeGatt();
        }
    }
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state) {
            handler.post(()->{
                if(!gattBusy || g!=activeGatt) { try { g.close(); } catch(Exception ignored){} return; }
                append("Estado GATT: "+state+" código "+status+" tras "+(android.os.SystemClock.elapsedRealtime()-connectionStartMs)+" ms.");
                if(status!=BluetoothGatt.GATT_SUCCESS || state==BluetoothProfile.STATE_DISCONNECTED) {
                    append("GATT desconectado. Código "+status); closeGatt();
                } else if(state==BluetoothProfile.STATE_CONNECTED) {
                    append("GATT conectado. Solicitando lista de servicios.");
                    try { if(!g.discoverServices()) { append("No se pudieron descubrir servicios."); closeGatt(); } }
                    catch(SecurityException ex) { append("Permiso GATT denegado."); closeGatt(); }
                }
            });
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic ch,byte[] value,int status){metadataResult(g,ch,value==null?null:value.clone(),status);}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic ch,int status){byte[] v=ch.getValue();metadataResult(g,ch,v==null?null:v.clone(),status);}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status) {
            runOnUiThread(()->{
                if(!gattBusy || g!=activeGatt) return;
                if(status==BluetoothGatt.GATT_SUCCESS) {
                    int total=0;
                    java.util.Set<UUID> services=new java.util.HashSet<>();
                    java.util.Set<UUID> characteristics=new java.util.HashSet<>();
                    for(BluetoothGattService service:g.getServices()) {
                        services.add(service.getUuid());
                        append("SERVICIO "+service.getUuid());
                        for(BluetoothGattCharacteristic ch:service.getCharacteristics()) {
                            characteristics.add(ch.getUuid());
                            append("  CARACTERÍSTICA "+ch.getUuid()+" ["+Libre3GattFingerprint.describe(ch.getUuid())+"] propiedades=0x"+
                                Integer.toHexString(ch.getProperties()));
                            for(android.bluetooth.BluetoothGattDescriptor d:ch.getDescriptors()) append("    DESCRIPTOR "+d.getUuid()+" permisos=0x"+Integer.toHexString(d.getPermissions()));
                            append("    Permisos declarados=0x"+Integer.toHexString(ch.getPermissions()));
                            total++;
                        }
                    }
                    append("Descubrimiento terminado. Características: "+total);
                    append(Libre3GattFingerprint.inspect(services,characteristics).summary());
                    readMetadata(g); return;
                } else append("Error descubriendo servicios: "+status);
                closeGatt();
            });
        }
    };
    private void readMetadata(BluetoothGatt g){
        metadataQueue.clear();
        for(BluetoothGattService service:g.getServices()){
            String serviceId=service.getUuid().toString();
            if(!serviceId.startsWith("00001800-") && !serviceId.startsWith("0000180a-") && !serviceId.equals(LIBRE3_SERVICE)) continue;
            for(BluetoothGattCharacteristic ch:service.getCharacteristics()){
                String id=ch.getUuid().toString();
                if((id.equals("08981482-ef89-11e9-81b4-2a2ae2dbcce4") || id.startsWith("00002a00-") || id.startsWith("00002a28-") || id.startsWith("00002a29-") || id.startsWith("00002a24-") || id.startsWith("00002a26-")) && (ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ)!=0) metadataQueue.add(ch);
            }
        }
        nextMetadata(g);
    }
    private void nextMetadata(BluetoothGatt g){
        if(!gattBusy || g!=activeGatt) return;
        BluetoothGattCharacteristic ch=metadataQueue.poll();
        if(ch==null){append("Lecturas terminadas. Sin autenticación ni escrituras. No interpretar como glucosa.");closeGatt();return;}
        pendingRead=ch;
        append("Solicitando lectura "+ch.getUuid());
        try{if(!g.readCharacteristic(ch)){append("Lectura no iniciada: "+ch.getUuid());nextMetadata(g);}}
        catch(SecurityException ex){append("Permiso de lectura denegado.");closeGatt();}
    }
    private void metadataResult(BluetoothGatt g,BluetoothGattCharacteristic ch,byte[] value,int status){
        handler.post(()->{
            if(!gattBusy || g!=activeGatt) return;
            if(pendingRead!=ch) return;
            pendingRead=null;
            String id=ch.getUuid().toString();
            if(status==0 && value!=null){
                String v=new String(value,java.nio.charset.StandardCharsets.UTF_8).replaceAll("[^\\p{Print}]","?");
                // Device name may contain a serial number: do not export its value.
                append("METADATO "+id+": "+(id.startsWith("00002a00-")?"nombre presente ("+value.length+" bytes)":v.substring(0,Math.min(v.length(),120))));
                if(id.startsWith("00002a28-") || id.equals("08981482-ef89-11e9-81b4-2a2ae2dbcce4")){
                    StringBuilder hex=new StringBuilder();
                    for(int i=0;i<Math.min(value.length,128);i++) hex.append(String.format(java.util.Locale.ROOT,"%02X ",value[i]&255));
                    append("BINARIO "+id+" longitud="+value.length+" hex="+hex+(value.length>128?" (truncado)":""));
                }
            }else append("METADATO "+id+" error "+status);
            nextMetadata(g);
        });
    }
    private void closeGatt() {
        metadataQueue.clear(); pendingRead=null;
        handler.removeCallbacks(gattTimeout);
        gattBusy=false;
        BluetoothGatt old=activeGatt;
        activeGatt=null;
        if(old!=null) {
            try { old.disconnect(); } catch(Exception ignored){}
            try { old.close(); } catch(Exception ignored){}
        }
        if(start!=null) start.setEnabled(!running);
        if(inspect!=null) inspect.setEnabled(false); // one-shot, no repeat connections
    }
    private void shareReport() {
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,"Instinct Go BLE/GATT (sin identificadores)");
        send.putExtra(Intent.EXTRA_TEXT,output.getText().toString());
        startActivity(Intent.createChooser(send,"Compartir informe"));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQ_SCAN) {
            if (grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) begin();
            else output.setText("Permiso de escaneo denegado.");
        }
    }
    @Override protected void onPause() { super.onPause(); cancelFresh(); stopScan(); closeGatt(); }
    @Override public void onBackPressed() { cancelFresh(); closeGatt(); super.onBackPressed(); }
    @Override protected void onDestroy() { cancelFresh(); stopScan(); closeGatt(); super.onDestroy(); }
}
