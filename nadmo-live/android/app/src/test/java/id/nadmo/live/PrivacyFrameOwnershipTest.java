package id.nadmo.live;

import org.junit.Test;
import org.webrtc.VideoFrame;
import static org.junit.Assert.*;

/** Recreates 50,000 privacy frames without JNI or a real device encoder. */
public class PrivacyFrameOwnershipTest {
  static final class FakeBuffer implements VideoFrame.Buffer {
    private int refs=1;
    boolean freed=false;
    @Override public int getWidth(){return 640;}
    @Override public int getHeight(){return 360;}
    @Override public VideoFrame.I420Buffer toI420(){return null;}
    @Override public VideoFrame.Buffer cropAndScale(int x,int y,int w,int h,int sw,int sh){throw new UnsupportedOperationException();}
    @Override public synchronized void retain(){if(refs<=0)throw new IllegalStateException("retain after free");refs++;}
    @Override public synchronized void release(){if(refs<=0)throw new IllegalStateException("double free");if(--refs==0)freed=true;}
    int refs(){return refs;}
  }
  @Test public void reusingOpaqueBufferNeverFreesCacheBetweenFrames(){
    FakeBuffer cached=new FakeBuffer();
    for(int i=0;i<50000;i++){
      VideoFrame frame=PrivacyFrameOwnership.newRetainedFrame(cached,0,i);
      assertEquals(2,cached.refs());
      frame.release();
      assertEquals("Cached backing memory must survive frame "+i,1,cached.refs());
      assertFalse(cached.freed);
    }
    cached.release();
    assertTrue(cached.freed);
    assertEquals(0,cached.refs());
  }
  @Test public void retainedSinkOutlivesCacheRotationSafely(){
    FakeBuffer old=new FakeBuffer();
    VideoFrame outbound=PrivacyFrameOwnership.newRetainedFrame(old,90,1337);
    old.release(); // Dimension changes, cache replaces old buffer.
    assertEquals(1,old.refs());
    assertFalse(old.freed);
    outbound.release();
    assertEquals(0,old.refs());
    assertTrue(old.freed);
  }
  @Test public void brokenOriginalPatternWouldFreeCacheOnFirstFrame(){
    FakeBuffer old=new FakeBuffer();
    VideoFrame frame=new VideoFrame(old,0,1);
    frame.release(); // Original implementation then reused old incorrectly.
    assertTrue(old.freed);
    assertEquals(0,old.refs());
  }
}
