package com.lucyago.instinctpassive;

/**
 * Evidence of successful cryptographic authentication.
 * No caller can manufacture a successful result using a boolean alone.
 * This is still an offline abstraction, not a working Instinct Go authenticator.
 */
public final class AuthenticationEvidence {
    public enum Protocol { LIBRE3_REFERENCE, INSTINCT_GO_VERIFIED }
    private final long sessionToken;
    private final Protocol protocol;
    private final boolean challengeVerified;
    private final boolean sensorIdentityVerified;
    private AuthenticationEvidence(long token, Protocol protocol, boolean challenge, boolean identity) {
        this.sessionToken=token; this.protocol=protocol;
        this.challengeVerified=challenge; this.sensorIdentityVerified=identity;
    }
    /** Only real protocol adapter may invoke this after cryptographic verification. */
    static AuthenticationEvidence fromVerifiedProtocol(long token, Protocol protocol,
                                                        boolean challenge, boolean identity) {
        if (token<=0 || protocol!=Protocol.INSTINCT_GO_VERIFIED || !challenge || !identity)
            throw new IllegalArgumentException("Instinct Go authentication not verified");
        return new AuthenticationEvidence(token,protocol,challenge,identity);
    }
    long sessionToken() { return sessionToken; }
    boolean isVerified() {
        return protocol==Protocol.INSTINCT_GO_VERIFIED && challengeVerified && sensorIdentityVerified;
    }
}
