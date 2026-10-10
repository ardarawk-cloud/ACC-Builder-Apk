package id.nadmo.live;

import android.Manifest;
import android.app.Activity;
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
  private static final String LIVE_HOST="nadmo-live-beta-20261009.ardarawk.workers.dev";
  private static final String ONLINE_URL="https://"+LIVE_HOST+"/app/";
  private static final String OFFGRID_APP="com.offgrid.mesh.dev";
  private static final int ASK_MEDIA=42;
  private static final int PICK_AVATAR=43;
  private WebView web;
  private FrameLayout root;
  private PermissionRequest pending;
  private ValueCallback<Uri[]> pendingPhoto;
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
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setAllowFileAccess(false);
    // User-selected Android gallery files are content:// URIs. Disabling this blocks
    // WebView from reading the picked photo, although desktop uploads still work.
    // Filesystem file:// access remains disabled; the OS grants access only to picked files.
    settings.setAllowContentAccess(true);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    web.setWebViewClient(new WebViewClient(){
      @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
        return loader.shouldInterceptRequest(req.getUrl());
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
        // User-driven gallery picker only. No blanket access to device storage.
        final Intent choose=new Intent(Intent.ACTION_GET_CONTENT);
        choose.addCategory(Intent.CATEGORY_OPENABLE);
        choose.setType("image/*");
        choose.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(Intent.createChooser(choose,"Pilih foto profil"),PICK_AVATAR);}
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
  }
  private void grantMedia(){
    PermissionRequest req=pending;pending=null;
    if(req==null)return;
    if(!trusted(req.getOrigin())){req.deny();return;}
    req.grant(req.getResources());
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=PICK_AVATAR)return;
    final ValueCallback<Uri[]> callback=pendingPhoto;
    pendingPhoto=null;
    if(callback==null)return;
    Uri uri=result==Activity.RESULT_OK&&data!=null?data.getData():null;
    if(uri==null&&result==Activity.RESULT_OK&&data!=null&&data.getClipData()!=null
      &&data.getClipData().getItemCount()>0){
      uri=data.getClipData().getItemAt(0).getUri();
    }
    // Accept only an OS-selected content-provider image, not arbitrary file paths.
    if(uri!=null&&"content".equalsIgnoreCase(uri.getScheme())){
      try{
        final String type=getContentResolver().getType(uri);
        if(type==null || !type.startsWith("image/"))uri=null;
      }catch(Exception ignored){uri=null;}
    }else uri=null;
    callback.onReceiveValue(uri==null?null:new Uri[]{uri});
  }
  @Override public void onRequestPermissionsResult(int code,String[] names,int[] results){
    super.onRequestPermissionsResult(code,names,results);
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
    if(pending!=null){pending.deny();pending=null;}
    if(pendingPhoto!=null){pendingPhoto.onReceiveValue(null);pendingPhoto=null;}
    if(web!=null){web.destroy();web=null;}
    super.onDestroy();
  }
}