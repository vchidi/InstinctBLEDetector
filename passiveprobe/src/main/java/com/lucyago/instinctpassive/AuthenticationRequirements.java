package com.lucyago.instinctpassive;

/** Checklist only: never stores or logs keys, PINs or authorization material. */
public final class AuthenticationRequirements {
    private AuthenticationRequirements() {}
    public static String[] unresolved() {
        return new String[]{
            "Instinct Go model/protocol identity not verified",
            "Authorization source for Instinct Go not established",
            "Challenge/response compatibility with Juggluco not verified",
            "Encrypted glucose frame format not verified",
            "No active sensor testing authorized"
        };
    }
}
