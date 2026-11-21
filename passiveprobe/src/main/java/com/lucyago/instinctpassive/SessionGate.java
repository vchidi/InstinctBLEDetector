package com.lucyago.instinctpassive;

/**
 * Offline state gate. Does not authenticate a real sensor.
 * Only a verified external authenticator may open a transport session.
 */
public final class SessionGate {
    public enum State { DISCONNECTED, CONNECTED_UNAUTHENTICATED, AUTHENTICATED, CLOSED }
    private State state = State.DISCONNECTED;
    private long session = 0;

    public synchronized long connected() {
        if (state != State.DISCONNECTED && state != State.CLOSED)
            throw new IllegalStateException("Session already active");
        session++;
        state = State.CONNECTED_UNAUTHENTICATED;
        return session;
    }
    public synchronized void authenticated(long token, boolean verified) {
        if (token != session || state != State.CONNECTED_UNAUTHENTICATED || !verified)
            throw new IllegalStateException("Unverified or stale authentication");
        state = State.AUTHENTICATED;
    }
    public synchronized void requireAuthenticated(long token) {
        if (token != session || state != State.AUTHENTICATED)
            throw new IllegalStateException("No authenticated session");
    }
    public synchronized void disconnected() { state = State.CLOSED; }
    public synchronized State state() { return state; }
}
