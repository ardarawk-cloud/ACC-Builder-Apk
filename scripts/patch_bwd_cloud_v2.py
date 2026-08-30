#!/usr/bin/env python3
import os, sys, pathlib, re

root = pathlib.Path(sys.argv[1] if len(sys.argv)>1 else 'bwd-native')
app = root/'app'
java_dir = app/'src/main/java/com/baliweddingdj/app'
manifest = app/'src/main/AndroidManifest.xml'
gradle = app/'build.gradle'
main = java_dir/'MainActivity.java'

def env(name):
    return os.environ.get(name,'').replace('\\','\\\\').replace('"','\\"')

g = gradle.read_text()
g = re.sub(r'compileSdk\s+\d+', 'compileSdk 36', g)
g = re.sub(r'targetSdk\s+\d+', 'targetSdk 36', g)
g = re.sub(r'versionCode\s+\d+', 'versionCode 2', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.0.0-cloud-alpha'", g)
fields = f'''\n        buildConfigField "String", "BWD_FIREBASE_API_KEY", "\\"{env('BWD_FIREBASE_API_KEY')}\\""\n        buildConfigField "String", "BWD_FIREBASE_APP_ID", "\\"{env('BWD_FIREBASE_APP_ID')}\\""\n        buildConfigField "String", "BWD_FIREBASE_PROJECT_ID", "\\"{env('BWD_FIREBASE_PROJECT_ID')}\\""\n        buildConfigField "String", "BWD_FIREBASE_SENDER_ID", "\\"{env('BWD_FIREBASE_SENDER_ID')}\\""\n        buildConfigField "String", "BWD_CLOUD_BASE_URL", "\\"{env('BWD_CLOUD_BASE_URL')}\\""\n'''
if 'BWD_FIREBASE_API_KEY' not in g:
    g = g.replace("        versionName '2.0.0-cloud-alpha'\n", "        versionName '2.0.0-cloud-alpha'\n" + fields)
if 'buildFeatures' not in g:
    g = g.replace('\n    buildTypes {', '\n    buildFeatures {\n        buildConfig true\n    }\n\n    buildTypes {')
if 'firebase-bom' not in g:
    g += '''\n\ndependencies {\n    implementation platform('com.google.firebase:firebase-bom:34.18.0')\n    implementation 'com.google.firebase:firebase-messaging'\n}\n'''
gradle.write_text(g)

m = manifest.read_text()
service = '''\n        <service\n            android:name=".BwdFirebaseMessagingService"\n            android:exported="false">\n            <intent-filter>\n                <action android:name="com.google.firebase.MESSAGING_EVENT" />\n            </intent-filter>\n        </service>'''
if 'BwdFirebaseMessagingService' not in m:
    m = m.replace('        <activity android:name=".MainActivity"', service + '\n        <activity android:name=".MainActivity"')
manifest.write_text(m)

s = main.read_text()
if 'import android.Manifest;' not in s:
    s = s.replace('package com.baliweddingdj.app;\n\n', 'package com.baliweddingdj.app;\n\nimport android.Manifest;\n')
old = '        db=new WeddingDb(this); prefs=getSharedPreferences("bwd_secure",MODE_PRIVATE);\n        buildShell(); showHome();'
new = '        db=new WeddingDb(this); prefs=getSharedPreferences("bwd_secure",MODE_PRIVATE);\n        BwdCloud.init(this); requestPushPermission();\n        buildShell(); showHome();'
if old in s:
    s = s.replace(old,new)
if 'private void requestPushPermission()' not in s:
    anchor = '    private void buildShell(){'
    method = '''    private void requestPushPermission(){\n        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){\n            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9001);\n        }\n    }\n\n'''
    s = s.replace(anchor, method+anchor)
s = s.replace('String id=db.addBooking(b);clearDraft();showBookingSuccess(id);','String id=db.addBooking(b);BwdCloud.submitBooking(this,b,id);clearDraft();showBookingSuccess(id);')
s = s.replace('Your wedding request has been saved.', 'Your wedding request has been saved and queued for secure cloud sync.')
s = s.replace('This native foundation keeps customer data locally on the device until the secure cloud backend is provisioned. No API secret or admin credential is embedded in this APK.', 'Bookings are kept locally first, then securely synced to Bali Wedding DJ Cloud when online. Push notification configuration never embeds an admin credential in the APK.')
admin_anchor = '        LinearLayout stats=Ui.card(this);'
if 'ENABLE CLOUD NOTIFICATIONS' not in s:
    insertion = '''        LinearLayout cloud=Ui.card(this);cloud.addView(Ui.text(this,"CLOUD STATUS",11,Ui.GOLD,true));cloud.addView(Ui.text(this,BwdCloud.statusText(),13,Ui.MUTED,false));cloud.addView(Ui.space(this,8));cloud.addView(fullButton("ENABLE CLOUD NOTIFICATIONS",false,()->BwdCloud.showAdminEnrollment(this)));p.addView(cloud);\n'''
    s = s.replace(admin_anchor, insertion+admin_anchor)
main.write_text(s)

(java_dir/'BwdCloud.java').write_text(r'''package com.baliweddingdj.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.InputType;
import android.widget.EditText;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class BwdCloud {
    private static final String PREF="bwd_cloud";
    private static final String CHANNEL="bwd_booking_alerts";
    private static volatile boolean initialized=false;
    private BwdCloud(){}

    public static void init(Context context){
        createChannel(context);
        if(initialized) return;
        initialized=true;
        try{
            if(firebaseConfigured() && FirebaseApp.getApps(context).isEmpty()){
                FirebaseOptions options=new FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.BWD_FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.BWD_FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.BWD_FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.BWD_FIREBASE_SENDER_ID)
                    .build();
                FirebaseApp.initializeApp(context,options);
            }
            if(firebaseConfigured()){
                FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->{
                    context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("fcm_token",token).apply();
                });
            }
        }catch(Exception ignored){}
        flushPending(context);
    }

    public static String statusText(){
        if(!backendConfigured() && !firebaseConfigured()) return "Cloud setup required · local mode remains active";
        if(!backendConfigured()) return "Push configured · booking API URL required";
        if(!firebaseConfigured()) return "Booking sync configured · Firebase push setup required";
        return "Cloud booking sync + push ready";
    }

    public static void submitBooking(Context context, JSONObject booking, String bookingId){
        try{
            JSONObject payload=new JSONObject(booking.toString());
            payload.put("booking_id",bookingId);
            payload.put("source","android");
            queue(context,bookingId,payload.toString());
            if(backendConfigured()) sendQueued(context,bookingId,payload.toString());
        }catch(Exception ignored){}
    }

    public static void flushPending(Context context){
        if(!backendConfigured()) return;
        SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        for(Map.Entry<String,?> e:p.getAll().entrySet()){
            if(e.getKey().startsWith("pending_") && e.getValue() instanceof String){
                String id=e.getKey().substring("pending_".length());
                sendQueued(context,id,(String)e.getValue());
            }
        }
    }

    private static void queue(Context c,String id,String json){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("pending_"+id,json).apply();
    }

    private static void sendQueued(Context c,String id,String json){
        new Thread(()->{
            try{
                int code=post("/v1/bookings",json,null);
                if(code>=200 && code<300){
                    c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove("pending_"+id).apply();
                }
            }catch(Exception ignored){}
        },"bwd-cloud-booking").start();
    }

    public static void showAdminEnrollment(Activity activity){
        if(!firebaseConfigured()){
            new AlertDialog.Builder(activity).setTitle("Cloud Notifications").setMessage("Firebase push is not configured in this build yet.").setPositiveButton("OK",null).show();return;
        }
        if(!backendConfigured()){
            new AlertDialog.Builder(activity).setTitle("Cloud Notifications").setMessage("Cloud API URL is not configured in this build yet.").setPositiveButton("OK",null).show();return;
        }
        EditText code=new EditText(activity);code.setHint("One-time admin enrollment code");code.setSingleLine(true);code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);code.setPadding(36,22,36,22);
        new AlertDialog.Builder(activity).setTitle("Enable Cloud Notifications").setMessage("Enter the private admin enrollment code. It is sent only to the cloud backend and is never stored in the APK source.").setView(code).setPositiveButton("ENABLE",(d,w)->enroll(activity,code.getText().toString().trim())).setNegativeButton("CANCEL",null).show();
    }

    private static void enroll(Activity a,String adminCode){
        if(adminCode.length()<8){toastDialog(a,"Cloud Notifications","Enrollment code is too short.");return;}
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->{
            new Thread(()->{
                try{
                    JSONObject body=new JSONObject();body.put("token",token);body.put("platform","android");body.put("app_version",BuildConfig.VERSION_NAME);
                    int code=post("/v1/admin/devices",body.toString(),adminCode);
                    a.runOnUiThread(()->toastDialog(a,"Cloud Notifications",code>=200&&code<300?"This device is enrolled for new booking alerts.":"Enrollment failed. Check the admin code or cloud setup."));
                }catch(Exception e){a.runOnUiThread(()->toastDialog(a,"Cloud Notifications","Unable to reach cloud backend."));}
            },"bwd-cloud-enroll").start();
        }).addOnFailureListener(e->toastDialog(a,"Cloud Notifications","Unable to obtain Firebase notification token."));
    }

    static void saveToken(Context c,String token){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("fcm_token",token).apply();
    }

    static void showNotification(Context c,String title,String body,String bookingId){
        createChannel(c);
        Intent i=new Intent(c,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(bookingId!=null)i.putExtra("booking_id",bookingId);
        PendingIntent pi=PendingIntent.getActivity(c,1001,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(c,CHANNEL):new android.app.Notification.Builder(c);
        b.setContentTitle(title==null||title.isEmpty()?"Bali Wedding DJ":title).setContentText(body==null?"":body).setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).setContentIntent(pi).setStyle(new android.app.Notification.BigTextStyle().bigText(body==null?"":body));
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify((int)(System.currentTimeMillis()&0x7fffffff),b.build());
    }

    private static void createChannel(Context c){
        if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel ch=new NotificationChannel(CHANNEL,"Wedding booking alerts",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("New Bali Wedding DJ booking and payment alerts");nm.createNotificationChannel(ch);}
    }

    private static int post(String path,String json,String adminCode)throws Exception{
        String base=BuildConfig.BWD_CLOUD_BASE_URL;
        while(base.endsWith("/"))base=base.substring(0,base.length()-1);
        HttpURLConnection con=(HttpURLConnection)new URL(base+path).openConnection();con.setConnectTimeout(12000);con.setReadTimeout(15000);con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json; charset=utf-8");if(adminCode!=null)con.setRequestProperty("X-BWD-Admin-Enroll",adminCode);
        try(OutputStream out=con.getOutputStream()){out.write(json.getBytes(StandardCharsets.UTF_8));}
        int code=con.getResponseCode();InputStream in=code>=400?con.getErrorStream():con.getInputStream();if(in!=null){try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){while(r.readLine()!=null){}}}con.disconnect();return code;
    }

    private static boolean firebaseConfigured(){return !BuildConfig.BWD_FIREBASE_API_KEY.isEmpty()&&!BuildConfig.BWD_FIREBASE_APP_ID.isEmpty()&&!BuildConfig.BWD_FIREBASE_PROJECT_ID.isEmpty()&&!BuildConfig.BWD_FIREBASE_SENDER_ID.isEmpty();}
    private static boolean backendConfigured(){return BuildConfig.BWD_CLOUD_BASE_URL!=null&&BuildConfig.BWD_CLOUD_BASE_URL.startsWith("https://");}
    private static void toastDialog(Activity a,String title,String msg){new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK",null).show();}
}
''')

(java_dir/'BwdFirebaseMessagingService.java').write_text(r'''package com.baliweddingdj.app;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class BwdFirebaseMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token){
        super.onNewToken(token);
        BwdCloud.saveToken(this,token);
    }

    @Override public void onMessageReceived(RemoteMessage message){
        super.onMessageReceived(message);
        String title="Bali Wedding DJ";
        String body="You have a new update.";
        if(message.getNotification()!=null){
            if(message.getNotification().getTitle()!=null)title=message.getNotification().getTitle();
            if(message.getNotification().getBody()!=null)body=message.getNotification().getBody();
        }
        if(message.getData().containsKey("title"))title=message.getData().get("title");
        if(message.getData().containsKey("body"))body=message.getData().get("body");
        BwdCloud.showNotification(this,title,body,message.getData().get("booking_id"));
    }
}
''')

print('BWD Cloud V2 patch applied')
print('firebase_configured=', bool(env('BWD_FIREBASE_API_KEY') and env('BWD_FIREBASE_APP_ID') and env('BWD_FIREBASE_PROJECT_ID') and env('BWD_FIREBASE_SENDER_ID')))
print('backend_configured=', env('BWD_CLOUD_BASE_URL').startswith('https://'))
