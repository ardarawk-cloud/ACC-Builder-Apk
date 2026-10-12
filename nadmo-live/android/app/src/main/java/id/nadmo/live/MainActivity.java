package id.nadmo.live;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.os.Build;
import android.provider.Settings;
import android.webkit.CookieManager;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import androidx.webkit.WebViewCompat;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;

public final class MainActivity extends Activity {
  private static final String FALLBACK_URL="https://appassets.androidplatform.net/assets/index.html";
  private static final String APP_HOST="appassets.androidplatform.net";
  private static final String LIVE_HOST="live.nadmo.id";
  private static final String ONLINE_URL="https://"+LIVE_HOST+"/app/";
  private static final String OFFGRID_APP="com.offgrid.mesh.dev";
  private static final int ASK_MEDIA=42;
  private static final int PICK_AVATAR=43;
  private static final int ASK_GAME_PERMISSION=44;
  private static final int ASK_SCREEN_CAPTURE=45;
  private static final int ASK_CHAT_NOTIFICATIONS=46;
  private boolean askedChatNotifications=false;
  private String gameTitle="",gameName="",gameQuality="stable",gameBroadcastKind="gaming";
  private boolean gameFace=false;
  private String gameFaceLayout="";
  private boolean gameReceiverRegistered=false;
  private boolean activityVisible=false;
  // Consent dialog backgrounds this Activity before :game service exists.
  // Never launch an empty overlay service or issue STOP/PAUSE out of order.
  private boolean gameScreenConsentPending=false;
  // GAME runs in a separate foreground process. Do not read its static fields:
  // those belong to a different VM after launching Mobile Legends.
  private String gameLiveState="stopped",gameLiveRoom="",gameLiveMessage="";
  private long gameLiveStartedAt=0L;
  private final BroadcastReceiver gameStatusReceiver=new BroadcastReceiver(){
    @Override public void onReceive(Context context,Intent intent){
      if(GameCaptureService.ACTION_STATUS.equals(intent.getAction())){
        String next=intent.getStringExtra("state");
        // Covers process recreation while GAME is still capturing in :game.
        // Fail closed even if this Activity forgot its previous native state.
        if(activityVisible && ("live".equals(next)||"reconnecting".equals(next)||"starting".equals(next))){
          try{startService(new Intent(MainActivity.this,GameCaptureService.class).setAction(GameCaptureService.ACTION_PAUSE));}
          catch(Exception ignored){}
        }
        if(activityVisible && !"stopped".equals(next)){
          try{startService(new Intent(MainActivity.this,GameCaptureService.class).setAction(GameCaptureService.ACTION_HIDE_FLOAT));}
          catch(Exception ignored){}
        }
        gameLiveStartedAt="stopped".equals(next)?0L:intent.getLongExtra("startedAt",gameLiveStartedAt);
 String incomingKind=intent.getStringExtra("broadcastKind");
 if("radio".equals(incomingKind)||"gaming".equals(incomingKind))gameBroadcastKind=incomingKind;
        notifyGameStatus(next,intent.getStringExtra("message"),intent.getStringExtra("room"));
      }
    }
  };
  private WebView web;
  private FrameLayout root;
  private PermissionRequest pending;
  private ValueCallback<Uri[]> pendingPhoto;
  private boolean pendingGalleryMultiple=false;
  private boolean pendingVideoAllowed=false;
  private View fullView;
  private WebChromeClient.CustomViewCallback fullCallback;

