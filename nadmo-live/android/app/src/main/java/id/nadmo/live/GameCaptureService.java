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
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.util.DisplayMetrics;
import android.util.Log;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.JavaI420Buffer;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpParameters;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoFrame;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.ByteBuffer;

/**
 * Foreground screen broadcaster. GAME runs in another Android app while capture,
 * WebRTC microphone, optional front camera and NADMO signaling stay in this process.
 *
 * Captures game playback audio only if Android 10+ and the game itself allow it.
 * Uses a separate low-latency WebRTC data channel for PCM and keeps the microphone
 * on its original audio track. Do not report successful game audio without samples.
 */
public final class GameCaptureService extends Service {
  static final String ACTION_START="id.nadmo.live.GAME_START";
  static final String ACTION_STOP="id.nadmo.live.GAME_STOP";
  static final String ACTION_PAUSE="id.nadmo.live.GAME_PAUSE";
  static final String ACTION_RESUME="id.nadmo.live.GAME_RESUME";
  static final String ACTION_SHOW_FLOAT="id.nadmo.live.GAME_SHOW_FLOAT";
  static final String ACTION_HIDE_FLOAT="id.nadmo.live.GAME_HIDE_FLOAT";
  static final String ACTION_STATUS="id.nadmo.live.GAME_STATUS";
  static final String ACTION_QUERY="id.nadmo.live.GAME_QUERY";
  // Failure codes are strictly device-local: never store session cookies,
  // stream URLs, chat content or WebRTC offer/candidate details.
  private static final String DIAG_PREFS="nadmo_game_status";
  private static final String DIAG_ARMED="capture_active";
  private static final String DIAG_LAST="last_diagnostic";
  private static final String DIAG_TIME="last_diagnostic_at";
  private static final int DIAG_MAX=150;
  static final String EXTRA_PROJECTION="projection";
  static final String EXTRA_RESULT="result";
  static final String EXTRA_TITLE="title";
  static final String EXTRA_GAME="game";
   static final String EXTRA_BROADCAST_KIND="broadcastKind";
  static final String EXTRA_QUALITY="quality";
  static final String EXTRA_FACE="face";
  static final String EXTRA_COOKIE="cookie";
  static final String EXTRA_FACE_LAYOUT="facecam";
  private static final String WS_URL="wss://live.nadmo.id/ws";
  // Server checks Origin for CSWSH defense. OkHttp (unlike browser WebSocket) does not set it.
  private static final String WS_ORIGIN="https://live.nadmo.id";
  private static final int NOTIFICATION_ID=7701;
  static final int CHAT_NOTIFICATION_ID=7702;
  private static final String CHAT_CHANNEL="nadmo_game_chat";
  private static final long CHAT_POPUP_MIN_INTERVAL_MS=1200;
  private static final int MAX_CHAT_NOTIFICATIONS=4;
  private long lastChatPopupAt=0L;
  private int nextChatNotificationId=CHAT_NOTIFICATION_ID;
  private final List<Integer> shownChatNotifications=new ArrayList<>();
  // Optional, user-granted display overlay. Notifications cannot guarantee a heads-up
  // banner over full-screen games; this short, non-touchable banner can.
  private WindowManager chatOverlayWindow=null;
  private TextView chatOverlayView=null;
  private Runnable chatOverlayHide=null;
  private JSONObject queuedChat=null;
  private Runnable queuedChatTask=null;
  // SYSTEM_ALERT_WINDOW is user-approved. This compact secure control stays
  // outside the game, can be dragged, and never captures game touches elsewhere.
  private WindowManager gameFloatingWindow;
  private LinearLayout gameFloatingRoot,gameFloatingMenu;
  private WindowManager.LayoutParams gameFloatingParams;
  private TextView gameFloatingBubble,gameFloatingPause,gameFloatingStop;
  private long endConfirmAt=0L;
  // Fail closed. No Android/NADMO system screen is ever broadcast before the
  // creator intentionally resumes from INSIDE the running game.
  private volatile boolean privacyPaused=true;
  private JavaI420Buffer privacyBlackBuffer=null;
  private String hostSocketId="";
  private static final String CHANNEL="nadmo_game_live";
  private static final String REST_CHANNEL="nadmo_game_break_reminders";
  private static final int REST_NOTIFICATION_ID=7710;
  private static final long ONE_HOUR_MS=3600000L;
  private long liveCreatedAt=0L;
  private int lastRestReminderHour=0;
  private static final String TAG="NadmoGameLive";
  private final Handler main=new Handler(Looper.getMainLooper());
  private final Map<String,PeerConnection> viewers=new HashMap<>();
  private final Map<String,List<IceCandidate>> pendingIce=new HashMap<>();
  private final Map<String,RtpSender> screenSenders=new HashMap<>();
  // Repair a failed viewer route without interrupting MediaProjection or microphone.
  private final Map<String,Runnable> peerRepairTasks=new HashMap<>();
  private final Map<String,Long> lastPeerRepairAt=new HashMap<>();
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
  private GameAudioRelay gameAudio;
  private String roomId="",resumeToken="",title="",game="",cookie="";
  private JSONObject faceLayout=null;
  private boolean stopping=false,started=false,wantFace=false,webSocketOpen=false,radioMode=false;
  private long offlineSince=0L;
  private int reconnectAttempt=0;
  private Runnable retryTask;
  private Runnable socketWatchdog;
  private ConnectivityManager connectivityManager;
  private ConnectivityManager.NetworkCallback networkCallback;
  private Network activeNetwork;
  private boolean networkLost=false;
  private final AtomicBoolean firstFrameReceived=new AtomicBoolean(false);
  private volatile long lastCapturedFrameAt=0;
  private boolean signalingStarted=false;
  private Runnable firstFrameTimeout;
  private PowerManager.WakeLock cpuWakeLock;
  private long lastServerMessageAt=0;
  private long socketOpenedAt=0;
  // Stability-first video profile. Original 1280px fixed capture could saturate
  // low-memory mobile hardware encoders while a heavy game runs in foreground.
  private static final int GAME_VIDEO_MAX_EDGE=960;
  private static final int GAME_VIDEO_FPS=20;
  private static final int GAME_VIDEO_BITRATE_BPS=850000;
  // Without an SFU the phone uploads one screen encode per viewer. Keep that
  // combined screen-video budget bounded so the game retains network headroom.
  private static final int GAME_TOTAL_SCREEN_UPLOAD_BPS=2000000;
  // Default STABIL leaves headroom for a running game and separate playback audio.
  // User can explicitly select HEMAT or DETAIL before recording the full display.
  private int activeVideoMaxEdge=720,activeVideoFps=18,activeVideoBitrateBps=620000,activeTotalUploadBps=1600000;
  private static final long SOCKET_STALE_MS=65000L;
  private static final long SOCKET_CONNECT_STALE_MS=25000L;
  private String diagnostic="Belum ada koneksi";
  static volatile String state="stopped",status="",activeRoom="";

