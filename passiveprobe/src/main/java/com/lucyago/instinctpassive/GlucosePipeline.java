package com.lucyago.instinctpassive;

/**
 * Design-only boundary between authenticated BLE transport and glucose decoding.
 * No implementation is connected to the clinical sensor.
 */
public interface GlucosePipeline {
    /** Never use this method before authentication has been independently verified. */
    void onEncryptedGlucoseFrame(byte[] frame);

    /** A successful decrypt must still be validated against official readings. */
    void onTransportDisconnected();
}
