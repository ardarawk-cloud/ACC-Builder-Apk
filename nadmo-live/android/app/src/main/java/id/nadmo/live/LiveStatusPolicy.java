package id.nadmo.live;

/**
 * Only an actively transmitting native live session requires an additional
 * pause command when the Activity is visible. A STARTING/RECONNECTING
 * status can be emitted by a service that is ALREADY privacy-paused; feeding
 * it back as ACTION_PAUSE causes an unbounded cross-process message loop.
 */
final class LiveStatusPolicy {
  private LiveStatusPolicy(){}
  static boolean shouldEnforceForegroundPrivacy(boolean activityVisible,String status){
    return activityVisible && "live".equals(status);
  }
}