  private SharedPreferences diagnosticPrefs(){
    return getSharedPreferences(DIAG_PREFS,Context.MODE_PRIVATE);
  }
  private void rememberFailure(String reason){
    String safe=limit(reason,DIAG_MAX,"Status tidak tersedia");
    diagnosticPrefs().edit().putString(DIAG_LAST,safe)
      .putLong(DIAG_TIME,System.currentTimeMillis()).apply();
  }
  private String lastFailure(){
    SharedPreferences prefs=diagnosticPrefs();
    if(prefs.getBoolean(DIAG_ARMED,false))
      return "SERVICE_INTERRUPTED: proses siaran GAME berhenti mendadak saat aplikasi lain dibuka.";
    return prefs.getString(DIAG_LAST,"Belum ada catatan kesalahan GAME LIVE.");
  }
  private void watchNetwork(){
    connectivityManager=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
    if(connectivityManager==null)return;
    networkCallback=new ConnectivityManager.NetworkCallback(){
      @Override public void onAvailable(Network network){main.post(()->{
        if(stopping||!started)return;
        boolean switched=networkLost||(activeNetwork!=null&&!activeNetwork.equals(network));
        activeNetwork=network;networkLost=false;
        if(switched){
          // Handover notifications can fire during app/game launch without
          // any actual socket failure. Reconnect only if transport fails or
          // the heartbeat watchdog confirms a stalled connection.
          diagnostic="NETWORK_SWITCH";
          Log.i(TAG,"Default network changed; awaiting actual socket failure or heartbeat timeout");
        }
      });}
      @Override public void onLost(Network network){main.post(()->{
        if(!stopping&&started&&network.equals(activeNetwork)){
          activeNetwork=null;networkLost=true;
          diagnostic="NETWORK_LOST";
          rememberFailure("NETWORK_LOST: jaringan HP terputus saat GAME berjalan");
        }
      });}
    };
    try{connectivityManager.registerDefaultNetworkCallback(networkCallback);}
    catch(Exception error){
      Log.w(TAG,"Cannot monitor default mobile network",error);
      networkCallback=null;
    }
  }
  @Override public IBinder onBind(Intent intent){return null;}

