# Instinct Go — safe passive BLE stage

This experimental Android phone application scans advertising packets only. It does **not** call connectGatt, request GATT services, write characteristics, pair, use NFC, disconnect the iPhone, or claim to read glucose.

Known Libre 3 service advertisement UUID: `089810cc-ef89-11e9-81b4-2a2ae2dbcce4`. Its appearance is not proof of sensor identity, protocol compatibility, or glucose access. An already connected sensor may not advertise.

## Observations from Juggluco source
- `Common/src/main/java/tk/glucodata/Libre3.java` sends NFC `02 A1 7A` for patch information.
- `Common/src/libre3/java/tk/glucodata/Libre3GattCallback.java` uses `Natives.getpin(sensorptr)` (4 bytes) in its challenge reply and retains authorization information (`getLibre3kAuth`). A service UUID or a PIN alone is insufficient to decode glucose.
- Juggluco's full NFC path has a second command, which can activate or change receiver; **do not use it on the active sensor**.

## Safe sequence
1. Run this passive scanner while MiniMed Go remains connected. No scan result is a valid possible outcome.
2. Do not disable or re-pair MiniMed Go for the experiment.
3. When a replacement sensor is running normally, investigate NFC with the retired sensor if it still responds.
4. Before any active GATT authentication, validate the exact sensor variant and expected credentials on a retired/test sensor.
5. Experimental data must never be used for insulin dosing.

## Limitations
Android 12+ may filter some BLE advertisements when `neverForLocation` is declared. The app displays no Bluetooth addresses or identifying payloads. It only reports compatible service UUID and RSSI.
