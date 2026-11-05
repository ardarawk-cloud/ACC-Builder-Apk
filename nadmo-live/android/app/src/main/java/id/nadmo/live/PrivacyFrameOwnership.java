package id.nadmo.live;

import org.webrtc.VideoFrame;

/** Transfer ONE retained frame reference; leave cached buffer's original ref untouched. */
final class PrivacyFrameOwnership {
  private PrivacyFrameOwnership(){}
  static VideoFrame newRetainedFrame(VideoFrame.Buffer cached,int rotation,long timestampNs){
    if(cached==null)throw new IllegalArgumentException("Missing opaque privacy buffer");
    cached.retain(); // new VideoFrame takes ownership; its release consumes only this reference.
    try{
      return new VideoFrame(cached,rotation,timestampNs);
    }catch(RuntimeException error){
      cached.release();
      throw error;
    }
  }
}