  @Override public int onStartCommand(Intent intent,int flags,int startId){
    if(intent==null)return START_NOT_STICKY;
    if(ACTION_QUERY.equals(intent.getAction())){
      Intent event=new Intent(ACTION_STATUS).setPackage(getPackageName());
      event.putExtra("state",started?state:"stopped")
        .putExtra("message",started?status:lastFailure())
        .putExtra("room",started?activeRoom:"")
        .putExtra("startedAt",started?liveCreatedAt:0L)
         .putExtra("broadcastKind",radioMode?"radio":"gaming");
      sendBroadcast(event);
      if(!started)stopSelf(startId);
      return START_NOT_STICKY;
    }
    if(ACTION_STOP.equals(intent.getAction())){stopGame("Siaran game diakhiri.");return START_NOT_STICKY;}
    if(ACTION_PAUSE.equals(intent.getAction())){
      if(started)setPrivacyPaused(true);
      else stopSelf(startId);
      return START_NOT_STICKY;
    }
    if(ACTION_RESUME.equals(intent.getAction())){
      // Only the in-game floating button may resume capture. No notification,
      // WebView IPC, or incoming intent is allowed to expose the NADMO app.
      if(!started)stopSelf(startId);
      return START_NOT_STICKY;
    }
    if(ACTION_SHOW_FLOAT.equals(intent.getAction())){
      if(started)showGameFloatingControl();
      else stopSelf(startId);
      return START_NOT_STICKY;
    }
    if(ACTION_HIDE_FLOAT.equals(intent.getAction())){
      if(started)hideGameFloatingControl();
      else stopSelf(startId);
      return START_NOT_STICKY;
    }
    if(!ACTION_START.equals(intent.getAction())||started)return START_NOT_STICKY;
    stopping=false;started=true;privacyPaused=true;
    liveCreatedAt=0L;lastRestReminderHour=0;
    diagnosticPrefs().edit().putBoolean(DIAG_ARMED,true)
      .putString(DIAG_LAST,"GAME LIVE mulai; menunggu perekaman layar.").commit();
    title=limit(intent.getStringExtra(EXTRA_TITLE),60,"NADMO GAME LIVE");
    radioMode="radio".equals(intent.getStringExtra(EXTRA_BROADCAST_KIND));
     game=radioMode?"NADMO RADIO":limit(intent.getStringExtra(EXTRA_GAME),35,"Gaming");
    String quality=intent.getStringExtra(EXTRA_QUALITY);
    if("low".equals(quality)){
      activeVideoMaxEdge=640;activeVideoFps=15;activeVideoBitrateBps=420000;activeTotalUploadBps=1100000;
    }else if("quality".equals(quality)){
      activeVideoMaxEdge=GAME_VIDEO_MAX_EDGE;activeVideoFps=GAME_VIDEO_FPS;
      activeVideoBitrateBps=GAME_VIDEO_BITRATE_BPS;activeTotalUploadBps=GAME_TOTAL_SCREEN_UPLOAD_BPS;
    }else{
      activeVideoMaxEdge=720;activeVideoFps=18;activeVideoBitrateBps=620000;activeTotalUploadBps=1600000;
    }
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
      // Opening signaling cannot depend on the Android capture callback.
      // Certain device/WebRTC versions never deliver onCapturerStarted to this observer,
      // leaving GAME/RADIO forever in MENYAMBUNG even though MediaProjection is valid.
      // Frame safety is independently enforced by the first-frame timeout below.
      connectWhenCaptureStarted();
      // Overlay is shown by MainActivity.onPause AFTER leaving NADMO for game.
      // Never offer RESUME over the broadcaster's own account/settings screen.
      watchNetwork();
      startSocketWatchdog();
      // Delayed first frame is not evidence that screen sharing was revoked:
      // opening a heavy game can stall Android's video pipeline temporarily.
      // Keep the acknowledged room alive so WebRTC may recover once frames
      // arrive. A genuinely revoked capture still ends via onStop callback.
      firstFrameTimeout=()->{
        if(!firstFrameReceived.get()&&!stopping){
          Log.w(TAG,"First video frame delayed; retaining room and private media track");
          rememberFailure("PROJECTION_FRAME_DELAY");
          if(!roomId.isEmpty())updateNotification("LIVE terhubung — menunggu gambar game");
        }
      };
      main.postDelayed(firstFrameTimeout,15000);
    }catch(Exception error){
      Log.e(TAG,"GAME init failed",error);
      String code="CAPTURE_INIT_"+error.getClass().getSimpleName();
      rememberFailure(code);
      report("error","Gagal menyiapkan GAME LIVE: "+code,"");
      stopGame(code);
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
        updateGameFloatingControl();
        checkHourlyRestReminder();
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
  private static String formatDuration(long elapsedMs){
    long sec=Math.max(0L,elapsedMs/1000L);
    return String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec%3600)/60,sec%60);
  }
  // Reminder does not pause, disconnect, or alter the stream. Running in the
  // foreground :game service means it still fires with a different game visible.
  private void checkHourlyRestReminder(){
    if(!started||stopping||roomId.isEmpty()||liveCreatedAt<=0L)return;
    long elapsed=Math.max(0L,System.currentTimeMillis()-liveCreatedAt);
    int hour=(int)Math.min(100000L,elapsed/ONE_HOUR_MS);
    if(hour<=lastRestReminderHour||hour<1)return;
    lastRestReminderHour=hour;
    String message="Sudah LIVE "+hour+" jam. Istirahat sebentar bila perlu. Siaran tetap berjalan.";
    try{
      if(Settings.canDrawOverlays(this))showGameChatOverlay("NADMO / ISTIRAHAT",message);
      if(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null&&nm.areNotificationsEnabled()){
          Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
          PendingIntent visit=PendingIntent.getActivity(this,7705,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
          Notification.Builder alert=new Notification.Builder(this,REST_CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("NADMO LIVE — PENGINGAT ISTIRAHAT")
            .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
            .setPriority(Notification.PRIORITY_HIGH).setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(visit);
          nm.notify(REST_NOTIFICATION_ID,alert.build());
        }
      }
    }catch(Exception e){Log.w(TAG,"Cannot show LIVE hourly rest reminder",e);}
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
    NotificationChannel rest=new NotificationChannel(REST_CHANNEL,"Pengingat istirahat LIVE",NotificationManager.IMPORTANCE_HIGH);
    rest.setDescription("Pengingat tiap satu jam siaran, tanpa menghentikan siaran.");
    rest.enableVibration(true);
    manager.createNotificationChannel(rest);
  }
  private Notification notification(String text,boolean ongoing){
    Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent go=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Intent stop=new Intent(this,GameCaptureService.class).setAction(ACTION_STOP);
    PendingIntent cancel=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Intent pauseIntent=new Intent(this,GameCaptureService.class).setAction(ACTION_PAUSE);
    PendingIntent pauseSafe=PendingIntent.getService(this,2,pauseIntent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
    return b.setSmallIcon(android.R.drawable.presence_video_online)
      .setContentTitle("NADMO / GAME LIVE").setContentText(text)
      .setContentIntent(go).setOngoing(ongoing).setOnlyAlertOnce(true)
      .addAction(android.R.drawable.ic_media_pause,"JEDA AMAN",pauseSafe)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel,"AKHIRI LIVE",cancel).build();
  }

  private static void fill(ByteBuffer buffer,byte value){
    for(int i=0;i<buffer.capacity();i++)buffer.put(i,value);
  }
  // Reuse an immutable BLACK YUV source for the current encoder dimensions.
  // We do NOT stop/restart MediaProjection (Android 14 grants are one-shot).
  // Even if the source captures Settings, passwords, notifications or NADMO,
  // its frames are replaced BEFORE they reach any viewer encoder.
  private synchronized VideoFrame blackPrivacyFrame(VideoFrame captured){
    int w=captured.getBuffer().getWidth(),h=captured.getBuffer().getHeight();
    if(privacyBlackBuffer==null||privacyBlackBuffer.getWidth()!=w||privacyBlackBuffer.getHeight()!=h){
      if(privacyBlackBuffer!=null)privacyBlackBuffer.release();
      privacyBlackBuffer=JavaI420Buffer.allocate(w,h);
      fill(privacyBlackBuffer.getDataY(),(byte)16);
      fill(privacyBlackBuffer.getDataU(),(byte)128);
      fill(privacyBlackBuffer.getDataV(),(byte)128);
    }
    return new VideoFrame(privacyBlackBuffer,captured.getRotation(),captured.getTimestampNs());
  }

  private void setPrivacyPaused(boolean paused){
    if(!started||stopping)return;
    privacyPaused=paused; // FIRST, before launching another Activity or touching UI.
    if(micTrack!=null)micTrack.setEnabled(!paused);
    if(faceTrack!=null)faceTrack.setEnabled(!paused);
    if(gameAudio!=null)gameAudio.setPaused(paused);
    // Pause can be requested while Android is still granting the screen or
    // the signaling socket is reconnecting. Never claim a LIVE room exists
    // before server acknowledgement, or overwrite the connection error.
    if(roomId.isEmpty())report("starting","Menyiapkan room GAME. Tunggu hingga server tersambung.",roomId);
    else if(!webSocketOpen)report("reconnecting","Room GAME terputus, mencoba sambung lagi. Video tetap terlindungi.",roomId);
    else report(paused?"paused":"live",
      paused?"SIARAN DIJEDA — penonton melihat layar hitam. Kembali ke game untuk RESUME.":
             "GAME LIVE berjalan — layar dan audio sedang disiarkan.",roomId);
    updateGameFloatingControl();
  }
  private TextView floatingButton(String title){
    TextView v=new TextView(this);
    v.setText(title);v.setTextColor(Color.WHITE);v.setTextSize(12);
    v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    v.setGravity(Gravity.CENTER);v.setMinHeight(dp(43));
    v.setPadding(dp(11),dp(7),dp(11),dp(7));
    GradientDrawable bg=new GradientDrawable();
    bg.setColor(0xF5111719);bg.setCornerRadius(dp(10));
    bg.setStroke(dp(1),0xFFB3D962);v.setBackground(bg);
    return v;
  }
  private void updateGameFloatingControl(){
    if(gameFloatingBubble!=null)gameFloatingBubble.setText(
      (roomId.isEmpty()?"N/ LINK ":privacyPaused?"N/ JEDA ":"N/ LIVE ")+formatDuration(liveCreatedAt>0?System.currentTimeMillis()-liveCreatedAt:0L));
    if(gameFloatingPause!=null)gameFloatingPause.setText(privacyPaused?(radioMode?"▶ RESUME RADIO":"▶ RESUME GAME"):"Ⅱ JEDA SIARAN");
  }
  private void hideGameFloatingControl(){
    if(gameFloatingWindow!=null&&gameFloatingRoot!=null)
      try{gameFloatingWindow.removeViewImmediate(gameFloatingRoot);}catch(Exception ignored){}
    gameFloatingWindow=null;gameFloatingRoot=null;gameFloatingMenu=null;
    gameFloatingParams=null;gameFloatingPause=null;gameFloatingBubble=null;
    gameFloatingStop=null;endConfirmAt=0L;
  }
  private void showGameFloatingControl(){
    if(stopping||!started||!Settings.canDrawOverlays(this)||gameFloatingRoot!=null)return;
    try{
      WindowManager wm=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
      if(wm==null)return;
      LinearLayout column=new LinearLayout(this);
      column.setOrientation(LinearLayout.VERTICAL);
      column.setGravity(Gravity.END);
      column.setPadding(dp(3),dp(3),dp(3),dp(3));
      TextView bubble=floatingButton("N/ JEDA");
      bubble.setTextColor(0xFFE0FF76);
      LinearLayout menu=new LinearLayout(this);menu.setOrientation(LinearLayout.VERTICAL);
      menu.setVisibility(View.GONE);
      TextView pause=floatingButton(radioMode?"▶ RESUME RADIO":"▶ RESUME GAME");
      TextView back=floatingButton("⌂ BUKA NADMO");
      TextView end=floatingButton("× AKHIRI LIVE");
      LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(dp(156),dp(43));
      item.topMargin=dp(4);
      menu.addView(pause,new LinearLayout.LayoutParams(item));
      menu.addView(back,new LinearLayout.LayoutParams(item));
      menu.addView(end,new LinearLayout.LayoutParams(item));
      column.addView(bubble,new LinearLayout.LayoutParams(dp(151),dp(46)));
      column.addView(menu);
      WindowManager.LayoutParams params=new WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
        WindowManager.LayoutParams.FLAG_SECURE |
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT);
      params.gravity=Gravity.TOP|Gravity.RIGHT;
      params.y=dp(165);params.x=dp(9);
      wm.addView(column,params);
      gameFloatingWindow=wm;gameFloatingRoot=column;
      gameFloatingParams=params;gameFloatingBubble=bubble;
      gameFloatingMenu=menu;gameFloatingPause=pause;gameFloatingStop=end;
      updateGameFloatingControl();
      bubble.setOnTouchListener(new View.OnTouchListener(){
        private float initialY;private int baseY;private boolean dragged;
        @Override public boolean onTouch(View v,MotionEvent ev){
          switch(ev.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
              initialY=ev.getRawY();baseY=params.y;dragged=false;return true;
            case MotionEvent.ACTION_MOVE:
              if(Math.abs(ev.getRawY()-initialY)>dp(9))dragged=true;
              if(dragged){
                int limit=Math.max(dp(45),getResources().getDisplayMetrics().heightPixels-dp(230));
                params.y=Math.min(limit,Math.max(dp(45),baseY+(int)(ev.getRawY()-initialY)));
                try{wm.updateViewLayout(column,params);}catch(Exception ignored){}
              }
              return true;
            case MotionEvent.ACTION_UP:
              if(!dragged){
                menu.setVisibility(menu.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
                endConfirmAt=0L;end.setText("× AKHIRI LIVE");
                try{wm.updateViewLayout(column,params);}catch(Exception ignored){}
              }return true;
            default:return false;
          }
        }
      });
      pause.setOnClickListener(v->{
        if(privacyPaused){
          if(roomId.isEmpty()||!webSocketOpen){
            android.widget.Toast.makeText(this,"GAME belum terhubung ke server. Tunggu sampai LINK pulih.",android.widget.Toast.LENGTH_LONG).show();
            return;
          }
          setPrivacyPaused(false);
        }else setPrivacyPaused(true);
        menu.setVisibility(privacyPaused?View.VISIBLE:View.GONE); // one-tap RESUME is visible when entering the game
      });
      back.setOnClickListener(v->{
        setPrivacyPaused(true); // shield BEFORE starting NADMO Activity
        hideGameFloatingControl();
        Intent open=new Intent(this,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try{startActivity(open);}catch(Exception ex){
          Log.w(TAG,"Could not return from game; capture remains paused",ex);
          showGameFloatingControl();
        }
      });
      end.setOnClickListener(v->{
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-endConfirmAt>6000L){
          endConfirmAt=now;end.setText("TEKAN LAGI AKHIRI");
        }else stopGame("Siaran game diakhiri.");
      });
    }catch(Exception ex){
      Log.w(TAG,"Floating control unavailable; notification PAUSE remains usable",ex);
      hideGameFloatingControl();
    }
  }

  private void hideGameChatOverlay(){
    if(chatOverlayHide!=null){main.removeCallbacks(chatOverlayHide);chatOverlayHide=null;}
    if(chatOverlayWindow!=null&&chatOverlayView!=null){
      try{chatOverlayWindow.removeViewImmediate(chatOverlayView);}catch(Exception ignored){}
    }
    chatOverlayView=null;chatOverlayWindow=null;
  }
  private void showGameChatOverlay(String name,String message){
    if(!Settings.canDrawOverlays(this))return;
    main.post(()->{
      if(stopping||!started||!Settings.canDrawOverlays(this))return;
      try{
        hideGameChatOverlay();
        WindowManager window=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
        if(window==null)return;
        TextView text=new TextView(this);
        text.setText(name+" · "+message);
        text.setTextSize(14);
        text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        text.setTextColor(Color.WHITE);
        text.setMaxLines(3);
        text.setPadding(dp(16),dp(12),dp(16),dp(12));
        GradientDrawable background=new GradientDrawable();
        background.setColor(0xF2121714);
        background.setCornerRadius(dp(9));
        background.setStroke(dp(1),0xFFE3FD72);
        text.setBackground(background);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(
          -2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
          WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        params.y=dp(57);
        params.width=Math.max(dp(230),getResources().getDisplayMetrics().widthPixels-dp(50));
        params.windowAnimations=0;
        window.addView(text,params);
        chatOverlayWindow=window;chatOverlayView=text;
        chatOverlayHide=this::hideGameChatOverlay;
        main.postDelayed(chatOverlayHide,6000);
      }catch(Exception error){
        hideGameChatOverlay();
        Log.w(TAG,"In-game chat overlay unavailable on this device",error);
      }
    });
  }
  private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
  private void onIncomingRoomChat(JSONObject message){
    if(!started||stopping||roomId.isEmpty())return;
    String senderId=message.optString("from","");
    if(!hostSocketId.isEmpty()&&hostSocketId.equals(senderId))return;
    String name=limit(message.optString("name","Penonton"),35,"Penonton");
    String body=limit(message.optString("text",""),250,"");
    if(body.isEmpty())return;
    showGameChatOverlay(name,body);
    long now=android.os.SystemClock.elapsedRealtime();
    if(now-lastChatPopupAt<CHAT_POPUP_MIN_INTERVAL_MS){
      // During chat bursts retain the latest message rather than discard it.
      queuedChat=message;
      if(queuedChatTask==null){
        queuedChatTask=()->{
          queuedChatTask=null;
          JSONObject pending=queuedChat;queuedChat=null;
          if(pending!=null&&!stopping)onIncomingRoomChat(pending);
        };
        main.postDelayed(queuedChatTask,CHAT_POPUP_MIN_INTERVAL_MS-(now-lastChatPopupAt));
      }
      return;
    }
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
    NotificationManager manager=getSystemService(NotificationManager.class);
    if(manager==null||!manager.areNotificationsEnabled())return;
    NotificationChannel channel=manager.getNotificationChannel(CHAT_CHANNEL);
    if(channel==null||channel.getImportance()<NotificationManager.IMPORTANCE_HIGH)return;
    lastChatPopupAt=now;
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
    // Updating one ID repeatedly usually suppresses subsequent heads-up alerts.
    // Keep separate recent notifications, capped to prevent UI clutter.
    int notificationId=++nextChatNotificationId;
    manager.notify(notificationId,builder.build());
    shownChatNotifications.add(notificationId);
    if(shownChatNotifications.size()>MAX_CHAT_NOTIFICATIONS)
      manager.cancel(shownChatNotifications.remove(0));
  }
  private void updateNotification(String value){
    getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(value,true));
  }
  private void report(String next,String message,String room){
    state=next;status=message;if(room!=null&&!room.isEmpty())activeRoom=room;
    Intent event=new Intent(ACTION_STATUS).setPackage(getPackageName());
    event.putExtra("state",next).putExtra("message",message).putExtra("room",room).putExtra("startedAt",liveCreatedAt)
       .putExtra("broadcastKind",radioMode?"radio":"gaming");
    sendBroadcast(event);
    if(started&&!stopping)updateNotification(message);
  }
  // Start signaling once Android confirms capture initialization. Wait for real
  // frames separately with the 15s fail-closed timeout. Android WebRTC may defer
  // VideoTrack sinks until a PeerConnection exists: waiting on that sink before
  // opening the signaling socket can deadlock GAME and RADIO at STARTING.
  private void connectWhenCaptureStarted(){
    main.post(()->{
      if(stopping||signalingStarted)return;
      signalingStarted=true;
      report("starting",radioMode?"RADIO menyambung...":"GAME menyambung...","");
      connectSignaling(false);
    });
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
    screenTrack.addSink(frame->{
      lastCapturedFrameAt=android.os.SystemClock.elapsedRealtime();
      if(firstFrameReceived.compareAndSet(false,true))main.post(()->{
        if(firstFrameTimeout!=null)main.removeCallbacks(firstFrameTimeout);
      });
    });
    screenHelper=SurfaceTextureHelper.create("NadmoScreenCapture",egl.getEglBaseContext());
    screenCapturer=new ScreenCapturerAndroid(permissionData,new MediaProjection.Callback(){
      @Override public void onStop(){main.post(()->{
        if(!stopping){
          Log.w(TAG,"MediaProjection ended by Android");
          rememberFailure("PROJECTION_STOP: izin layar dicabut Android atau penghentian rekaman");
          stopGame("PROJECTION_STOP: Android mengakhiri rekam layar.");
        }
      });}
    });
    final CapturerObserver transport=screenSource.getCapturerObserver();
    screenCapturer.initialize(screenHelper,this,new CapturerObserver(){
      @Override public void onCapturerStarted(boolean success){
        transport.onCapturerStarted(success);
        if(success)connectWhenCaptureStarted();
        else main.post(()->{if(!stopping)stopGame("CAPTURE_START_FAILED");});
       }
      @Override public void onCapturerStopped(){transport.onCapturerStopped();}
      @Override public void onFrameCaptured(VideoFrame captured){
        if(!privacyPaused&&!radioMode){transport.onFrameCaptured(captured);return;}
         // RADIO ALWAYS outputs an opaque synthetic frame. Never leak music app
         // pixels, notifications, messages, credentials or any phone screen.
         // MediaProjection is only retained as Android audio-playback-capture
         // authorization; protected apps can refuse audio capture.
        VideoFrame safe=blackPrivacyFrame(captured);
        try{transport.onFrameCaptured(safe);}
        finally{safe.release();}
      }
    });
    int[] dimensions=captureDimensions();
    screenCapturer.startCapture(dimensions[0],dimensions[1],activeVideoFps);
    // Android 14 forbids reusing the projection intent for a second session.
    // Obtain the projection already held by the current screen capturer.
    if(Build.VERSION.SDK_INT>=29){
      try{
        // WebRTC publishes the existing MediaProjection. Reuse the same one:
        // calling getMediaProjection(resultData) again is forbidden on Android 14.
        MediaProjection projection=screenCapturer.getMediaProjection();
        if(projection!=null){
          gameAudio=new GameAudioRelay();
          if(!gameAudio.start(projection))gameAudio=null;
          else gameAudio.setPaused(privacyPaused);
        }
      }catch(Exception unavailable){
        Log.w(TAG,"Internal GAME audio not capturable; microphone remains active",unavailable);
        gameAudio=null;
      }
    }
    micSource=factory.createAudioSource(new MediaConstraints());
    micTrack=factory.createAudioTrack("nadmo_microphone",micSource);
    micTrack.setEnabled(!privacyPaused);
    if(wantFace)startFaceCamera();
  }
  private int[] captureDimensions(){
    DisplayMetrics m=getResources().getDisplayMetrics();
    int width=Math.max(1,m.widthPixels),height=Math.max(1,m.heightPixels);
    float scale=Math.min(1f,(float)activeVideoMaxEdge/Math.max(width,height));
    int w=Math.max(2,Math.round(width*scale)/2*2);
    int h=Math.max(2,Math.round(height*scale)/2*2);
    return new int[]{w,h};
  }
  @Override public void onConfigurationChanged(Configuration configuration){
    super.onConfigurationChanged(configuration);
    if(screenCapturer!=null&&!stopping){
      int[] d=captureDimensions();
      try{screenCapturer.changeCaptureFormat(d[0],d[1],activeVideoFps);}
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
        faceTrack.setEnabled(!privacyPaused);
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
              .put("category",radioMode?"DJ & Music":"Gaming").put("broadcastKind",radioMode?"radio":"gaming").put("mode","public").put("hostName","Host").put("facecam",faceLayout).json());
            // Do not leave beta users stuck on MENYAMBUNG forever when
            // WebSocket is open but the server never acknowledges CREATE.
            main.postDelayed(()->{
              if(started&&!stopping&&ws==socket&&roomId.isEmpty()&&webSocketOpen){
                rememberFailure("ROOM_ACK_TIMEOUT");
                report("error","Room tidak tersambung. Coba lagi.","");
                stopGame("ROOM_ACK_TIMEOUT");
              }
            },12000);
          });
        }
        @Override public void onMessage(WebSocket ws,String body){main.post(()->{
          if(!stopping&&ws==socket){lastServerMessageAt=android.os.SystemClock.elapsedRealtime();handleMessage(body);}
        });}
        @Override public void onFailure(WebSocket ws,Throwable failure,Response response){
          final int httpCode=response==null?0:response.code();
          final String error=failure==null?"Unknown":failure.getClass().getSimpleName();
          Log.w(TAG,"Game signaling failure HTTP "+httpCode+" ("+error+")");
          diagnostic="WS_HTTP_"+httpCode+"_"+error;
          rememberFailure(diagnostic);
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
          diagnostic="WS_CLOSE_"+code;
          rememberFailure(diagnostic);
          main.post(()->{if(!stopping&&ws==socket)lostConnection();});
        }
      });
    }catch(Exception e){
      Log.e(TAG,"Connection failed",e);
      diagnostic="WS_CONNECT_"+e.getClass().getSimpleName();
      rememberFailure(diagnostic);
      lostConnection();
    }
  }
  private void send(JSONObject obj){if(socket!=null&&webSocketOpen)socket.send(obj.toString());}
  private void handleMessage(String raw){
    try{
      JSONObject m=new JSONObject(raw);String type=m.optString("type","");
      if("created".equals(type)||"resumed".equals(type)){
        roomId=m.optString("id",roomId);
        // The server's original creation time survives temporary disconnect.
        long serverStart=m.optLong("createdAt",0L),localNow=System.currentTimeMillis();
        liveCreatedAt=serverStart>1600000000000L&&serverStart<=localNow+60000L?serverStart:localNow;
        hostSocketId=m.optString("selfId",hostSocketId);
        if("created".equals(type))resumeToken=m.optString("resumeToken","");
        reconnectAttempt=0;offlineSince=0;
        String message=radioMode?"RADIO LIVE aktif · audio aplikasi hanya bila Android & pemutar mengizinkan · MIC":
           "GAME LIVE aktif · "+game+" · mic"+(faceTrack!=null?" · facecam":"");
        report(privacyPaused?"paused":"live",
          privacyPaused?(radioMode?"RADIO siap — OPEN MUSIC, tekan RESUME pada tombol melayang. Layar aplikasi tidak ditampilkan.":"GAME siap — buka game, lalu tekan tombol melayang RESUME."):message,roomId);
        updateGameFloatingControl();checkHourlyRestReminder();
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
        cancelPeerRepair(id);lastPeerRepairAt.remove(id);
        PeerConnection pc=viewers.remove(id);
        pendingIce.remove(id);
        screenSenders.remove(id);
        if(gameAudio!=null)gameAudio.detach(id);
        if(pc!=null){pc.close();pc.dispose();}
        balanceScreenUpload();
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
      report("error","GAME terputus: "+diagnostic,roomId);
      stopGame("SIGNAL_TIMEOUT_"+diagnostic);
      return;
    }
    report("reconnecting","Koneksi GAME terputus; pemulihan otomatis... ("+diagnostic+")",roomId);
    if(retryTask!=null)main.removeCallbacks(retryTask);
    final int delay=Math.min(1200*(1<<Math.min(reconnectAttempt++,3)),9000);
    retryTask=()->connectSignaling(!roomId.isEmpty());
    main.postDelayed(retryTask,delay);
  }
  private void cancelPeerRepair(String id){
    Runnable task=peerRepairTasks.remove(id);
    if(task!=null)main.removeCallbacks(task);
  }
  private void onViewerIceState(String id,PeerConnection pc,PeerConnection.IceConnectionState iceState){
    main.post(()->{
      if(stopping||viewers.get(id)!=pc)return;
      if(iceState==PeerConnection.IceConnectionState.CONNECTED||
         iceState==PeerConnection.IceConnectionState.COMPLETED){
        cancelPeerRepair(id);return;
      }
      if(iceState!=PeerConnection.IceConnectionState.FAILED&&
         iceState!=PeerConnection.IceConnectionState.DISCONNECTED)return;
      if(peerRepairTasks.containsKey(id))return;
      long now=android.os.SystemClock.elapsedRealtime();
      long last=lastPeerRepairAt.getOrDefault(id,0L);
      long backoff=Math.max(0L,15000L-(now-last));
      long delay=Math.max(backoff,iceState==PeerConnection.IceConnectionState.FAILED?1200L:6000L);
      Runnable repair=()->{
        peerRepairTasks.remove(id);
        // Room resume independently reconstructs the viewer peer connections.
        if(stopping||viewers.get(id)!=pc||!webSocketOpen)return;
        lastPeerRepairAt.put(id,android.os.SystemClock.elapsedRealtime());
        Log.w(TAG,"Repairing stalled viewer ICE "+id);
        offerTo(id);
      };
      peerRepairTasks.put(id,repair);
      main.postDelayed(repair,delay);
    });
  }
  private void balanceScreenUpload(){
    int count=Math.max(1,screenSenders.size());
    int perViewer=Math.min(activeVideoBitrateBps,activeTotalUploadBps/count);
    for(RtpSender sender:screenSenders.values()){
      try{
        RtpParameters params=sender.getParameters();
        for(RtpParameters.Encoding encoding:params.encodings){
          encoding.maxBitrateBps=perViewer;
          encoding.maxFramerate=activeVideoFps;
        }
        if(!sender.setParameters(params))Log.w(TAG,"Encoder rejected shared upload cap");
      }catch(Exception error){Log.w(TAG,"Cannot tune GAME viewer bitrate",error);}
    }
  }
  private void offerTo(String id){
    if(id==null||id.isEmpty()||stopping||factory==null)return;
    cancelPeerRepair(id);
    screenSenders.remove(id);
    if(gameAudio!=null)gameAudio.detach(id);
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
      @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state){
        if(state==PeerConnection.IceConnectionState.FAILED ||
          state==PeerConnection.IceConnectionState.DISCONNECTED)
          Log.w(TAG,"Viewer WebRTC "+id+" connection "+state);
        onViewerIceState(id,holder[0],state);
      }
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
    // GAME PCM is transported over SCTP/WebRTC, never via signaling or disk.
    // Unreliable, unordered chunks avoid seconds of delayed audio after packet loss.
    if(gameAudio!=null&&gameAudio.isCapturing()){
      DataChannel.Init audioOptions=new DataChannel.Init();
      audioOptions.ordered=false;
      audioOptions.maxRetransmits=0;
      DataChannel channel=pc.createDataChannel("nadmo-game-audio",audioOptions);
      gameAudio.attach(id,channel);
    }
    RtpSender screenSender=pc.addTrack(screenTrack,Arrays.asList("NADMO_GAME_SCREEN"));
    if(screenSender!=null)screenSenders.put(id,screenSender);
    balanceScreenUpload();
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
    for(Runnable task:peerRepairTasks.values())main.removeCallbacks(task);
    peerRepairTasks.clear();lastPeerRepairAt.clear();
    if(gameAudio!=null)for(String id:viewers.keySet())gameAudio.detach(id);
    for(PeerConnection pc:viewers.values()){pc.close();pc.dispose();}
    viewers.clear();pendingIce.clear();screenSenders.clear();
  }
  private void stopGame(String reason){
    if(stopping)return;stopping=true;privacyPaused=true;
    hideGameFloatingControl();
    if(retryTask!=null)main.removeCallbacks(retryTask);
    if(socketWatchdog!=null)main.removeCallbacks(socketWatchdog);
    if(firstFrameTimeout!=null)main.removeCallbacks(firstFrameTimeout);
    if(queuedChatTask!=null)main.removeCallbacks(queuedChatTask);
    queuedChatTask=null;queuedChat=null;
    if(networkCallback!=null&&connectivityManager!=null){
      try{connectivityManager.unregisterNetworkCallback(networkCallback);}
      catch(Exception ignored){}
      networkCallback=null;activeNetwork=null;
    }
    if(cpuWakeLock!=null&&cpuWakeLock.isHeld())cpuWakeLock.release();
    try{send(new JSONObjectSafe().put("type","leave").json());}catch(Exception ignored){}
    if(socket!=null){socket.close(1000,"game stream ended");socket=null;}
    webSocketOpen=false;clearViewers();
    if(gameAudio!=null){gameAudio.stop();gameAudio=null;}
    try{if(faceCapturer!=null){faceCapturer.stopCapture();faceCapturer.dispose();}}catch(Exception e){Log.w(TAG,"Stop face camera",e);}
    try{if(screenCapturer!=null){screenCapturer.stopCapture();screenCapturer.dispose();}}catch(Exception e){Log.w(TAG,"Stop projection",e);}
    synchronized(this){if(privacyBlackBuffer!=null){privacyBlackBuffer.release();privacyBlackBuffer=null;}}
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
    cookie="";roomId="";resumeToken="";started=false;activeRoom="";faceLayout=null;radioMode=false;
    liveCreatedAt=0L;lastRestReminderHour=0;
    signalingStarted=false;firstFrameReceived.set(false);lastCapturedFrameAt=0;
    hostSocketId="";
    try{
      NotificationManager manager=getSystemService(NotificationManager.class);
      if(manager!=null)for(Integer id:shownChatNotifications)manager.cancel(id);
    }catch(Exception ignored){}
    shownChatNotifications.clear();
    hideGameChatOverlay();
    try{NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.cancel(REST_NOTIFICATION_ID);}catch(Exception ignored){}
    state="stopped";status=reason;
    if("Siaran game diakhiri.".equals(reason))rememberFailure("LIVE_DIAKHIRI_HOST");
    else rememberFailure(reason);
    // Commit synchronously so a subsequent process recreation can read the
    // accurate final cause, including after resource pressure from the game.
    diagnosticPrefs().edit().putBoolean(DIAG_ARMED,false).commit();
    Intent event=new Intent(ACTION_STATUS).setPackage(getPackageName());
    event.putExtra("state","stopped").putExtra("message",reason).putExtra("room","");
    sendBroadcast(event);
    stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
  }
  @Override public void onDestroy(){
    if(started&&!stopping)stopGame("SERVICE_DESTROYED: Android menghentikan proses siaran.");
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
