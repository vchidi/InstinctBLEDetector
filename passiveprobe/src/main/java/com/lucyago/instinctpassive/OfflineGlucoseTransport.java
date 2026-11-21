package com.lucyago.instinctpassive;

import java.util.Arrays;

/**
 * Demonstrates a guarded receive path without hardware or sensor credentials.
 * Only encrypted frames are forwarded. No glucose decoding or display.
 */
public final class OfflineGlucoseTransport {
    public interface EncryptedFrameSink {
        void onEncryptedFrame(byte[] encryptedFrame);
    }
    private final SessionGate gate = new SessionGate();
    private final GlucoseFrameAssembler assembler;
    private final EncryptedFrameSink sink;
    private long token;

    public OfflineGlucoseTransport(int expectedEncryptedFrameLength, EncryptedFrameSink sink) {
        if (sink == null) throw new IllegalArgumentException("Missing sink");
        this.sink = sink;
        assembler = new GlucoseFrameAssembler(expectedEncryptedFrameLength, frame -> {
            gate.requireAuthenticated(token);
            this.sink.onEncryptedFrame(Arrays.copyOf(frame, frame.length));
        });
    }
    public synchronized long connected() {
        assembler.reset();
        token = gate.connected();
        return token;
    }
    /** A real implementation must never pass true until cryptographic verification succeeds. */
    public synchronized void verifiedAuthentication(long sessionToken, boolean verified) {
        gate.authenticated(sessionToken, verified);
    }
    public synchronized void encryptedFragment(long sessionToken, byte[] fragment) {
        gate.requireAuthenticated(sessionToken);
        assembler.accept(fragment);
    }
    public synchronized void disconnected() {
        assembler.reset();
        gate.disconnected();
    }
    public synchronized SessionGate.State state() { return gate.state(); }
}
