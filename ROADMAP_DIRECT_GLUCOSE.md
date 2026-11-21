# Ruta completa hacia glucosa directa: Instinct Go → Wear OS

Estado: preparación técnica, **sin validación de lectura de glucosa**.

## 1. Descubrimiento (completado parcialmente)
- Detector pasivo Android: UUID de servicio compatible con Libre 3; no identifica de manera inequívoca al sensor.
- Señal cercana y lejana observada: -59 dBm / -82 dBm. La distancia es evidencia circunstancial, no identificación criptográfica.
- El detector NO usa `connectGatt`. Los servicios GATT internos no pueden enumerarse desde anuncios BLE.

## 2. Inspección GATT (NO ejecutar con sensor activo)
- Requiere `connectGatt`, `discoverServices` y leer propiedades (READ/WRITE/NOTIFY) de las características.
- Una conexión puede competir con el receptor oficial y causar interrupciones; reservar para sensor de pruebas.
- Registrar UUID y propiedades, sin leer/escribir datos ni habilitar notificaciones.
- No incluir emparejamiento automático ni intentos repetidos de reconexión.

## 3. Autenticación (bloqueada hasta disponer de sensor de prueba)
- Juggluco: `Common/src/libre3/java/tk/glucodata/Libre3GattCallback.java`.
- La respuesta al desafío incluye `Natives.getpin(sensorptr)` (4 bytes), desafío y respuesta cifrada.
- Hay claves/autorizaciones adicionales: `getLibre3kAuth` y `libre3SelectAppKeyAndSavedAuthorization`.
- No se ha demostrado compatibilidad criptográfica con Instinct Go ni posibilidad de extraer autorización del MiniMed Go.
- No intentar comandos NFC `A0` (activación) ni `A8` (cambio de receptor) con el sensor activo.

## 4. Recepción y descifrado (código de referencia localizado)
- En `Libre3GattCallback.java`, `onCharacteristicChanged` envía la característica `LIBRE3_CHAR_GLUCOSE_DATA` a `glucose_data`.
- El descifrado usa rutinas nativas `Natives.intDecrypt` y guarda muestras de Libre 3.
- Debe verificarse el formato real de Instinct Go y contrastarse contra el receptor oficial antes de mostrarlo como medición.

## 5. Wear OS
- Primero validar Android con sensor de prueba y lecturas comparables.
- Después portar conexión/autenticación a Galaxy Watch, sin iPhone; estudiar soporte Wear OS de Juggluco.
- La conectividad LTE del reloj para cuidadores es un proyecto posterior a la lectura fiable.
- Nunca utilizar valores experimentales para calcular dosis de insulina.

## Condiciones para avanzar
1. Mantener MiniMed Go operativo sin interrupciones.
2. Disponer de sensor retirado que siga respondiendo o de sensor de pruebas.
3. Obtener autorización técnica válida sin tomar control del sensor de uso clínico.
4. Validar repetidamente datos y estabilidad frente a fuente oficial.

## Evidencia que necesitamos
- Escaneo pasivo con metadatos sin identificadores privados.
- Si es posible, referencia de modelo exacto, firmware y comportamiento de NFC del sensor retirado.
- Tabla de servicios GATT obtenida únicamente de un sensor de pruebas.
- Traza de negociación y lecturas únicamente con sensor de pruebas y sin datos personales publicados.

## Referencias
- https://github.com/j-kaltes/Juggluco/blob/master/Common/src/main/java/tk/glucodata/Libre3.java
- https://github.com/j-kaltes/Juggluco/blob/master/Common/src/libre3/java/tk/glucodata/Libre3GattCallback.java
- https://github.com/j-kaltes/Juggluco/blob/master/Common/src/libre3/java/tk/glucodata/libre3/NFC.java
