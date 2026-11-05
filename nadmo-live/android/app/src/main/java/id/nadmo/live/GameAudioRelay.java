package id.nadmo.live;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import org.webrtc.DataChannel;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Low-latency GAME audio uplink. Uses the EXISTING MediaProjection granted for
 * ScreenCapturerAndroid; requesting a second projection token would fail on
 * Android 14+. AudioPlaybackCapture will only return audio when the game permits
 * capture. Microphone remains a separate, ordinary WebRTC AudioTrack.
 *
 * Sends independent 20ms 24kHz mono IMA-ADPCM frames (~99 kbps instead of
 * 384 kbps PCM per viewer) through unreliable WebRTC DATA CHANNEL frames.
 * Never sends audio over the signaling WebSocket or stores captured audio.
 * Viewers play these frames through WebAudio beside the regular mic track.
 */
final class GameAudioRelay {
  private static final String TAG="NadmoGameAudio";
  // 48 kHz Android capture downsampled to 24 kHz. Each DATA CHANNEL frame is
  // independently decodable, so lost/out-of-order packets do not poison audio.
  private static final int CAPTURE_RATE=48000;
  private static final int TRANSPORT_RATE=24000;
  private static final int FRAME_MS=20;
  private static final int CAPTURE_FRAME_BYTES=CAPTURE_RATE*2*FRAME_MS/1000;
  private static final int TRANSPORT_SAMPLES=TRANSPORT_RATE*FRAME_MS/1000;
  // 'NAD1' | 16-bit LE predictor | 8-bit step index | reserved 0 | 479 nibbles.
  // 248 bytes / 20ms => 99.2 kbps per viewer vs. 960-byte PCM => 384 kbps.
  private static final int ADPCM_HEADER_BYTES=8;
  private static final int ADPCM_FRAME_BYTES=ADPCM_HEADER_BYTES+(TRANSPORT_SAMPLES/2);
  private static final int[] ADPCM_STEPS={
    7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,
    107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,
    876,963,1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,
    4428,4871,5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,
    18500,20350,22385,24623,27086,29794,32767
  };
  private static final int[] ADPCM_INDEX={-1,-1,-1,-1,2,4,6,8};
  private static final long MAX_BUFFERED_BYTES=2048L; // Avoid large audio delay on slow peers.
  private final Map<String,DataChannel> channels=new ConcurrentHashMap<>();
  private AudioRecord record;
  private Thread reader;
  private volatile boolean running=false;
  // Stop delivering GAME PCM at the same instant the video privacy shield engages.
  private volatile boolean privacyPaused=true;
  private volatile boolean capturedSamples=false;
  private volatile long lastPacketAt=0L;
  private int adpcmIndex=0;

