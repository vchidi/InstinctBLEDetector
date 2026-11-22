package com.lucyago.instinctpassive;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
public final class GattFingerprintSelfTest {
    public static void main(String[] args) {
        Libre3GattFingerprint.Result empty=Libre3GattFingerprint.inspect(
            Collections.<UUID>emptyList(),Collections.<UUID>emptyList());
        if(empty.hasExpectedLayout())throw new AssertionError("False positive");
        Libre3GattFingerprint.Result complete=Libre3GattFingerprint.inspect(
            Arrays.asList(Libre3GattFingerprint.DATA_SERVICE,Libre3GattFingerprint.SECURITY_SERVICE),
            Arrays.asList(Libre3GattFingerprint.GLUCOSE,Libre3GattFingerprint.PATCH_CONTROL,
                Libre3GattFingerprint.COMMAND_RESPONSE,Libre3GattFingerprint.CHALLENGE,
                Libre3GattFingerprint.CERTIFICATE));
        if(!complete.hasExpectedLayout())throw new AssertionError("Missed expected UUIDs");
        Libre3GattFingerprint.Result incomplete=Libre3GattFingerprint.inspect(
            Arrays.asList(Libre3GattFingerprint.DATA_SERVICE,Libre3GattFingerprint.SECURITY_SERVICE),
            Arrays.asList(Libre3GattFingerprint.GLUCOSE,Libre3GattFingerprint.PATCH_CONTROL,
                Libre3GattFingerprint.COMMAND_RESPONSE,Libre3GattFingerprint.CHALLENGE));
        if(incomplete.hasExpectedLayout())throw new AssertionError("Missing certificate not detected");
        System.out.println("GATT fingerprint tests OK (synthetic UUID sets)");
    }
}
