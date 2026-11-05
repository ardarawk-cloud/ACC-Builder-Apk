package id.nadmo.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameRateGateTest {
  @Test public void highRefreshGameCannotOverrunCaptureBudget() {
    for(int refresh:new int[]{60,90,120})for(int fps:new int[]{15,18,20}) {
      FrameRateGate gate=new FrameRateGate(fps);int frames=0;
      for(int i=0;i<refresh*10;i++)if(gate.allow(i*1000000000L/refresh))frames++;
      assertTrue("refresh="+refresh+" fps="+fps+" frames="+frames,frames<=fps*10+1);
      assertTrue("Frame pacing must not starve gameplay",frames>=fps*10-1);
    }
  }
  @Test public void radioOnlyProcessesTwoSyntheticFramesPerSecond() {
    FrameRateGate gate=new FrameRateGate(2);int frames=0;
    for(int i=0;i<1200;i++)if(gate.allow(i*1000000000L/120))frames++;
    assertEquals(20,frames);
  }
  @Test public void timestampRestartAndLongPauseRecoverImmediately() {
    FrameRateGate gate=new FrameRateGate(18);
    assertTrue(gate.allow(5000000000L));assertFalse(gate.allow(5000000001L));
    assertTrue(gate.allow(100));assertTrue(gate.allow(9000000000L));
    assertFalse(gate.allow(9000000001L));
  }
}
