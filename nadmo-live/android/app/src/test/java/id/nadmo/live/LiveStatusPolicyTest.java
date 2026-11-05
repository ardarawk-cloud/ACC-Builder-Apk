package id.nadmo.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiveStatusPolicyTest {
  @Test public void activityVisibleOnlyLiveNeedsPause(){
    assertTrue(LiveStatusPolicy.shouldEnforceForegroundPrivacy(true,"live"));
    for(String status:new String[]{"starting","reconnecting","paused","stopped","error",null,""}){
      assertFalse("Cannot feedback PAUSE for "+status,LiveStatusPolicy.shouldEnforceForegroundPrivacy(true,status));
    }
    assertFalse(LiveStatusPolicy.shouldEnforceForegroundPrivacy(false,"live"));
  }
  @Test public void repeatedStartingAndReconnectingMustNeverGeneratePause(){
    for(int round=0;round<10000;round++){
      assertFalse(LiveStatusPolicy.shouldEnforceForegroundPrivacy(true,"starting"));
      assertFalse(LiveStatusPolicy.shouldEnforceForegroundPrivacy(true,"reconnecting"));
    }
  }
}
