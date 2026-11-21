# Offline glucose pipeline (not clinical)

The framing helper is an isolated, pure-Java building block. It does not connect to Bluetooth, pair, decrypt, parse, or show glucose.

## Juggluco reference behavior
In Juggluco `Libre3GattCallback.glucose_data(byte[],long)` appends incoming fragments into `oneMinuteRawData`. On completion, it calls `intDecrypt(cryptptr,3,oneMinuteRawData)`. Its Libre 3 decrypted payload is checked for 29 bytes (Lingo 51 bytes) before `saveLibre3MinuteL`. These are **Juggluco Libre 3 facts**, not verified Instinct Go facts.

## Implementation
- `GlucoseFrameAssembler` assembles a configured number of encrypted bytes; it rejects overrun and clears its buffer.
- `GlucosePipeline` is an unimplemented interface, so no glucose values are guessed or displayed.
- `OfflineFrameSelfTest` verifies fragmentation and rejection of invalid input.
- The separate workflow compiles and runs these pure Java tests without Android or hardware.

## Next blocking questions
1. Can a retired/test Instinct Go be connected without taking control of an active MiniMed Go sensor?
2. Does it expose compatible glucose and security characteristics?
3. Can we lawfully and safely obtain appropriate authorization and verify the protocol?
4. Are decrypted payloads identical to Libre 3? Requires real comparison; no assumptions.
5. Only then implement Watch receiver and cross-check with official MiniMed Go.

No experimental output should guide insulin dosing.
