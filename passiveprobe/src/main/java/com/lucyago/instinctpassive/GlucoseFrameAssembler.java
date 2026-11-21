package com.lucyago.instinctpassive;

import java.util.Arrays;

/**
 * Offline-only framing helper. Does not communicate with a sensor.
 * Frame length must be verified against the real protocol before use.
 * Deliberately does not parse glucose values or implement cryptography.
 */
public final class GlucoseFrameAssembler {
    public interface Listener {
        void onCompleteEncryptedFrame(byte[] frame);
    }
    private final byte[] buffer;
    private final Listener listener;
    private int filled;

    public GlucoseFrameAssembler(int frameLength, Listener listener) {
        if (frameLength < 1 || frameLength > 4096 || listener == null)
            throw new IllegalArgumentException("Invalid frame length or listener");
        this.buffer = new byte[frameLength];
        this.listener = listener;
    }

    /** Supply fragments in order, from one verified authenticated session. */
    public synchronized void accept(byte[] fragment) {
        if (fragment == null || fragment.length == 0 || fragment.length > buffer.length - filled) {
            reset();
            throw new IllegalArgumentException("Missing or oversized fragment; frame reset");
        }
        System.arraycopy(fragment, 0, buffer, filled, fragment.length);
        filled += fragment.length;
        if (filled == buffer.length) {
            byte[] completed = Arrays.copyOf(buffer, buffer.length);
            reset();
            listener.onCompleteEncryptedFrame(completed);
        }
    }

    public synchronized int pendingBytes() { return filled; }

    public synchronized void reset() {
        Arrays.fill(buffer, (byte)0);
        filled = 0;
    }
}
