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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
  private static final String SITE="https://live.nadmo.id/";
  private static final String HOST="live.nadmo.id";
  private static final int ASK_MEDIA=42;
  private WebView web;
  private FrameLayout root;
  private LinearLayout offline;
  private PermissionRequest pending;
  private View fullView;
  private WebChromeClient.CustomViewCallback fullCallback;
  private boolean pageFailed;

  private boolean trusted(Uri u) {
    return u!=null && "https".equalsIgnoreCase(u.getScheme()) && HOST.equalsIgnoreCase(u.getHost());
  }
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setStatusBarColor(Color.rgb(8,12,23));
    getWindow().setNavigationBarColor(Color.rgb(8,12,23));
    root=new FrameLayout(this);
    web=new WebView(this);
    web.setBackgroundColor(Color.rgb(8,12,23));
    WebSettings settings=web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setMediaPlaybackRequiresUserGesture(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
        Uri u=req.getUrl();
        if(trusted(u))return false;
        if("https".equalsIgnoreCase(u.getScheme()))startActivity(new Intent(Intent.ACTION_VIEW,u));
        return true;
      }
      @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){pageFailed=false;}
      @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError e){
        if(req.isForMainFrame()){pageFailed=true;showOffline();}
      }
      @Override public void onPageFinished(WebView view,String url){
        if(!pageFailed && trusted(Uri.parse(url)))hideOffline();
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public void onPermissionRequest(PermissionRequest request){
        runOnUiThread(()->{
          if(!trusted(request.getOrigin())){request.deny();return;}
          for(String resource:request.getResources()){
            if(!PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) &&
               !PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)){request.deny();return;}
          }
          pending=request;
          if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED &&
             checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
            grantMedia();
          } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},ASK_MEDIA);
          }
        });
      }
      @Override public void onPermissionRequestCanceled(PermissionRequest request){
        if(pending==request)pending=null;
      }
      @Override public void onShowCustomView(View view,CustomViewCallback callback){
        if(fullView!=null){callback.onCustomViewHidden();return;}
        fullView=view;fullCallback=callback;web.setVisibility(View.GONE);
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
      }
      @Override public void onHideCustomView(){exitFull();}
    });
    root.addView(web,new FrameLayout.LayoutParams(-1,-1));
    setContentView(root);
    web.loadUrl(SITE);
  }
  private void grantMedia(){
    if(pending==null)return;
    PermissionRequest req=pending;pending=null;
    if(!trusted(req.getOrigin())){req.deny();return;}
    req.grant(req.getResources());
  }
  @Override public void onRequestPermissionsResult(int code,String[] perms,int[] results){
    super.onRequestPermissionsResult(code,perms,results);
    if(code!=ASK_MEDIA)return;
    if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED &&
       checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
      grantMedia();
    }else{
      if(pending!=null)pending.deny();
      pending=null;
    }
  }
  private void showOffline(){
    if(offline!=null)return;
    offline=new LinearLayout(this);
    offline.setOrientation(LinearLayout.VERTICAL);
    offline.setGravity(android.view.Gravity.CENTER);
    offline.setPadding(32,32,32,32);
    offline.setBackgroundColor(Color.rgb(8,12,23));
    TextView title=new TextView(this);
    title.setText("NADMO LIVE");title.setTextSize(28);
    title.setTextColor(Color.WHITE);
    title.setGravity(android.view.Gravity.CENTER);
    offline.addView(title);
    TextView hint=new TextView(this);
    hint.setText("Server NADMO LIVE belum tersedia atau koneksi terputus.\nBackend harus diaktifkan sebelum mulai siaran.");
    hint.setTextColor(Color.rgb(180,184,207));
    hint.setTextSize(14);
    hint.setGravity(android.view.Gravity.CENTER);
    hint.setPadding(0,25,0,25);
    offline.addView(hint);
    Button retry=new Button(this);
    retry.setText("COBA LAGI");
    retry.setOnClickListener(v->web.loadUrl(SITE));
    offline.addView(retry);
    root.addView(offline,new FrameLayout.LayoutParams(-1,-1));
  }
  private void hideOffline(){
    if(offline!=null){root.removeView(offline);offline=null;}
  }
  private void exitFull(){
    if(fullView==null)return;
    root.removeView(fullView);fullView=null;web.setVisibility(View.VISIBLE);
    if(fullCallback!=null)fullCallback.onCustomViewHidden();
    fullCallback=null;
  }
  @Override public void onBackPressed(){
    if(fullView!=null){exitFull();return;}
    if(web.canGoBack()){web.goBack();return;}
    super.onBackPressed();
  }
  @Override protected void onDestroy(){
    if(pending!=null){pending.deny();pending=null;}
    if(web!=null){web.destroy();web=null;}
    super.onDestroy();
  }
}
