package com.lucyago.instinctpassive;

import java.util.Arrays;

/** Deterministic test with synthetic bytes, not real glucose data. */
public final class OfflineTransportSelfTest {
    private static void mustReject(Runnable call) {
        try { call.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalStateException expected) {}
    }
    public static void main(String[] args) {
        final int[] received = {0};
        OfflineGlucoseTransport transport = new OfflineGlucoseTransport(4, frame -> {
            if (!Arrays.equals(frame, new byte[]{10,20,30,40}))
                throw new AssertionError("Wrong frame");
            received[0]++;
        });
        long first = transport.connected();
        mustReject(() -> transport.encryptedFragment(first, new byte[]{10}));
        mustReject(() -> transport.verifiedAuthentication(first, false));
        transport.verifiedAuthentication(first, true); // SIMULATED verification only
        transport.encryptedFragment(first, new byte[]{10,20});
        transport.encryptedFragment(first, new byte[]{30,40});
        if (received[0] != 1) throw new AssertionError("Frame not delivered");
        transport.disconnected();
        mustReject(() -> transport.encryptedFragment(first, new byte[]{10}));
        long second = transport.connected();
        if (second == first) throw new AssertionError("Session token reused");
        mustReject(() -> transport.encryptedFragment(first, new byte[]{10}));
        mustReject(() -> transport.verifiedAuthentication(first, true));
        transport.verifiedAuthentication(second, true); // SIMULATED verification only
        transport.encryptedFragment(second, new byte[]{10,20,30,40});
        if (received[0] != 2) throw new AssertionError("Second session failed");
        System.out.println("Offline guarded transport tests OK");
    }
}
