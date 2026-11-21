# Authentication research — no sensor interaction

Source examined: `j-kaltes/Juggluco/Common/src/libre3/java/tk/glucodata/Libre3GattCallback.java`.

## Confirmed in reference code
- `Natives.getLibre3kAuth(sensorptr)` obtains saved authorization material.
- `Natives.getpin(sensorptr)` returns a 4-byte PIN used in challenge response construction.
- `Natives.libre3BeginSecurityHandshake` starts a cryptographic context.
- `Natives.libre3SelectAppKeyAndSavedAuthorization` restores the app key and saved authorization.
- `mknonceback` assembles challenge values and PIN, then encrypts the response.
- `glucose_data` assembles incoming encrypted fragments, calls `intDecrypt(cryptptr,3,...)`, checks decrypted length (29 bytes for reference Libre 3, 51 for Lingo) and forwards to the native glucose parser.

## Unknowns
- Whether Instinct Go uses the same authorization derivation and handshake.
- Whether MiniMed Go retains credentials in a reusable format (do not extract secrets from the active clinical phone).
- Whether a retired sensor remains usable for BLE experiments.
- Whether another receiver can coexist without interrupting MiniMed Go.

## Safety/design decisions
- Do not claim that BLE advertisement UUID identifies Instinct Go.
- Do not set authenticated=true merely because GATT connected or services discovered.
- Never hardcode sensor PINs, keys, session secrets or personal identifiers in GitHub.
- Keep GATT test connection disabled by default.
- Prefer a retired sensor or manufacturer-provided development hardware for live tests.
- `AuthenticationEvidence` is a restrictive internal contract, **not a cryptographic verifier**.
- Until protocol compatibility and credentials are established, no real glucose can be read or trusted.

## Next implementation
Build a *real* protocol adapter only after valid sensor-test credentials and verified specification are available. Wire successful cryptographic verification to a session gate; separately implement and test decryption and sensor-value validation. Never use experimental data for insulin dosing.
