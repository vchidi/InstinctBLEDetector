package com.lucyago.instinctpassive;

/** All values are synthetic. No sensor or clinical glucose involved. */
public final class VerifiedSessionGlucosePathSelfTest {
    public static void main(String[] args) {
        final int[] readings={0};
        AuthenticatedGlucoseDecoder synthetic=new AuthenticatedGlucoseDecoder(){
            public String decoderId(){return "synthetic-test";}
            public GlucoseReading decode(byte[] frame,long now) {
                if(frame.length!=4 || frame[0]!=1 || frame[3]!=4)
                    throw new IllegalArgumentException("Unexpected frame");
                return new GlucoseReading(120,now,decoderId());
            }
        };
        VerifiedSessionGlucosePath path=new VerifiedSessionGlucosePath(4,synthetic,r->readings[0]++);
        long first=path.connected();
        expectFailure(()->path.encryptedFragment(first,new byte[]{1,2}),"unauthenticated fragment");
        expectFailure(()->path.authenticationResult(first,false),"false authentication");
        path.authenticationResult(first,true); // SYNTHETIC AUTHENTICATOR ONLY
        path.encryptedFragment(first,new byte[]{1,2});
        path.encryptedFragment(first,new byte[]{3,4});
        if(readings[0]!=1)throw new AssertionError("Expected one synthetic reading");
        path.disconnected();
        expectFailure(()->path.encryptedFragment(first,new byte[]{1}),"disconnected fragment");
        long second=path.connected();
        if(second==first)throw new AssertionError("Session token not rotated");
        expectFailure(()->path.authenticationResult(first,true),"stale authentication");
        path.authenticationResult(second,true);
        path.encryptedFragment(second,new byte[]{1,2});
        path.disconnected(); // Must clear partial frame
        long third=path.connected();
        path.authenticationResult(third,true);
        path.encryptedFragment(third,new byte[]{1,2,3,4});
        if(readings[0]!=2)throw new AssertionError("Partial frame leaked across sessions");
        path.disconnected();

        VerifiedSessionGlucosePath real=new VerifiedSessionGlucosePath(
            4,new InstinctGoDecoder(),r->readings[0]++);
        long token=real.connected();
        real.authenticationResult(token,true); // synthetic gate; real decoder MUST reject
        expectFailure(()->real.encryptedFragment(token,new byte[]{1,2,3,4}),
            "unverified Instinct Go decoder");
        if(readings[0]!=2)throw new AssertionError("Fabricated glucose emitted");
        real.disconnected();
        System.out.println("Integrated offline glucose session tests OK (synthetic only)");
    }
    private static void expectFailure(Runnable action,String label) {
        try {action.run();throw new AssertionError("Expected rejection: "+label);}
        catch(SecurityException | IllegalArgumentException | IllegalStateException expected){}
    }
}
