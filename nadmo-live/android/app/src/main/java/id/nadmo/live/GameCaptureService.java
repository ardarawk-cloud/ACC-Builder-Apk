package id.nadmo.live;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Foreground screen broadcaster. GAME runs in another Android app while capture,
 * WebRTC microphone, optional front camera and NADMO signaling stay in this process.
 *
 * Does not capture internal game audio. Android's playback-capture permission and
 * game-specific opt-in require a separate tested audio path. Never imply otherwise.
 */
public final class GameCaptureService extends Service {
  static final String ACTION_START="id.nadmo.live.GAME_START";
  static final String ACTION_STOP="id.nadmo.live.GAME_STOP";
  static final String ACTION_STATUS="id.nadmo.live.GAME_STATUS";
  static final String EXTRA_PROJECTION="projection";
  static final String EXTRA_RESULT="result";
  static final String EXTRA_TITLE="title";
  static final String EXTRA_GAME="game";
  static final String EXTRA_FACE="face";
  static final String EXTRA_COOKIE="cookie";
  static final String EXTRA_FACE_LAYOUT="facecam";
  private static final String WS_URL="wss://nadmo-live-beta-20261009.ardarawk.workers.dev/ws";
  // Server checks Origin for CSWSH defense. OkHttp (unlike browser WebSocket) does not set it.
  private static final String WS_ORIGIN="https://nadmo-live-beta-20261009.ardarawk.workers.dev";
  private static final int NOTIFICATION_ID=7701;
  static final int CHAT_NOTIFICATION_ID=7702;
  private static final String CHAT_CHANNEL="nadmo_game_chat";
  private static final long CHAT_POPUP_MIN_INTERVAL_MS=1800;
  private long lastChatPopupAt=0L;
  private String hostSocketId="";
  private static final String CHANNEL="nadmo_game_live";
  private static final String TAG="NadmoGameLive";
  private final Handler main=new Handler(Looper.getMainLooper());
  private final Map<String,PeerConnection> viewers=new HashMap<>();
  private final Map<String,List<IceCandidate>> pendingIce=new HashMap<>();
  private OkHttpClient client;
  private WebSocket socket;
  private PeerConnectionFactory factory;
  private EglBase egl;
  private SurfaceTextureHelper screenHelper,faceHelper;
  private ScreenCapturerAndroid screenCapturer;
  private CameraVideoCapturer faceCapturer;
  private VideoSource screenSource,faceSource;
  private VideoTrack screenTrack,faceTrack;
  private AudioSource micSource;
  private AudioTrack micTrack;
  private String roomId="",resumeToken="",title="",game="",cookie="";
  private JSONObject faceLayout=null;
  private boolean stopping=false,started=false,wantFace=false,webSocketOpen=false;
  private long offlineSince=0L;
  private int reconnectAttempt=0;
  private Runnable retryTask;
  private Runnable socketWatchdog;
  private PowerManager.WakeLock cpuWakeLock;
  private long lastServerMessageAt=0;
  private long socketOpenedAt=0;
  private static final long SOCKET_STALE_MS=65000L;
  private static final long SOCKET_CONNECT_STALE_MS=25000L;
  private String diagnostic="Belum ada koneksi";
  static volatile String state="stopped",status="",activeRoom="";

  @Override public IBinder onBind(Intent intent){return null;}

