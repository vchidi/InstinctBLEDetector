package com.lucyago.instinctpassive;

import java.util.Arrays;

/**
 * Complete offline data path: encrypted frame -> decoder -> sanity check -> candidate.
 * No hardware access. The real InstinctGoDecoder intentionally rejects every frame.
 */
public final class OfflineGlucosePipeline {
    public interface CandidateSink { void onCandidate(GlucoseReading candidate); }
    private final AuthenticatedGlucoseDecoder decoder;
    private final CandidateSink sink;
    public OfflineGlucosePipeline(AuthenticatedGlucoseDecoder decoder, CandidateSink sink) {
        if (decoder==null || sink==null) throw new IllegalArgumentException("Missing component");
        this.decoder=decoder; this.sink=sink;
    }
    public void acceptEncryptedFrame(byte[] encrypted, long receivedAtMs) {
        if (encrypted==null || encrypted.length==0 || encrypted.length>4096)
            throw new IllegalArgumentException("Invalid frame");
        GlucoseReading reading=decoder.decode(Arrays.copyOf(encrypted,encrypted.length),receivedAtMs);
        if (!decoder.decoderId().equals(reading.decoderId))
            throw new SecurityException("Decoder identity mismatch");
        GlucoseReadingValidator.requirePlausible(reading,receivedAtMs);
        sink.onCandidate(reading);
    }
}
