package id.nadmo.live;

import org.junit.Test;
import static org.junit.Assert.*;
import java.lang.reflect.Method;

/** Wire-level sanity test: Android ADPCM packets must be independently
 * decodable even when an unreliable WebRTC data channel loses a packet. */
public class GameAudioRelayCodecTest {
  private static final int[] STEPS={
    7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,
    107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,
    876,963,1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,
    4428,4871,5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,
    18500,20350,22385,24623,27086,29794,32767
  };
  private static final int[] INDEX={-1,-1,-1,-1,2,4,6,8};

  private static short[] decode(byte[] packet){
    assertEquals("99.2kbps frame must be 248 bytes",248,packet.length);
    assertEquals('N',packet[0]);assertEquals('A',packet[1]);
    assertEquals('D',packet[2]);assertEquals('1',packet[3]);
    assertEquals("Reserved protocol byte",0,packet[7]);
    int predictor=(short)((packet[4]&255)|((packet[5]&255)<<8));
    int index=packet[6]&255;
    assertTrue("ADPCM index range",index<=88);
    short[] output=new short[480];output[0]=(short)predictor;
    for(int i=1;i<output.length;i++){
      int octet=packet[8+((i-1)>>1)]&255;
      int code=(i&1)==1?(octet&15):(octet>>>4);
      int step=STEPS[index],delta=step>>3;
      if((code&4)!=0)delta+=step;
      if((code&2)!=0)delta+=step>>1;
      if((code&1)!=0)delta+=step>>2;
      predictor+=(code&8)!=0?-delta:delta;
      predictor=Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,predictor));
      index=Math.max(0,Math.min(88,index+INDEX[code&7]));
      output[i]=(short)predictor;
    }
    return output;
  }

  @Test public void encodedFramesAreSmallAndSelfContained() throws Exception {
    GameAudioRelay relay=new GameAudioRelay();
    Method encode=GameAudioRelay.class.getDeclaredMethod("encodeAdpcm",short[].class,byte[].class);
    encode.setAccessible(true);
    byte[][] wire=new byte[4][248];
    short[][] original=new short[4][480];
    for(int frame=0;frame<4;frame++){
      for(int i=0;i<480;i++){
        original[frame][i]=(short)Math.round(11000.0*
          Math.sin(2.0*Math.PI*440.0*(i+frame*480)/24000.0));
      }
      encode.invoke(relay,original[frame],wire[frame]);
    }
    // Simulates out-of-order/dropped frames: decoding relies only on its own header.
    for(int frame=wire.length-1;frame>=0;frame--){
      short[] decoded=decode(wire[frame]);
      assertEquals("First sample is an exact per-packet predictor",original[frame][0],decoded[0]);
      long totalError=0;
      for(int i=0;i<480;i++)totalError+=Math.abs(original[frame][i]-decoded[i]);
      assertTrue("Excess codec distortion for frame "+frame+" mean="+totalError/480,
        totalError/480<500);
    }
  }
}
