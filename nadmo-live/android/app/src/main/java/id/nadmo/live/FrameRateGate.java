package id.nadmo.live;

/** Capture-thread frame budget, independent of display refresh rate. No frame queue. */
final class FrameRateGate {
  private final long intervalNs;
  private long nextNs=Long.MIN_VALUE,previousNs=Long.MIN_VALUE;
  FrameRateGate(int fps) {
    if(fps<1||fps>120)throw new IllegalArgumentException("fps");
    intervalNs=1000000000L/fps;
  }
  boolean allow(long timestampNs) {
    if(nextNs==Long.MIN_VALUE||timestampNs<previousNs||timestampNs-nextNs>intervalNs*2) {
      previousNs=timestampNs;nextNs=timestampNs+intervalNs;return true;
    }
    previousNs=timestampNs;
    if(timestampNs<nextNs)return false;
    nextNs+=intervalNs;
    return true;
  }
}
