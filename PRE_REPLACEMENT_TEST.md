# Protocolo de prueba justo antes de retirar el Instinct Go

**Compilación experimental, sin validación en dispositivo.** Rama separada de la app pasiva estable.

## Qué hace esta versión
1. Escanea anuncios BLE durante 90 s y busca el UUID de servicio compatible con Libre 3.
2. Solo si hay **un único candidato**, habilita un botón independiente para una conexión GATT de prueba.
3. El botón requiere confirmación explícita del riesgo de interrumpir MiniMed Go.
4. Hace `connectGatt` sin autoconectar, descubre UUID y propiedades de características, y cierra automáticamente (máximo 20 s).
5. Comparte el informe de texto mediante el menú de Android; no muestra direcciones MAC ni guarda claves.

## Qué NO hace
- No verifica que el candidato sea inequívocamente Instinct Go.
- No lee características, no escribe, no habilita notificaciones ni realiza pairing.
- No autentica, no descifra ni obtiene glucosa.
- No puede garantizar que MiniMed Go conserve la conexión; tampoco que la recupere.

## Condiciones para ejecución
- Únicamente **justo antes de retirar el sensor activo**, cuando la familia ya está lista para el cambio.
- Tener a mano una medición alternativa de glucosa y el material habitual para el cambio.
- No repetir la conexión si falla; conservar el informe y cambiar el sensor.
- Mantener el teléfono Samsung alejado del sensor nuevo después de cerrar la prueba.
- No utilizar el sensor de repuesto sin activar para este experimento.
- Si el niño presenta síntomas o requiere atención, cancelar el experimento.

## Pasos en el teléfono
1. Instalar APK de la rama `experiment/pre-replacement-gatt` (no confundir con la aplicación pasiva anterior).
2. Pulsar «Escanear 90 segundos».
3. Si hay exactamente un candidato, pulsar «Explorar GATT» y aceptar la advertencia.
4. Esperar cierre automático, pulsar «Compartir informe».
5. Continuar con el cambio habitual de sensor.

## Siguiente barrera: lectura de glucosa
Los UUID GATT permiten contrastar si el sensor se parece a Libre 3, pero no bastan para leer glucosa. Hace falta autenticación y descifrado válidos para Instinct Go. No afirmar que la lectura de glucosa está implementada.

## Código de referencia
- `j-kaltes/Juggluco/Common/src/libre3/java/tk/glucodata/Libre3GattCallback.java`
- `AUTHENTICATION_RESEARCH.md`
