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
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import androidx.webkit.WebViewAssetLoader;

public final class MainActivity extends Activity {
  private static final String FALLBACK_URL="https://appassets.androidplatform.net/assets/index.html";
  private static final String APP_HOST="appassets.androidplatform.net";
  private static final String LIVE_HOST="nadmo-live-beta-20261009.ardarawk.workers.dev";
  private static final String ONLINE_URL="https://"+LIVE_HOST+"/app/";
  private static final int ASK_MEDIA=42;
  private WebView web;
  private FrameLayout root;
  private PermissionRequest pending;
  private View fullView;
  private WebChromeClient.CustomViewCallback fullCallback;

  private boolean trusted(Uri uri) {
    return uri!=null
      && "https".equalsIgnoreCase(uri.getScheme())
      && (APP_HOST.equalsIgnoreCase(uri.getHost()) || LIVE_HOST.equalsIgnoreCase(uri.getHost()));
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
    settings.setAllowContentAccess(false);
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
        if(trusted(uri))return false;
        if("https".equalsIgnoreCase(uri.getScheme())){
          try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
        }
        return true;
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
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
    if(web!=null){web.destroy();web=null;}
    super.onDestroy();
  }
}