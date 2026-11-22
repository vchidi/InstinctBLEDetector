package com.lucyago.instinctpassive;

/** Candidate reading from a verified decoder. Never for dosing decisions. */
public final class GlucoseReading {
    public final int mgDl;
    public final long timestampMs;
    public final String decoderId;
    public GlucoseReading(int mgDl, long timestampMs, String decoderId) {
        if (mgDl < 20 || mgDl > 600 || timestampMs <= 0 ||
                decoderId == null || decoderId.isEmpty())
            throw new IllegalArgumentException("Invalid candidate reading");
        this.mgDl=mgDl;
        this.timestampMs=timestampMs;
        this.decoderId=decoderId;
    }
}
