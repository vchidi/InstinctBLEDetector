package com.lucyago.instinctpassive;

/**
 * Implement only after verifying Instinct Go security and message format.
 * Frame bytes remain encrypted until the decoder authenticates and decrypts.
 */
public interface AuthenticatedGlucoseDecoder {
    /** Return a candidate only on successful authenticated decryption and parsing. */
    GlucoseReading decode(byte[] encryptedFrame, long receivedAtMs)
        throws SecurityException, IllegalArgumentException;
    String decoderId();
}
