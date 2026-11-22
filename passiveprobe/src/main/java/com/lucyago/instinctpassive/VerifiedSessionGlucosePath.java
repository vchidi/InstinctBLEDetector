package com.lucyago.instinctpassive;

/**
 * Offline integration: authenticated session -> encrypted fragments -> decoder.
 *
 * Authentication must be confirmed cryptographically by a separate implementation.
 * This class never performs BLE, never accepts a PIN as proof, and never returns
 * a glucose value from the unverified Instinct Go decoder.
 */
public final class VerifiedSessionGlucosePath {
    private final OfflineGlucoseTransport transport;
    private final OfflineGlucosePipeline pipeline;
    private long sessionToken;
    private boolean connected;

    public VerifiedSessionGlucosePath(int encryptedFrameLength,
                                     AuthenticatedGlucoseDecoder decoder,
                                     OfflineGlucosePipeline.CandidateSink sink) {
        pipeline=new OfflineGlucosePipeline(decoder,sink);
        transport=new OfflineGlucoseTransport(encryptedFrameLength,
            frame->pipeline.acceptEncryptedFrame(frame,System.currentTimeMillis()));
    }

    public synchronized long connected() {
        if(connected)throw new IllegalStateException("Already connected");
        sessionToken=transport.connected();
        connected=true;
        return sessionToken;
    }

    /**
     * This API does not prove authentication; caller MUST be a verified
     * cryptographic authenticator. Never call with true on BLE connect alone.
     */
    public synchronized void authenticationResult(long token, boolean cryptographicallyVerified) {
        transport.verifiedAuthentication(token,cryptographicallyVerified);
    }

    public synchronized void encryptedFragment(long token,byte[] fragment) {
        transport.encryptedFragment(token,fragment);
    }

    public synchronized void disconnected() {
        transport.disconnected();
        connected=false;
    }

    public synchronized SessionGate.State state() { return transport.state(); }
}
