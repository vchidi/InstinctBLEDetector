package com.lucyago.instinctpassive;

/**
 * Fail-closed placeholder. Libre 3 packet structures are not proof of Instinct Go
 * compatibility. Never fabricate glucose from BLE advertisement or encrypted bytes.
 */
public final class InstinctGoDecoder implements AuthenticatedGlucoseDecoder {
    @Override public String decoderId() { return "instinct-go-unverified"; }
    @Override public GlucoseReading decode(byte[] encryptedFrame, long receivedAtMs) {
        throw new SecurityException("Instinct Go authentication/decryption not implemented");
    }
}
