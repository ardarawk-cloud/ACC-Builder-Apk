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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Low-latency GAME audio uplink. Uses the EXISTING MediaProjection granted for
 * ScreenCapturerAndroid; requesting a second projection token would fail on
 * Android 14+. AudioPlaybackCapture will only return audio when the game permits
 * capture. Microphone remains a separate, ordinary WebRTC AudioTrack.
 *
 * Sends raw 24kHz mono PCM as short, unreliable WebRTC DATA CHANNEL frames.
 * Never sends audio over the signaling WebSocket or stores captured audio.
 * Viewers play these frames through WebAudio beside the regular mic track.
 */
final class GameAudioRelay {
  private static final String TAG="NadmoGameAudio";
  // Android capture devices reliably support 48 kHz. Downsample before
  // transport so viewer bandwidth stays at ~384 kbit/s mono PCM.
  private static final int CAPTURE_RATE=48000;
  private static final int TRANSPORT_RATE=24000;
  private static final int FRAME_MS=20;
  private static final int CAPTURE_FRAME_BYTES=CAPTURE_RATE*2*FRAME_MS/1000;
  private static final int TRANSPORT_FRAME_BYTES=TRANSPORT_RATE*2*FRAME_MS/1000;
  private static final long MAX_BUFFERED_BYTES=8192L;
  private final Map<String,DataChannel> channels=new ConcurrentHashMap<>();
  private AudioRecord record;
  private Thread reader;
  private volatile boolean running=false;
  private volatile boolean capturedSamples=false;
  private volatile long lastPacketAt=0L;

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
  boolean receivedAudibleSamples(){return capturedSamples;}
  long lastPacketAt(){return lastPacketAt;}

  private void readLoop(){
    byte[] frame=new byte[CAPTURE_FRAME_BYTES];
    byte[] packet=new byte[TRANSPORT_FRAME_BYTES];
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
        boolean audible=false;
        // 48 -> 24 kHz averaging adjacent samples, preserving signed PCM.
        for(int i=0;i<TRANSPORT_FRAME_BYTES/2;i++){
          int k=i*4;
          int sample1=(short)((frame[k]&255)|((frame[k+1]&255)<<8));
          int sample2=(short)((frame[k+2]&255)|((frame[k+3]&255)<<8));
          int mono=(sample1+sample2)/2;
          packet[i*2]=(byte)(mono&255);packet[i*2+1]=(byte)((mono>>>8)&255);
          if(mono>220||mono<-220)audible=true;
        }
        if(audible)capturedSamples=true;
        // Send the actual decoded playback samples. Do not fabricate sound
        // or claim that a source game permits playback capture when silent.
        boolean sent=false;
        for(DataChannel channel:channels.values()){
          try{
            if(channel.state()!=DataChannel.State.OPEN||channel.bufferedAmount()>MAX_BUFFERED_BYTES)continue;
            byte[] pcm=packet.clone(); // each DataChannel receives an immutable 24kHz frame
            if(channel.send(new DataChannel.Buffer(ByteBuffer.wrap(pcm),true)))sent=true;
          }catch(Exception ex){Log.w(TAG,"GAME audio peer send error",ex);}
        }
        if(sent)lastPacketAt=android.os.SystemClock.elapsedRealtime();
      }
    }finally{running=false;closeRecorder();}
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