  boolean start(MediaProjection projection){
    if(Build.VERSION.SDK_INT<29||projection==null)return false;
    try{
      AudioPlaybackCaptureConfiguration rule=
        new AudioPlaybackCaptureConfiguration.Builder(projection)
          .addMatchingUsage(AudioAttributes.USAGE_GAME)
          .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
          .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
          .build();
      int minimum=AudioRecord.getMinBufferSize(CAPTURE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
      if(minimum<=0)throw new IllegalStateException("Unsupported audio format");
      AudioFormat format=new AudioFormat.Builder().setSampleRate(CAPTURE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
      record=new AudioRecord.Builder().setAudioFormat(format)
        .setBufferSizeInBytes(Math.max(minimum,CAPTURE_FRAME_BYTES*10))
        .setAudioPlaybackCaptureConfig(rule).build();
      if(record.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("AudioRecord not ready");
      record.startRecording();
      if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("AudioRecord permission denied");
      running=true;
      reader=new Thread(this::readLoop,"NadmoGameAudioPCM");
      reader.setDaemon(true);reader.start();
      Log.i(TAG,"Game playback capture started; audible samples depend on source app policy");
      return true;
    }catch(Exception ex){
      Log.w(TAG,"Game playback not available; keep microphone stream active",ex);
      closeRecorder();
      return false;
    }
  }

  void attach(String id,DataChannel channel){
    if(channel==null)return;
    DataChannel old=channels.put(id,channel);
    if(old!=null&&old!=channel){try{old.close();old.dispose();}catch(Exception ignored){}}
  }
  void detach(String id){
    DataChannel old=channels.remove(id);
    if(old!=null){try{old.close();old.dispose();}catch(Exception ignored){}}
  }
  boolean isCapturing(){return running;}
  void setPaused(boolean paused){privacyPaused=paused;}
  boolean receivedAudibleSamples(){return capturedSamples;}
  long lastPacketAt(){return lastPacketAt;}

  private void readLoop(){
    byte[] frame=new byte[CAPTURE_FRAME_BYTES];
    short[] samples=new short[TRANSPORT_SAMPLES];
    byte[] packet=new byte[ADPCM_FRAME_BYTES];
    try{
      while(running){
        int pos=0;
        while(pos<CAPTURE_FRAME_BYTES&&running){
          AudioRecord r=record;
          if(r==null)return;
          int n=r.read(frame,pos,CAPTURE_FRAME_BYTES-pos);
          if(n<=0){if(n<0)Log.w(TAG,"Playback audio record read failed "+n);running=false;break;}
          pos+=n;
        }
        if(!running||pos!=CAPTURE_FRAME_BYTES)break;
        if(privacyPaused)continue; // Privacy first: no private app/game audio while paused.
        boolean audible=false;
        // 48 -> 24 kHz averaging adjacent samples, preserving signed PCM.
        for(int i=0;i<TRANSPORT_SAMPLES;i++){
          int k=i*4;
          int sample1=(short)((frame[k]&255)|((frame[k+1]&255)<<8));
          int sample2=(short)((frame[k+2]&255)|((frame[k+3]&255)<<8));
          int mono=(sample1+sample2)/2;
          samples[i]=(short)mono;
          if(mono>220||mono<-220)audible=true;
        }
        if(audible)capturedSamples=true;
        encodeAdpcm(samples,packet);
        // Send the actual decoded playback samples. Do not fabricate sound
        // or claim that a source game permits playback capture when silent.
        boolean sent=false;
        for(DataChannel channel:channels.values()){
          try{
            if(channel.state()!=DataChannel.State.OPEN||channel.bufferedAmount()>MAX_BUFFERED_BYTES)continue;
            byte[] coded=packet.clone(); // immutable independent 24kHz GAME audio frame
            if(channel.send(new DataChannel.Buffer(ByteBuffer.wrap(coded),true)))sent=true;
          }catch(Exception ex){Log.w(TAG,"GAME audio peer send error",ex);}
        }
        if(sent)lastPacketAt=android.os.SystemClock.elapsedRealtime();
      }
    }finally{running=false;closeRecorder();}
  }
  // Stateful encoder for compression efficiency, stateless frame decoding.
  // Each 20ms frame embeds the predictor and the starting step index.
  private void encodeAdpcm(short[] pcm,byte[] packet){
    Arrays.fill(packet,(byte)0);
    packet[0]=78;packet[1]=65;packet[2]=68;packet[3]=49; // 'NAD1'
    int predictor=pcm[0],index=adpcmIndex;
    packet[4]=(byte)(predictor&255);
    packet[5]=(byte)((predictor>>>8)&255);
    packet[6]=(byte)index;
    for(int i=1;i<pcm.length;i++){
      int diff=pcm[i]-predictor;
      int sign=diff<0?8:0;
      if(diff<0)diff=-diff;
      int step=ADPCM_STEPS[index],delta=step>>3,code=0;
      if(diff>=step){code|=4;diff-=step;delta+=step;}
      if(diff>=step/2){code|=2;diff-=step/2;delta+=step/2;}
      if(diff>=step/4){code|=1;delta+=step/4;}
      predictor+=sign!=0?-delta:delta;
      predictor=Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,predictor));
      index=Math.max(0,Math.min(88,index+ADPCM_INDEX[code]));
      int offset=ADPCM_HEADER_BYTES+((i-1)>>1),nibble=code|sign;
      if((i&1)==1)packet[offset]=(byte)nibble;
      else packet[offset]=(byte)((packet[offset]&15)|(nibble<<4));
    }
    adpcmIndex=index;
  }

  void stop(){
    running=false;
    closeRecorder();
    Thread t=reader;
    if(t!=null&&t!=Thread.currentThread()){
      try{t.join(400);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    reader=null;
    for(String id:channels.keySet())detach(id);
  }
  private synchronized void closeRecorder(){
    AudioRecord r=record;record=null;
    if(r!=null){try{r.stop();}catch(Exception ignored){}
      try{r.release();}catch(Exception ignored){}}
  }
}