  private boolean trusted(Uri uri) {
    return uri!=null
      && "https".equalsIgnoreCase(uri.getScheme())
      && (APP_HOST.equalsIgnoreCase(uri.getHost()) || LIVE_HOST.equalsIgnoreCase(uri.getHost()));
  }
  // Existing OFFGRID Alpha (BLE direct/group/relay) remains in its own native app.
  // Route only the fixed, user-tapped local URI; do not need Internet or a server.
  private void openOffgrid(){
    final Intent launch=getPackageManager().getLaunchIntentForPackage(OFFGRID_APP);
    if(launch==null){
      Toast.makeText(this,"OFFGRID belum terpasang. Instal sebelum internet blackout.",Toast.LENGTH_LONG).show();
      return;
    }
    try{startActivity(launch);}
    catch(Exception ignored){Toast.makeText(this,"Tidak dapat membuka OFFGRID.",Toast.LENGTH_LONG).show();}
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.rgb(8,11,21));
    getWindow().setNavigationBarColor(Color.rgb(8,11,21));
    root=new FrameLayout(this);
    web=new WebView(this);
    web.setBackgroundColor(Color.rgb(8,11,21));
    final WebViewAssetLoader loader=new WebViewAssetLoader.Builder()
      .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this))
      .build();
    WebSettings settings=web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setCacheMode(WebSettings.LOAD_NO_CACHE); // Always retrieve current host cyberdeck themes and live stage.
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setAllowFileAccess(false);
    // User-selected Android gallery files are content:// URIs. Disabling this blocks
    // WebView from reading the picked photo, although desktop uploads still work.
    // Filesystem file:// access remains disabled; the OS grants access only to picked files.
    settings.setAllowContentAccess(true);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    // AndroidX injects bridge messages only for the two audited first-party origins.
    // No addJavascriptInterface: that legacy API would be visible to every iframe.
    WebViewCompat.addWebMessageListener(web,"NadmoGame",new HashSet<>(Arrays.asList(
      "https://"+LIVE_HOST,"https://"+APP_HOST)),(view,message,origin,isMainFrame,reply)->{
      if(!isMainFrame||!trusted(origin)||message.getData()==null)return;
      runOnUiThread(()->{
        try{
          JSONObject command=new JSONObject(message.getData());
          String action=command.optString("type");
          if("start".equals(action))startGame(command);
          else if("stop".equals(action))stopGame();
          else if("launch".equals(action))launchGame();
           else if("launch-music".equals(action))launchMusic();
          else if("status".equals(action))requestGameStatus();
          else if("chat-settings".equals(action))openGameChatSettings();
          else if("chat-overlay-settings".equals(action))openGameOverlaySettings();
        }catch(Exception ignored){notifyGameStatus("error","Perintah GAME tidak valid.","");}
      });
    });
    IntentFilter gameFilter=new IntentFilter(GameCaptureService.ACTION_STATUS);
    if(Build.VERSION.SDK_INT>=33)registerReceiver(gameStatusReceiver,gameFilter,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(gameStatusReceiver,gameFilter);
    gameReceiverRegistered=true;
    web.setWebViewClient(new WebViewClient(){
      @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
        // Only appassets requests resolve locally. HTTPS /app/ must load the
        // deployed document so camera framing, host themes and HUD stay current.
        return loader.shouldInterceptRequest(req.getUrl());
      }
      @Override public void onPageFinished(WebView view,String url){
        if(url!=null&&trusted(Uri.parse(url)))requestGameStatus();
      }
      @Override public void onReceivedError(WebView view,WebResourceRequest req,android.webkit.WebResourceError error){
        if(req.isForMainFrame() && LIVE_HOST.equalsIgnoreCase(req.getUrl().getHost())){
          view.post(()->{if(!view.getUrl().equals(FALLBACK_URL))view.loadUrl(FALLBACK_URL);});
        }
      }
      @Override public void onReceivedHttpError(WebView view,WebResourceRequest req,android.webkit.WebResourceResponse response){
        if(req.isForMainFrame() && LIVE_HOST.equalsIgnoreCase(req.getUrl().getHost()) && response.getStatusCode()>=500){
          view.post(()->{if(!view.getUrl().equals(FALLBACK_URL))view.loadUrl(FALLBACK_URL);});
        }
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
        Uri uri=req.getUrl();
        if(uri!=null&&"nadmolive".equalsIgnoreCase(uri.getScheme())){
          if("offgrid".equalsIgnoreCase(uri.getHost()))openOffgrid();
          return true;
        }
        if(trusted(uri))return false;
        if("https".equalsIgnoreCase(uri.getScheme())){
          try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
        }
        return true;
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
        final String page=view.getUrl();
        if(page==null || !trusted(Uri.parse(page))){callback.onReceiveValue(null);return true;}
        if(pendingPhoto!=null){pendingPhoto.onReceiveValue(null);pendingPhoto=null;}
        pendingPhoto=callback;
        // File type follows the actual HTML input: avatar remains image-only;
        // SOCIAL accepts up to ten selected photos/videos in one OS picker.
        String accepts=String.join(",",params.getAcceptTypes()).toLowerCase(java.util.Locale.ROOT);
        pendingVideoAllowed=accepts.contains("video");
        pendingGalleryMultiple=pendingVideoAllowed&&params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE;
        final Intent choose=new Intent(Intent.ACTION_GET_CONTENT);
        choose.addCategory(Intent.CATEGORY_OPENABLE);
        choose.setType(pendingVideoAllowed?"*/*":"image/*");
        if(pendingVideoAllowed)choose.putExtra(Intent.EXTRA_MIME_TYPES,
          new String[]{"image/jpeg","image/png","image/webp","video/mp4","video/webm"});
        if(pendingGalleryMultiple)choose.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        choose.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(Intent.createChooser(choose,pendingVideoAllowed?"Pilih foto dan video":"Pilih foto profil"),PICK_AVATAR);}
        catch(Exception error){
          pendingPhoto.onReceiveValue(null);pendingPhoto=null;
        }
        return true;
      }
      @Override public void onPermissionRequest(PermissionRequest request){
        runOnUiThread(()->{
          if(!trusted(request.getOrigin())){request.deny();return;}
          for(String resource:request.getResources()){
            if(!PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
               && !PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)){
              request.deny();return;
            }
          }
          if(pending!=null && pending!=request)pending.deny();
          pending=request;
          if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
             && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
            grantMedia();
          }else{
            requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},ASK_MEDIA);
          }
        });
      }
      @Override public void onPermissionRequestCanceled(PermissionRequest request){
        if(pending==request)pending=null;
      }
      @Override public void onShowCustomView(View view,CustomViewCallback callback){
        if(fullView!=null){callback.onCustomViewHidden();return;}
        fullView=view;fullCallback=callback;
        web.setVisibility(View.GONE);
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
      }
      @Override public void onHideCustomView(){exitFullscreen();}
    });
    root.addView(web,new FrameLayout.LayoutParams(-1,-1));
    setContentView(root);
    web.loadUrl(ONLINE_URL);
    requestGameStatus();
  }
  private void requestGameStatus(){
    try{
      startService(new Intent(this,GameCaptureService.class).setAction(GameCaptureService.ACTION_QUERY));
    }catch(Exception error){
      notifyGameStatus("error","Layanan GAME LIVE tidak tersedia: "+error.getClass().getSimpleName(),"");
    }
  }
  @Override protected void onResume(){
    super.onResume();
    activityVisible=true;
    // Always fail CLOSED before navigating Android menus, NADMO settings or
    // other apps. The WebView can never reactivate GAME recording on its own.
    if(!gameScreenConsentPending&&!gameLiveRoom.isEmpty()&&
       ("live".equals(gameLiveState)||"paused".equals(gameLiveState)||
       "reconnecting".equals(gameLiveState)||"starting".equals(gameLiveState))){
      try{startService(new Intent(this,GameCaptureService.class).setAction(GameCaptureService.ACTION_PAUSE));}
      catch(Exception ignored){}
      try{startService(new Intent(this,GameCaptureService.class).setAction(GameCaptureService.ACTION_HIDE_FLOAT));}
      catch(Exception ignored){}
    }
    if(web!=null)requestGameStatus();
  }
  @Override protected void onPause(){
    activityVisible=false;
    super.onPause();
    // The screen broadcast runs in :game. Display its small control after
    // the host leaves NADMO for a game; never auto-resume media.
    if(!gameScreenConsentPending&&!gameLiveRoom.isEmpty()&&
       ("live".equals(gameLiveState)||"paused".equals(gameLiveState)||
       "reconnecting".equals(gameLiveState))){
      try{startService(new Intent(this,GameCaptureService.class).setAction(GameCaptureService.ACTION_SHOW_FLOAT));}
      catch(Exception ignored){}
    }
  }
  private void startGame(JSONObject command){
    if("live".equals(gameLiveState)||"paused".equals(gameLiveState)||
       "starting".equals(gameLiveState)||"reconnecting".equals(gameLiveState)){
      notifyGameStatus(gameLiveState,
        "Room GAME masih aktif/menyambung. Jangan MULAI ulang. Buka GAME, lalu gunakan tombol melayang RESUME jika JEDA.",
        gameLiveRoom);return;
    }
    gameBroadcastKind="radio".equals(command.optString("broadcastKind"))?"radio":"gaming";
     gameTitle=command.optString("title",gameBroadcastKind.equals("radio")?"NADMO RADIO LIVE":"NADMO GAME LIVE");
    gameName=gameBroadcastKind.equals("radio")?"NADMO RADIO":command.optString("game","Gaming");
    gameQuality=command.optString("quality","stable");
    gameFace=command.optBoolean("face",false);
    gameFaceLayout=gameFace&&command.optJSONObject("facecam")!=null?command.optJSONObject("facecam").toString():"";
    if(!Settings.canDrawOverlays(this)){
      notifyGameStatus("error","Aktifkan izin Tampilkan di atas aplikasi lain agar tombol melayang PAUSE tersedia. Setelah izin aktif, ketuk MULAI GAME lagi.","");
      openGameOverlaySettings();
      return;
    }
    if(Build.VERSION.SDK_INT>=33&&!askedChatNotifications&&
       checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
      askedChatNotifications=true;
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},ASK_CHAT_NOTIFICATIONS);
      return;
    }
    continueGamePermissions();
  }
  private void continueGamePermissions(){
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED||
       (gameFace&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)){
      requestPermissions(gameFace?new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA}:
        new String[]{Manifest.permission.RECORD_AUDIO},ASK_GAME_PERMISSION);
    }else requestScreenPermission();
  }
  private void requestScreenPermission(){
    try{
      MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
      if(manager==null){notifyGameStatus("error","Perekaman layar tidak tersedia di HP ini.","");return;}
      // GAME LIVE must continue after the creator opens another app.
      // Android 14+ defaults to allowing single-app capture: selecting NADMO
      // would end/blank the projection as soon as Mobile Legends is opened.
      // Request full-display sharing; Android still shows its consent dialog.
      notifyGameStatus("starting","Izinkan perekaman seluruh layar untuk GAME LIVE.","");
      Intent capture=Build.VERSION.SDK_INT>=34
        ?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        :manager.createScreenCaptureIntent();
      gameScreenConsentPending=true;
      startActivityForResult(capture,ASK_SCREEN_CAPTURE);
    }catch(Exception e){gameScreenConsentPending=false;notifyGameStatus("error","Gagal meminta izin rekam layar.","");}
  }
  private void stopGame(){
    startService(new Intent(this,GameCaptureService.class).setAction(GameCaptureService.ACTION_STOP));
  }
  private void launchGame(){
    if(!"live".equals(gameLiveState)&&!"paused".equals(gameLiveState)){
      notifyGameStatus("error","Mulai GAME LIVE sebelum membuka permainan.","");return;
    }
    String[] packages;
    String key=gameName.toLowerCase(java.util.Locale.ROOT);
    if(key.contains("mobile legends"))packages=new String[]{"com.mobile.legends"};
    else if(key.contains("free fire"))packages=new String[]{"com.dts.freefireth","com.dts.freefiremax"};
    else if(key.contains("pubg"))packages=new String[]{"com.tencent.ig","com.pubg.krmobile"};
    else if(key.contains("honor of kings"))packages=new String[]{"com.levelinfinite.sgameGlobal"};
    else if(key.contains("roblox"))packages=new String[]{"com.roblox.client"};
    else if(key.contains("minecraft"))packages=new String[]{"com.mojang.minecraftpe"};
    else packages=new String[0];
    for(String id:packages){
      Intent launch=getPackageManager().getLaunchIntentForPackage(id);
      if(launch!=null){try{startActivity(launch);return;}catch(Exception ignored){}}
    }
    Toast.makeText(this,"Buka game melalui layar utama HP. GAME LIVE tetap berjalan.",Toast.LENGTH_LONG).show();
  }
  private void launchMusic(){
     if(!"radio".equals(gameBroadcastKind)||!("live".equals(gameLiveState)||"paused".equals(gameLiveState))){
       notifyGameStatus("error","Mulai RADIO LIVE dulu sebelum OPEN MUSIC.",gameLiveRoom);return;
     }
     // Never choose a music app on behalf of the creator or scrape installed app details.
     // Android music app selector may have no matching apps. Home is a universal fallback
     // where the host can open ANY installed authorized player manually.
     try{
       Intent selector=Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_MUSIC);
       selector.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
       if(selector.resolveActivity(getPackageManager())!=null){
         startActivity(Intent.createChooser(selector,"OPEN MUSIC — pilih aplikasi musik"));
         return;
       }
     }catch(Exception ignored){}
     try{
       Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
         .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
       startActivity(home);
       Toast.makeText(this,"Pilih aplikasi musik di HP. Video layar pribadi tidak ikut disiarkan.",Toast.LENGTH_LONG).show();
     }catch(Exception ignored){
       notifyGameStatus(gameLiveState,"Tidak ada aplikasi musik yang dapat dibuka. Buka pemutar dari HP.",gameLiveRoom);
     }
   }
   private boolean chatNotificationsEnabled(){
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
    NotificationManager manager=getSystemService(NotificationManager.class);
    if(manager==null||!manager.areNotificationsEnabled())return false;
    NotificationChannel channel=manager.getNotificationChannel("nadmo_game_chat");
    // DEFAULT notifications may land silently in the shade, not over the game.
    // Do not show CHAT READY until its actual heads-up channel is enabled.
    return channel!=null&&channel.getImportance()>=NotificationManager.IMPORTANCE_HIGH;
  }
  private void openGameOverlaySettings(){
    if(Settings.canDrawOverlays(this)){
      notifyGameStatus(gameLiveState,"Tombol jeda dan chat melayang sudah diizinkan.",gameLiveRoom);
      return;
    }
    try{
      Intent intent=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
      startActivity(intent);
    }catch(Exception error){
      Toast.makeText(this,"Aktifkan Tampilkan di atas aplikasi lain untuk NADMO LIVE.",Toast.LENGTH_LONG).show();
    }
  }
  private void openGameChatSettings(){
    try{
      Intent settings=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
      startActivity(settings);
    }catch(Exception e){
      notifyGameStatus("error","Buka Setelan Android > NADMO LIVE > Notifikasi untuk mengaktifkan chat.","");
    }
  }
  private void notifyGameStatus(String state,String message,String room){
    gameLiveState=state==null?"stopped":state;
    gameLiveMessage=message==null?"":message;
    gameLiveRoom=room==null?"":room;
    if(web==null)return;
    String payload="{state:"+JSONObject.quote(state==null?"":state)+",message:"+
      JSONObject.quote(message==null?"":message)+",room:"+JSONObject.quote(room==null?"":room)+
      ",chatNotificationsEnabled:"+chatNotificationsEnabled()+",chatOverlayEnabled:"+Settings.canDrawOverlays(this)+
      ",startedAt:"+gameLiveStartedAt+",broadcastKind:"+JSONObject.quote(gameBroadcastKind)+",radioMusicSupported:true}";
    web.evaluateJavascript("window.dispatchEvent(new CustomEvent('nadmo-game-status',{detail:"+payload+"}));",null);
  }
  private void grantMedia(){
    PermissionRequest req=pending;pending=null;
    if(req==null)return;
    if(!trusted(req.getOrigin())){req.deny();return;}
    req.grant(req.getResources());
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==ASK_SCREEN_CAPTURE){
      gameScreenConsentPending=false;
      if(result!=Activity.RESULT_OK||data==null){notifyGameStatus("error","Izin rekam layar dibatalkan.","");return;}
      Intent run=new Intent(this,GameCaptureService.class).setAction(GameCaptureService.ACTION_START)
        .putExtra(GameCaptureService.EXTRA_RESULT,result)
        .putExtra(GameCaptureService.EXTRA_PROJECTION,data)
        .putExtra(GameCaptureService.EXTRA_TITLE,gameTitle)
        .putExtra(GameCaptureService.EXTRA_GAME,gameName)
         .putExtra(GameCaptureService.EXTRA_BROADCAST_KIND,gameBroadcastKind)
        .putExtra(GameCaptureService.EXTRA_QUALITY,gameQuality)
        .putExtra(GameCaptureService.EXTRA_FACE,gameFace)
        .putExtra(GameCaptureService.EXTRA_FACE_LAYOUT,gameFaceLayout)
        .putExtra(GameCaptureService.EXTRA_COOKIE,CookieManager.getInstance().getCookie(ONLINE_URL));
      try{
        if(Build.VERSION.SDK_INT>=26)startForegroundService(run);else startService(run);
        // A broadcast may arrive before the WebView finishes resuming after
        // MediaProjection consent. A few bounded queries recover the actual
        // server-created status instead of leaving BUKA GAME disabled forever.
        if(web!=null){
          web.postDelayed(()->{if(activityVisible&&"starting".equals(gameLiveState))requestGameStatus();},2500);
          web.postDelayed(()->{if(activityVisible&&"starting".equals(gameLiveState))requestGameStatus();},6500);
          web.postDelayed(()->{if(activityVisible&&"starting".equals(gameLiveState))requestGameStatus();},11000);
        }
      }catch(Exception e){notifyGameStatus("error","Android tidak mengizinkan layanan LIVE.","");}
      return;
    }
    if(request!=PICK_AVATAR)return;
    final ValueCallback<Uri[]> callback=pendingPhoto;
    pendingPhoto=null;
    if(callback==null)return;
    final boolean mediaAllowed=pendingVideoAllowed,multiple=pendingGalleryMultiple;
    pendingVideoAllowed=false;pendingGalleryMultiple=false;
    final java.util.ArrayList<Uri> selected=new java.util.ArrayList<>();
    if(result==Activity.RESULT_OK&&data!=null){
      if(data.getClipData()!=null){
        int count=Math.min(data.getClipData().getItemCount(),multiple?10:1);
        for(int i=0;i<count;i++)selected.add(data.getClipData().getItemAt(i).getUri());
      }else if(data.getData()!=null)selected.add(data.getData());
    }
    // OS-selected content URI only. Enforce type even if a provider lies about
    // the MIME chooser filter. No broad storage permission is required.
    final java.util.ArrayList<Uri> accepted=new java.util.ArrayList<>();
    for(Uri uri:selected){
      if(uri==null||!"content".equalsIgnoreCase(uri.getScheme()))continue;
      String mime=null;
      try{mime=getContentResolver().getType(uri);}catch(Exception ignored){}
      if(mime==null)continue;
      if(mediaAllowed){
        if(!java.util.Arrays.asList("image/jpeg","image/png","image/webp","video/mp4","video/webm").contains(mime))continue;
      }else if(!mime.startsWith("image/"))continue;
      accepted.add(uri);
    }
    callback.onReceiveValue(accepted.isEmpty()?null:accepted.toArray(new Uri[0]));
  }
  @Override public void onRequestPermissionsResult(int code,String[] names,int[] results){
    super.onRequestPermissionsResult(code,names,results);
    if(code==ASK_CHAT_NOTIFICATIONS){
      if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
        notifyGameStatus("starting","Notifikasi chat dimatikan. Aktifkan izin notifikasi NADMO di Setelan HP.","");
      continueGamePermissions();return;
    }
    if(code==ASK_GAME_PERMISSION){
      if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
        notifyGameStatus("error","Izin mikrofon diperlukan untuk GAME LIVE.","");return;
      }
      // Optional facecam can be omitted if its separate permission is denied.
      if(gameFace&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
        gameFace=false;
        notifyGameStatus("starting","Kamera ditolak. GAME LIVE berjalan tanpa facecam.","");
      }
      requestScreenPermission();return;
    }
    if(code!=ASK_MEDIA)return;
    if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
       && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
      grantMedia();
    }else{
      if(pending!=null)pending.deny();
      pending=null;
    }
  }
  private void exitFullscreen(){
    if(fullView==null)return;
    root.removeView(fullView);fullView=null;
    web.setVisibility(View.VISIBLE);
    if(fullCallback!=null)fullCallback.onCustomViewHidden();
    fullCallback=null;
  }
  @Override public void onBackPressed(){
    if(fullView!=null){exitFullscreen();return;}
    if(web.canGoBack()){web.goBack();return;}
    super.onBackPressed();
  }
  @Override protected void onDestroy(){
    if(gameReceiverRegistered){unregisterReceiver(gameStatusReceiver);gameReceiverRegistered=false;}
    if(pending!=null){pending.deny();pending=null;}
    if(pendingPhoto!=null){pendingPhoto.onReceiveValue(null);pendingPhoto=null;}
    if(web!=null){web.destroy();web=null;}
    super.onDestroy();
  }
}