  @Override public int onStartCommand(Intent intent,int flags,int startId){
    if(intent==null)return START_NOT_STICKY;
    if(ACTION_STOP.equals(intent.getAction())){stopGame("Siaran game diakhiri.");return START_NOT_STICKY;}
    if(!ACTION_START.equals(intent.getAction())||started)return START_NOT_STICKY;
    stopping=false;started=true;
    title=limit(intent.getStringExtra(EXTRA_TITLE),60,"NADMO GAME LIVE");
    game=limit(intent.getStringExtra(EXTRA_GAME),35,"Gaming");
    cookie=intent.getStringExtra(EXTRA_COOKIE);
    if(cookie==null)cookie="";
    wantFace=intent.getBooleanExtra(EXTRA_FACE,false);
    try{String raw=intent.getStringExtra(EXTRA_FACE_LAYOUT);if(wantFace&&raw!=null&&!raw.isEmpty())faceLayout=new JSONObject(raw);}catch(Exception ignored){}
    int result=intent.getIntExtra(EXTRA_RESULT,Activity.RESULT_CANCELED);
    Intent grant;
    if(Build.VERSION.SDK_INT>=33)grant=intent.getParcelableExtra(EXTRA_PROJECTION,Intent.class);
    else grant=intent.getParcelableExtra(EXTRA_PROJECTION);
    if(result!=Activity.RESULT_OK||grant==null){
      report("error","Izin rekam layar tidak diberikan.","");stopGame("Izin ditolak.");return START_NOT_STICKY;
    }
    try{
      createChannel();
      int types=ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        |ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
      if(wantFace)types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
      if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,notification("Menyiapkan layar game...",true),types);
      else startForeground(NOTIFICATION_ID,notification("Menyiapkan layar game...",true));
      report("starting","Menghubungkan GAME LIVE...","");
      holdCaptureCpu();
      initCapture(grant);
      startSocketWatchdog();
      connectSignaling(false);
    }catch(Exception error){
      Log.e(TAG,"GAME init failed",error);
      report("error","Gagal menyiapkan GAME LIVE: "+error.getClass().getSimpleName(),"");
      stopGame("Capture gagal.");
    }
    return START_NOT_STICKY;
  }

  private void holdCaptureCpu(){
    PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
    if(pm==null)return;
    cpuWakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"NADMO:GameBroadcast");
    cpuWakeLock.setReferenceCounted(false);
    cpuWakeLock.acquire(180000);
  }
  private void startSocketWatchdog(){
    if(socketWatchdog!=null)main.removeCallbacks(socketWatchdog);
    socketWatchdog=new Runnable(){
      @Override public void run(){
        if(stopping)return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(cpuWakeLock!=null&&!cpuWakeLock.isHeld())cpuWakeLock.acquire(180000);
        if(webSocketOpen&&lastServerMessageAt>0&&now-lastServerMessageAt>SOCKET_STALE_MS){
          Log.w(TAG,"No server heartbeat for "+(now-lastServerMessageAt)+"ms; retrying signaling");
          disconnectStaleSocket("Server tidak mengirim heartbeat");
        }else if(!webSocketOpen&&socketOpenedAt>0&&now-socketOpenedAt>SOCKET_CONNECT_STALE_MS){
          Log.w(TAG,"WebSocket connect timed out; retrying");
          disconnectStaleSocket("Koneksi GAME LIVE terlalu lama");
        }
        main.postDelayed(this,15000);
      }
    };
    main.postDelayed(socketWatchdog,15000);
  }
  private void disconnectStaleSocket(String note){
    diagnostic=note;
    WebSocket old=socket;socket=null;webSocketOpen=false;socketOpenedAt=0;
    if(old!=null)old.cancel();
    lostConnection();
  }
  private static String limit(String raw,int max,String fallback){
    if(raw==null)return fallback;
    raw=raw.trim();
    return raw.isEmpty()?fallback:raw.substring(0,Math.min(max,raw.length()));
  }
  private void createChannel(){
    NotificationChannel channel=new NotificationChannel(CHANNEL,"NADMO GAME LIVE",NotificationManager.IMPORTANCE_LOW);
    channel.setDescription("Siaran layar game berjalan. Ketuk STOP untuk mengakhiri.");
    NotificationManager manager=getSystemService(NotificationManager.class);
    manager.createNotificationChannel(channel);
    NotificationChannel chatChannel=new NotificationChannel(CHAT_CHANNEL,"Chat saat main game",NotificationManager.IMPORTANCE_HIGH);
    chatChannel.setDescription("Chat penonton muncul saat bermain. Jawab langsung lewat mikrofon LIVE.");
    chatChannel.enableVibration(true);
    manager.createNotificationChannel(chatChannel);
  }
  private Notification notification(String text,boolean ongoing){
    Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent go=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Intent stop=new Intent(this,GameCaptureService.class).setAction(ACTION_STOP);
    PendingIntent cancel=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
    return b.setSmallIcon(android.R.drawable.presence_video_online)
      .setContentTitle("NADMO / GAME LIVE").setContentText(text)
      .setContentIntent(go).setOngoing(ongoing).setOnlyAlertOnce(true)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel,"AKHIRI LIVE",cancel).build();
  }
  private void onIncomingRoomChat(JSONObject message){
    if(!started||stopping||roomId.isEmpty())return;
    String senderId=message.optString("from","");
    if(!hostSocketId.isEmpty()&&hostSocketId.equals(senderId))return;
    String name=limit(message.optString("name","Penonton"),35,"Penonton");
    String body=limit(message.optString("text",""),250,"");
    if(body.isEmpty())return;
    long now=android.os.SystemClock.elapsedRealtime();
    if(now-lastChatPopupAt<CHAT_POPUP_MIN_INTERVAL_MS)return;
    lastChatPopupAt=now;
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
    Intent back=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent view=PendingIntent.getActivity(this,7703,back,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder builder=new Notification.Builder(this,CHAT_CHANNEL)
      .setSmallIcon(android.R.drawable.sym_action_chat)
      .setContentTitle(name+" · GAME LIVE")
      .setContentText(body)
      .setStyle(new Notification.BigTextStyle().bigText(body))
      .setCategory(Notification.CATEGORY_MESSAGE)
      .setPriority(Notification.PRIORITY_HIGH)
      .setVisibility(Notification.VISIBILITY_PRIVATE)
      .setAutoCancel(true)
      .setContentIntent(view);
    getSystemService(NotificationManager.class).notify(CHAT_NOTIFICATION_ID,builder.build());
  }
  private void updateNotification(String value){
    getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(value,true));
  }
  private void report(String next,String message,String room){
    state=next;status=message;if(room!=null&&!room.isEmpty())activeRoom=room;
    Intent event=new Intent(ACTION_STATUS).setPackage(getPackageName());
    event.putExtra("state",next).putExtra("message",message).putExtra("room",room);
    sendBroadcast(event);
    if(started&&!stopping)updateNotification(message);
  }
  private void initCapture(Intent permissionData){
    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this)
      .setEnableInternalTracer(false).createInitializationOptions());
    egl=EglBase.create();
    factory=PeerConnectionFactory.builder()
      .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(),true,true))
      .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
      .createPeerConnectionFactory();
    screenSource=factory.createVideoSource(true);
    screenTrack=factory.createVideoTrack("nadmo_screen",screenSource);
    screenTrack.setEnabled(true);
    screenHelper=SurfaceTextureHelper.create("NadmoScreenCapture",egl.getEglBaseContext());
    screenCapturer=new ScreenCapturerAndroid(permissionData,new MediaProjection.Callback(){
      @Override public void onStop(){main.post(()->{
        if(!stopping){report("error","Android menghentikan izin perekaman layar.","");stopGame("Izin layar berakhir.");}
      });}
    });
    screenCapturer.initialize(screenHelper,this,screenSource.getCapturerObserver());
    int[] dimensions=captureDimensions();
    screenCapturer.startCapture(dimensions[0],dimensions[1],20);
    micSource=factory.createAudioSource(new MediaConstraints());
    micTrack=factory.createAudioTrack("nadmo_microphone",micSource);
    micTrack.setEnabled(true);
    if(wantFace)startFaceCamera();
  }
  private int[] captureDimensions(){
    DisplayMetrics m=getResources().getDisplayMetrics();
    int width=Math.max(1,m.widthPixels),height=Math.max(1,m.heightPixels);
    float scale=Math.min(1f,1280f/Math.max(width,height));
    int w=Math.max(2,Math.round(width*scale)/2*2);
    int h=Math.max(2,Math.round(height*scale)/2*2);
    return new int[]{w,h};
  }
  @Override public void onConfigurationChanged(Configuration configuration){
    super.onConfigurationChanged(configuration);
    if(screenCapturer!=null&&!stopping){
      int[] d=captureDimensions();
      try{screenCapturer.changeCaptureFormat(d[0],d[1],20);}
      catch(Exception e){Log.w(TAG,"Screen format rotation update failed",e);}
    }
  }
  private void startFaceCamera(){
    try{
      Camera2Enumerator cameras=new Camera2Enumerator(this);
      for(String device:cameras.getDeviceNames()){
        if(!cameras.isFrontFacing(device))continue;
        faceCapturer=cameras.createCapturer(device,null);
        if(faceCapturer==null)continue;
        faceSource=factory.createVideoSource(false);
        faceHelper=SurfaceTextureHelper.create("NadmoFaceCapture",egl.getEglBaseContext());
        faceCapturer.initialize(faceHelper,this,faceSource.getCapturerObserver());
        faceTrack=factory.createVideoTrack("nadmo_face",faceSource);
        faceCapturer.startCapture(320,240,12);
        break;
      }
    }catch(Exception error){
      Log.w(TAG,"Optional front camera unavailable",error);
      faceTrack=null;wantFace=false;
    }
  }
  private void connectSignaling(boolean resume){
    if(stopping)return;
    try{
      if(client==null)client=new OkHttpClient.Builder().pingInterval(20,TimeUnit.SECONDS)
        .connectTimeout(12,TimeUnit.SECONDS).build();
      Request.Builder request=new Request.Builder().url(WS_URL).header("Origin",WS_ORIGIN);
      if(!cookie.isEmpty())request.header("Cookie",cookie);
      webSocketOpen=false;
      socketOpenedAt=android.os.SystemClock.elapsedRealtime();
      socket=client.newWebSocket(request.build(),new WebSocketListener(){
        @Override public void onOpen(WebSocket ws,Response response){
          main.post(()->{
            if(stopping||ws!=socket)return;
            webSocketOpen=true;
            socketOpenedAt=0;
            lastServerMessageAt=android.os.SystemClock.elapsedRealtime();
            diagnostic="WebSocket terhubung";
            if(resume&&roomId.length()>0&&resumeToken.length()>0)
              send(new JSONObjectSafe().put("type","resume").put("id",roomId).put("token",resumeToken).json());
            else send(new JSONObjectSafe().put("type","create").put("title",title)
              .put("category","Gaming").put("mode","public").put("hostName","Host").put("facecam",faceLayout).json());
          });
        }
        @Override public void onMessage(WebSocket ws,String body){main.post(()->{
          if(!stopping&&ws==socket){lastServerMessageAt=android.os.SystemClock.elapsedRealtime();handleMessage(body);}
        });}
        @Override public void onFailure(WebSocket ws,Throwable failure,Response response){
          final int httpCode=response==null?0:response.code();
          final String error=failure==null?"Unknown":failure.getClass().getSimpleName();
          Log.w(TAG,"Game signaling failure HTTP "+httpCode+" ("+error+")");
          diagnostic="WebSocket HTTP "+httpCode+" / "+error;
          main.post(()->{
            if(stopping||ws!=socket)return;
            if(httpCode==403||httpCode==401){
              report("error","Server menolak koneksi GAME (HTTP "+httpCode+").",roomId);
              stopGame("Server menolak sesi GAME LIVE.");
            }else lostConnection();
          });
        }
        @Override public void onClosed(WebSocket ws,int code,String reason){
          Log.w(TAG,"Game signaling closed (code "+code+")");
          diagnostic="WebSocket ditutup kode "+code;
          main.post(()->{if(!stopping&&ws==socket)lostConnection();});
        }
      });
    }catch(Exception e){Log.e(TAG,"Connection failed",e);lostConnection();}
  }
  private void send(JSONObject obj){if(socket!=null&&webSocketOpen)socket.send(obj.toString());}
  private void handleMessage(String raw){
    try{
      JSONObject m=new JSONObject(raw);String type=m.optString("type","");
      if("created".equals(type)||"resumed".equals(type)){
        roomId=m.optString("id",roomId);
        hostSocketId=m.optString("selfId",hostSocketId);
        if("created".equals(type))resumeToken=m.optString("resumeToken","");
        reconnectAttempt=0;offlineSince=0;
        String message="GAME LIVE aktif · "+game+" · mic"+(faceTrack!=null?" · facecam":"");
        report("live",message,roomId);
        if("resumed".equals(type)){
          clearViewers();
          JSONArray ids=m.optJSONArray("viewers");
          if(ids!=null)for(int i=0;i<ids.length();i++)offerTo(ids.optString(i));
        }
      }else if("chat".equals(type)){
        onIncomingRoomChat(m);
      }else if("viewer-joined".equals(type)){
        offerTo(m.optString("id"));
      }else if("viewer-left".equals(type)){
        String id=m.optString("id");
        PeerConnection pc=viewers.remove(id);
        pendingIce.remove(id);
        if(pc!=null){pc.close();pc.dispose();}
      }else if("media-refresh-request".equals(type)){
        offerTo(m.optString("id"));
      }else if("signal".equals(type)){
        String id=m.optString("from");JSONObject data=m.optJSONObject("data");
        if(data!=null)handleSignal(id,data);
      }else if("error".equals(type)){
        String message=m.optString("message","Room ditolak server");
        report("error",message,roomId);
        if(roomId.isEmpty()||message.contains("Pemulihan")||message.contains("terverifikasi"))stopGame(message);
      }else if("room-ended".equals(type)){
        stopGame("Room telah berakhir.");
      }
    }catch(Exception e){Log.w(TAG,"Invalid signaling event",e);}
  }
  private void lostConnection(){
    if(stopping)return;
    webSocketOpen=false;socketOpenedAt=0;
    if(offlineSince==0)offlineSince=System.currentTimeMillis();
    if(System.currentTimeMillis()-offlineSince>85000||roomId.isEmpty()&&reconnectAttempt>=4){
      report("error","Jaringan tidak pulih. GAME LIVE dihentikan.",roomId);
      stopGame("Jaringan berakhir.");
      return;
    }
    report("reconnecting","Koneksi GAME terputus; pemulihan otomatis... ("+diagnostic+")",roomId);
    if(retryTask!=null)main.removeCallbacks(retryTask);
    final int delay=Math.min(1200*(1<<Math.min(reconnectAttempt++,3)),9000);
    retryTask=()->connectSignaling(!roomId.isEmpty());
    main.postDelayed(retryTask,delay);
  }
  private void offerTo(String id){
    if(id==null||id.isEmpty()||stopping||factory==null)return;
    PeerConnection old=viewers.remove(id);
    if(old!=null){old.close();old.dispose();}
    pendingIce.remove(id);
    List<PeerConnection.IceServer> ice=Arrays.asList(
      PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
      PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
    PeerConnection.RTCConfiguration cfg=new PeerConnection.RTCConfiguration(ice);
    cfg.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;
    final PeerConnection[] holder=new PeerConnection[1];
    PeerConnection pc=factory.createPeerConnection(cfg,new PeerConnection.Observer(){
      @Override public void onSignalingChange(PeerConnection.SignalingState state){}
      @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state){}
      @Override public void onIceConnectionReceivingChange(boolean receiving){}
      @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state){}
      @Override public void onIceCandidate(IceCandidate candidate){main.post(()->{
        if(viewers.get(id)!=holder[0]||stopping)return;
        JSONObject c=new JSONObjectSafe().put("candidate",candidate.sdp)
          .put("sdpMid",candidate.sdpMid).put("sdpMLineIndex",candidate.sdpMLineIndex).json();
        signal(id,new JSONObjectSafe().put("candidate",c).json());
      });}
      @Override public void onIceCandidatesRemoved(IceCandidate[] candidates){}
      @Override public void onAddStream(MediaStream stream){}
      @Override public void onRemoveStream(MediaStream stream){}
      @Override public void onDataChannel(DataChannel channel){}
      @Override public void onRenegotiationNeeded(){}
      @Override public void onAddTrack(RtpReceiver receiver,MediaStream[] streams){}
    });
    if(pc==null){report("error","Tidak bisa menyambungkan penonton WebRTC.",roomId);return;}
    holder[0]=pc;
    viewers.put(id,pc);
    pc.addTrack(screenTrack,Arrays.asList("NADMO_GAME_SCREEN"));
    pc.addTrack(micTrack,Arrays.asList("NADMO_GAME_SCREEN"));
    if(faceTrack!=null)pc.addTrack(faceTrack,Arrays.asList("NADMO_GAME_FACE"));
    pc.createOffer(new SdpAdapter(){
      @Override public void onCreateSuccess(SessionDescription description){
        pc.setLocalDescription(new SdpAdapter(){
          @Override public void onSetSuccess(){main.post(()->{
            if(viewers.get(id)!=pc||stopping)return;
            JSONObject desc=new JSONObjectSafe().put("type","offer").put("sdp",description.description).json();
            signal(id,new JSONObjectSafe().put("description",desc).put("reset",true).json());
          });}
        },description);
      }
    },new MediaConstraints());
  }
  private void signal(String id,JSONObject data){
    send(new JSONObjectSafe().put("type","signal").put("to",id).put("data",data).json());
  }
  private void handleSignal(String id,JSONObject data){
    PeerConnection pc=viewers.get(id);if(pc==null)return;
    JSONObject description=data.optJSONObject("description");
    if(description!=null&&"answer".equals(description.optString("type"))){
      SessionDescription answer=new SessionDescription(SessionDescription.Type.ANSWER,description.optString("sdp"));
      pc.setRemoteDescription(new SdpAdapter(){
        @Override public void onSetSuccess(){
          main.post(()->{
            List<IceCandidate> candidates=pendingIce.remove(id);
            if(candidates!=null&&viewers.get(id)==pc)for(IceCandidate c:candidates)pc.addIceCandidate(c);
          });
        }
      },answer);
    }
    JSONObject ice=data.optJSONObject("candidate");
    if(ice!=null){
      IceCandidate candidate=new IceCandidate(ice.optString("sdpMid"),
        ice.optInt("sdpMLineIndex"),ice.optString("candidate"));
      if(pc.getRemoteDescription()!=null)pc.addIceCandidate(candidate);
      else pendingIce.computeIfAbsent(id,k->new ArrayList<>()).add(candidate);
    }
  }
  private void clearViewers(){
    for(PeerConnection pc:viewers.values()){pc.close();pc.dispose();}
    viewers.clear();pendingIce.clear();
  }
  private void stopGame(String reason){
    if(stopping)return;stopping=true;
    if(retryTask!=null)main.removeCallbacks(retryTask);
    if(socketWatchdog!=null)main.removeCallbacks(socketWatchdog);
    if(cpuWakeLock!=null&&cpuWakeLock.isHeld())cpuWakeLock.release();
    try{send(new JSONObjectSafe().put("type","leave").json());}catch(Exception ignored){}
    if(socket!=null){socket.close(1000,"game stream ended");socket=null;}
    webSocketOpen=false;clearViewers();
    try{if(faceCapturer!=null){faceCapturer.stopCapture();faceCapturer.dispose();}}catch(Exception e){Log.w(TAG,"Stop face camera",e);}
    try{if(screenCapturer!=null){screenCapturer.stopCapture();screenCapturer.dispose();}}catch(Exception e){Log.w(TAG,"Stop projection",e);}
    if(screenTrack!=null)screenTrack.dispose();
    if(faceTrack!=null)faceTrack.dispose();
    if(micTrack!=null)micTrack.dispose();
    if(micSource!=null)micSource.dispose();
    if(screenSource!=null)screenSource.dispose();
    if(faceSource!=null)faceSource.dispose();
    if(screenHelper!=null)screenHelper.dispose();
    if(faceHelper!=null)faceHelper.dispose();
    if(factory!=null)factory.dispose();
    if(egl!=null)egl.release();
    if(client!=null){client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();}
    cookie="";roomId="";resumeToken="";started=false;activeRoom="";faceLayout=null;
    hostSocketId="";
    try{getSystemService(NotificationManager.class).cancel(CHAT_NOTIFICATION_ID);}catch(Exception ignored){}
    state="stopped";status=reason;
    Intent event=new Intent(ACTION_STATUS).setPackage(getPackageName());
    event.putExtra("state","stopped").putExtra("message",reason).putExtra("room","");
    sendBroadcast(event);
    stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
  }
  @Override public void onDestroy(){
    if(!stopping)stopGame("GAME LIVE dihentikan Android.");
    super.onDestroy();
  }
  private static class JSONObjectSafe {
    private final JSONObject obj=new JSONObject();
    JSONObjectSafe put(String key,Object value){
      try{obj.put(key,value);}catch(Exception ignored){}
      return this;
    }
    JSONObject json(){return obj;}
  }
  private static class SdpAdapter implements SdpObserver {
    @Override public void onCreateSuccess(SessionDescription sdp){}
    @Override public void onSetSuccess(){}
    @Override public void onCreateFailure(String reason){Log.w(TAG,"SDP create "+reason);}
    @Override public void onSetFailure(String reason){Log.w(TAG,"SDP set "+reason);}
  }
}
