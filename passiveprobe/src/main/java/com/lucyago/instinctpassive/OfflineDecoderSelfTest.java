package com.lucyago.instinctpassive;

/** Synthetic test: never a clinical glucose measurement. */
public final class OfflineDecoderSelfTest {
    public static void main(String[] args) {
        long now=1800000000000L;
        final int[] count={0};
        OfflineGlucosePipeline blocked=new OfflineGlucosePipeline(new InstinctGoDecoder(),r->count[0]++);
        try {
            blocked.acceptEncryptedFrame(new byte[]{1,2,3},now);
            throw new AssertionError("Unverified decoder must fail");
        } catch(SecurityException expected) {}
        if(count[0]!=0)throw new AssertionError("Unexpected candidate");
        AuthenticatedGlucoseDecoder fake=new AuthenticatedGlucoseDecoder(){
            public String decoderId(){return "synthetic-test";}
            public GlucoseReading decode(byte[] bytes,long received){
                return new GlucoseReading(123,received,"synthetic-test");
            }
        };
        OfflineGlucosePipeline pipeline=new OfflineGlucosePipeline(fake,r->{
            if(r.mgDl!=123)throw new AssertionError("Wrong synthetic value");
            count[0]++;
        });
        pipeline.acceptEncryptedFrame(new byte[]{5},now);
        if(count[0]!=1)throw new AssertionError("No candidate");
        try {
            GlucoseReadingValidator.requirePlausible(new GlucoseReading(123,now-700000L,"synthetic-test"),now);
            throw new AssertionError("Stale reading accepted");
        }catch(IllegalArgumentException expected){}
        System.out.println("Offline decoder pipeline tests OK (synthetic data only)");
    }
}
