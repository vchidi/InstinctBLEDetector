package com.lucyago.instinctpassive;

/**
 * Secondary sanity filter; it does not establish clinical accuracy.
 * Real comparisons with the official receiver are still required.
 */
public final class GlucoseReadingValidator {
    private GlucoseReadingValidator() {}
    public static void requirePlausible(GlucoseReading reading, long nowMs) {
        if (reading == null || nowMs <= 0) throw new IllegalArgumentException("Missing reading");
        if (reading.timestampMs > nowMs + 120000L ||
                nowMs - reading.timestampMs > 600000L)
            throw new IllegalArgumentException("Stale or future glucose reading");
        if (reading.mgDl < 40 || reading.mgDl > 400)
            throw new IllegalArgumentException("Outside conservative diagnostic display range");
    }
}
