package com.lucyago.instinctpassive;

import java.util.Arrays;

/** Pure Java test, no Android, no Bluetooth, no sensor access. */
public final class OfflineFrameSelfTest {
    public static void main(String[] args) {
        final int[] callbacks={0};
        GlucoseFrameAssembler a=new GlucoseFrameAssembler(6,frame -> {
            if (!Arrays.equals(frame,new byte[]{1,2,3,4,5,6}))
                throw new AssertionError("Unexpected assembled frame");
            callbacks[0]++;
        });
        a.accept(new byte[]{1,2});
        if(a.pendingBytes()!=2)throw new AssertionError("Pending byte count");
        a.accept(new byte[]{3,4,5,6});
        if(callbacks[0]!=1||a.pendingBytes()!=0)throw new AssertionError("Completion");
        a.accept(new byte[]{1});
        try {
            a.accept(new byte[]{2,3,4,5,6,7});
            throw new AssertionError("Oversized fragment should fail");
        } catch (IllegalArgumentException expected) {
            if(a.pendingBytes()!=0)throw new AssertionError("Must reset on invalid fragment");
        }
        System.out.println("Offline framing tests OK");
    }
}
