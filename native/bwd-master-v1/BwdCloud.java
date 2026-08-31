package com.baliweddingdj.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;

public final class BwdCloud {
    private static final String PREF="bwd_cloud";
    private static final String CHANNEL="bwd_booking_alerts";
    private static volatile boolean initialized=false;
    private BwdCloud(){}

    public static boolean isOwner(Context c){ return "com.baliweddingdj.owner".equals(c.getPackageName()); }
    public static boolean backendConfigured(){ return BuildConfig.BWD_CLOUD_BASE_URL!=null && BuildConfig.BWD_CLOUD_BASE_URL.startsWith("https://"); }
    private static String firebaseAppId(Context c){
        if(isOwner(c) && BuildConfig.BWD_FIREBASE_OWNER_APP_ID!=null && !BuildConfig.BWD_FIREBASE_OWNER_APP_ID.isEmpty()) return BuildConfig.BWD_FIREBASE_OWNER_APP_ID;
        return BuildConfig.BWD_FIREBASE_APP_ID;
    }
    public static boolean firebaseConfigured(Context c){
        return !BuildConfig.BWD_FIREBASE_API_KEY.isEmpty() && !firebaseAppId(c).isEmpty() && !BuildConfig.BWD_FIREBASE_PROJECT_ID.isEmpty() && !BuildConfig.BWD_FIREBASE_SENDER_ID.isEmpty();
    }
    public static String statusText(Context c){
        if(!backendConfigured() && !firebaseConfigured(c)) return "Cloud setup required · local fallback active";
        if(!backendConfigured()) return "Firebase configured · backend URL required";
        if(!firebaseConfigured(c)) return "Backend configured · Firebase app config required";
        if(isOwner(c) && !adminReady(c)) return "Cloud ready · owner authorization required";
        return "Cloud connected · sync + push ready";
    }
    public static boolean adminReady(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("admin_key","").length()>=8; }

    public static void init(Context c){
        createChannel(c);
        if(initialized) return;
        initialized=true;
        try{
            if(firebaseConfigured(c) && FirebaseApp.getApps(c).isEmpty()){
                FirebaseOptions o=new FirebaseOptions.Builder().setApiKey(BuildConfig.BWD_FIREBASE_API_KEY).setApplicationId(firebaseAppId(c)).setProjectId(BuildConfig.BWD_FIREBASE_PROJECT_ID).setGcmSenderId(BuildConfig.BWD_FIREBASE_SENDER_ID).build();
                FirebaseApp.initializeApp(c,o);
            }
            if(firebaseConfigured(c)) FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t->saveToken(c,t));
        }catch(Exception ignored){}
        flushPending(c);
    }

    public static void saveToken(Context c,String token){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("fcm_token",token).apply();
        if(isOwner(c)){ if(adminReady(c)) registerOwnerDevice(c); }
        else registerClientDevices(c);
    }

    private static String randomToken(){ byte[] b=new byte[32];new SecureRandom().nextBytes(b);StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x));return s.toString(); }
    public static String clientToken(Context c,String bookingId){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);String k="client_token_"+bookingId;String v=p.getString(k,"");
        if(v.length()>=32)return v;v=randomToken();p.edit().putString(k,v).apply();return v;
    }

    public static void submitBooking(Context c,JSONObject booking,String id){
        try{
            JSONObject body=new JSONObject(booking.toString());body.put("booking_id",id);body.put("source","android");body.put("client_token",clientToken(c,id));
            String f=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("fcm_token","");if(f.length()>40)body.put("fcm_token",f);
            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("pending_booking_"+id,body.toString()).apply();
            if(backendConfigured()) sendQueuedBooking(c,id,body.toString());
        }catch(Exception ignored){}
    }
    private static void sendQueuedBooking(Context c,String id,String json){new Thread(()->{try{Response r=request("POST","/v1/bookings",json,null,null);if(r.code>=200&&r.code<300)c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove("pending_booking_"+id).apply();}catch(Exception ignored){}},"bwd-booking-upload").start();}

    public static void flushPending(Context c){
        if(!backendConfigured())return;SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        for(Map.Entry<String,?> e:p.getAll().entrySet()){
            if(e.getKey().startsWith("pending_booking_")&&e.getValue() instanceof String)sendQueuedBooking(c,e.getKey().substring(16),(String)e.getValue());
            if(e.getKey().startsWith("pending_receipt_")&&e.getValue() instanceof String)sendReceiptQueued(c,e.getKey().substring(16),(String)e.getValue());
        }
    }

    public static void registerClientDevices(Context c){
        if(isOwner(c)||!backendConfigured())return;SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);String f=p.getString("fcm_token","");if(f.length()<40)return;
        WeddingDb db=new WeddingDb(c);JSONArray a=db.bookings();for(int i=0;i<a.length();i++){JSONObject b=a.optJSONObject(i);if(b==null)continue;String id=b.optString("booking_id");String token=clientToken(c,id);new Thread(()->{try{JSONObject body=new JSONObject();body.put("token",f);request("POST","/v1/client/bookings/"+id+"/device",body.toString(),"X-BWD-Client-Key",token);}catch(Exception ignored){}},"bwd-client-device").start();}
    }

    public static void syncClientBookings(Activity a,WeddingDb db,Runnable done){
        if(!backendConfigured()){if(done!=null)done.run();return;}JSONArray rows=db.bookings();new Thread(()->{try{for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);if(x==null)continue;String id=x.optString("booking_id");Response r=request("GET","/v1/client/bookings/"+id,null,"X-BWD-Client-Key",clientToken(a,id));if(r.code==200){JSONObject o=new JSONObject(r.body);JSONObject b=o.optJSONObject("booking");if(b!=null)db.applyCloudBooking(b);}}}catch(Exception ignored){}a.runOnUiThread(()->{if(done!=null)done.run();});},"bwd-client-sync").start();
    }

    public static void syncOwner(Activity a,WeddingDb db,Runnable done){
        if(!backendConfigured()||!adminReady(a)){if(done!=null)done.run();return;}new Thread(()->{try{Response r=adminRequest(a,"GET","/v1/admin/bookings",null);if(r.code==200){JSONObject o=new JSONObject(r.body);JSONArray bookings=o.optJSONArray("bookings");if(bookings!=null)db.applyCloudBookings(bookings);}}catch(Exception ignored){}a.runOnUiThread(()->{if(done!=null)done.run();});},"bwd-owner-sync").start();
    }

    public static void clientAcceptQuote(Activity a,WeddingDb db,String id,Runnable done){clientMutation(a,db,id,"/v1/client/bookings/"+id+"/quote/accept",new JSONObject(),done);}
    public static void clientMusic(Activity a,WeddingDb db,String id,String plan,Runnable done){try{JSONObject o=new JSONObject();o.put("music_plan",plan);clientMutation(a,db,id,"/v1/client/bookings/"+id+"/music",o,done);}catch(Exception ignored){}}
    public static void clientTimeline(Activity a,WeddingDb db,String id,String timeline,Runnable done){try{JSONObject o=new JSONObject();o.put("timeline",timeline);clientMutation(a,db,id,"/v1/client/bookings/"+id+"/timeline",o,done);}catch(Exception ignored){}}
    private static void clientMutation(Activity a,WeddingDb db,String id,String path,JSONObject body,Runnable done){if(!backendConfigured()){if(done!=null)done.run();return;}new Thread(()->{try{Response r=request("POST",path,body.toString(),"X-BWD-Client-Key",clientToken(a,id));if(r.code>=200&&r.code<300&&r.body!=null&&!r.body.isEmpty()){JSONObject root=new JSONObject(r.body);JSONObject b=root.optJSONObject("booking");if(b!=null)db.applyCloudBooking(b);}}catch(Exception ignored){}a.runOnUiThread(()->{if(done!=null)done.run();});},"bwd-client-mutation").start();}

    public static boolean receiptUploadReady(){return backendConfigured();}
    public static String receiptStatus(Context c,String id){SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);if(p.getBoolean("receipt_synced_"+id,false))return "Uploaded securely · Waiting for admin verification";if(backendConfigured())return "Queued for secure upload";return "Saved on this device · cloud receipt sync unavailable";}
    public static void submitReceipt(Context c,String id,Uri uri){try{byte[] jpeg=compressReceipt(c,uri);if(jpeg==null||jpeg.length==0)return;JSONObject body=new JSONObject();body.put("booking_id",id);body.put("client_token",clientToken(c,id));body.put("content_type","image/jpeg");body.put("image_base64",Base64.encodeToString(jpeg,Base64.NO_WRAP));String payload=body.toString();c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("pending_receipt_"+id,payload).putBoolean("receipt_synced_"+id,false).apply();if(backendConfigured())sendReceiptQueued(c,id,payload);}catch(Exception ignored){}}
    private static void sendReceiptQueued(Context c,String id,String json){new Thread(()->{try{Response r=request("POST","/v1/bookings/"+id+"/payment-receipt",json,null,null);if(r.code>=200&&r.code<300)c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove("pending_receipt_"+id).putBoolean("receipt_synced_"+id,true).apply();}catch(Exception ignored){}},"bwd-receipt-upload").start();}
    private static byte[] compressReceipt(Context c,Uri uri)throws Exception{Bitmap src;try(InputStream in=c.getContentResolver().openInputStream(uri)){src=BitmapFactory.decodeStream(in);}if(src==null)return null;int w=src.getWidth(),h=src.getHeight(),max=1600;Bitmap out=src;if(Math.max(w,h)>max){float scale=max/(float)Math.max(w,h);out=Bitmap.createScaledBitmap(src,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);}int q=82;byte[] bytes;do{ByteArrayOutputStream bos=new ByteArrayOutputStream();out.compress(Bitmap.CompressFormat.JPEG,q,bos);bytes=bos.toByteArray();q-=10;}while(bytes.length>1800000&&q>=42);if(out!=src)out.recycle();src.recycle();return bytes.length<=1800000?bytes:null;}

    public static void showAdminEnrollment(Activity a){
        if(!firebaseConfigured(a)||!backendConfigured()){new AlertDialog.Builder(a).setTitle("Owner Cloud Setup").setMessage(statusText(a)).setPositiveButton("OK",null).show();return;}
        EditText code=new EditText(a);code.setHint("Private owner cloud key");code.setSingleLine(true);code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);code.setPadding(36,22,36,22);
        new AlertDialog.Builder(a).setTitle("Connect Owner Device").setMessage("Enter the private owner key once. It is stored only on this device and is never embedded in the APK.").setView(code).setPositiveButton("CONNECT",(d,w)->enroll(a,code.getText().toString().trim())).setNegativeButton("CANCEL",null).show();
    }
    private static void enroll(Activity a,String key){if(key.length()<8){dialog(a,"Owner Cloud","Key is too short.");return;}FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->new Thread(()->{try{JSONObject body=new JSONObject();body.put("token",token);body.put("platform","android");body.put("app_version",BuildConfig.VERSION_NAME);Response r=request("POST","/v1/admin/devices",body.toString(),"X-BWD-Admin-Key",key);if(r.code>=200&&r.code<300)a.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("admin_key",key).putString("fcm_token",token).apply();a.runOnUiThread(()->dialog(a,"Owner Cloud",r.code>=200&&r.code<300?"Owner device connected. Booking and payment push notifications are enabled.":"Connection failed. Check the owner key and backend."));}catch(Exception e){a.runOnUiThread(()->dialog(a,"Owner Cloud","Unable to reach backend."));}},"bwd-owner-enroll").start()).addOnFailureListener(e->dialog(a,"Owner Cloud","Unable to obtain Firebase token."));}
    private static void registerOwnerDevice(Context c){String key=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("admin_key","");String token=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("fcm_token","");if(key.length()<8||token.length()<40||!backendConfigured())return;new Thread(()->{try{JSONObject b=new JSONObject();b.put("token",token);b.put("platform","android");b.put("app_version",BuildConfig.VERSION_NAME);request("POST","/v1/admin/devices",b.toString(),"X-BWD-Admin-Key",key);}catch(Exception ignored){}},"bwd-owner-device").start();}

    public static void adminStatus(Activity a,WeddingDb db,String id,String status,Runnable done){try{JSONObject o=new JSONObject();o.put("status",status);adminMutation(a,db,id,"/v1/admin/bookings/"+id+"/status",o,done);}catch(Exception ignored){}}
    public static void adminQuote(Activity a,WeddingDb db,String id,JSONObject quote,Runnable done){adminMutation(a,db,id,"/v1/admin/bookings/"+id+"/quote",quote,done);}
    public static void adminVerifyPayment(Activity a,WeddingDb db,String id,long amount,boolean full,Runnable done){try{JSONObject o=new JSONObject();o.put("amount",amount);o.put("paid_in_full",full);adminMutation(a,db,id,"/v1/admin/bookings/"+id+"/payment/verify",o,done);}catch(Exception ignored){}}
    public static void adminTimeline(Activity a,WeddingDb db,String id,String timeline,Runnable done){try{JSONObject o=new JSONObject();o.put("timeline",timeline);adminMutation(a,db,id,"/v1/admin/bookings/"+id+"/timeline",o,done);}catch(Exception ignored){}}
    public static void adminNotes(Activity a,WeddingDb db,String id,String notes,Runnable done){try{JSONObject o=new JSONObject();o.put("admin_notes",notes);adminMutation(a,db,id,"/v1/admin/bookings/"+id+"/notes",o,done);}catch(Exception ignored){}}
    private static void adminMutation(Activity a,WeddingDb db,String id,String path,JSONObject body,Runnable done){if(!backendConfigured()||!adminReady(a)){if(done!=null)done.run();return;}new Thread(()->{try{Response r=adminRequest(a,"POST",path,body.toString());if(r.code>=200&&r.code<300&&r.body!=null&&!r.body.isEmpty()){JSONObject root=new JSONObject(r.body);JSONObject b=root.optJSONObject("booking");if(b!=null)db.applyCloudBooking(b);}}catch(Exception ignored){}a.runOnUiThread(()->{if(done!=null)done.run();});},"bwd-admin-mutation").start();}

    public static void viewAdminReceipt(Activity a,String id){if(!backendConfigured()||!adminReady(a)){dialog(a,"Payment Receipt","Owner cloud connection is required.");return;}new Thread(()->{try{byte[] bytes=adminBytes(a,"/v1/admin/bookings/"+id+"/payment-receipt");Bitmap bm=bytes==null?null:BitmapFactory.decodeByteArray(bytes,0,bytes.length);a.runOnUiThread(()->{if(bm==null){dialog(a,"Payment Receipt","Receipt could not be loaded.");return;}android.widget.ImageView iv=new android.widget.ImageView(a);iv.setImageBitmap(bm);iv.setAdjustViewBounds(true);iv.setPadding(12,12,12,12);new AlertDialog.Builder(a).setTitle("Payment Receipt · "+id).setView(iv).setPositiveButton("CLOSE",null).show();});}catch(Exception e){a.runOnUiThread(()->dialog(a,"Payment Receipt","Receipt could not be loaded."));}},"bwd-receipt-view").start();}

    static void showNotification(Context c,String title,String body,String bookingId,String type){createChannel(c);Intent i=new Intent(c,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(bookingId!=null)i.putExtra("booking_id",bookingId);if(type!=null)i.putExtra("event_type",type);PendingIntent pi=PendingIntent.getActivity(c,(int)(System.currentTimeMillis()&0xffff),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(c,CHANNEL):new android.app.Notification.Builder(c);b.setContentTitle(title==null||title.isEmpty()?"Bali Wedding DJ":title).setContentText(body==null?"":body).setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).setContentIntent(pi).setStyle(new android.app.Notification.BigTextStyle().bigText(body==null?"":body));((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify((int)(System.currentTimeMillis()&0x7fffffff),b.build());}
    private static void createChannel(Context c){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel ch=new NotificationChannel(CHANNEL,"Wedding booking alerts",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("Bali Wedding DJ booking, quotation and payment alerts");nm.createNotificationChannel(ch);}}

    private static Response adminRequest(Context c,String method,String path,String json)throws Exception{return request(method,path,json,"X-BWD-Admin-Key",c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("admin_key",""));}
    private static byte[] adminBytes(Context c,String path)throws Exception{String base=base();HttpURLConnection con=(HttpURLConnection)new URL(base+path).openConnection();con.setConnectTimeout(12000);con.setReadTimeout(20000);con.setRequestMethod("GET");con.setRequestProperty("X-BWD-Admin-Key",c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("admin_key",""));int code=con.getResponseCode();if(code<200||code>=300){con.disconnect();return null;}try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);con.disconnect();return out.toByteArray();}}
    private static String base(){String b=BuildConfig.BWD_CLOUD_BASE_URL;while(b.endsWith("/"))b=b.substring(0,b.length()-1);return b;}
    private static Response request(String method,String path,String json,String header,String value)throws Exception{HttpURLConnection con=(HttpURLConnection)new URL(base()+path).openConnection();con.setConnectTimeout(12000);con.setReadTimeout(20000);con.setRequestMethod(method);con.setRequestProperty("Accept","application/json");if(header!=null&&value!=null&&!value.isEmpty())con.setRequestProperty(header,value);if(json!=null){con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=con.getOutputStream()){out.write(json.getBytes(StandardCharsets.UTF_8));}}int code=con.getResponseCode();InputStream in=code>=400?con.getErrorStream():con.getInputStream();String body="";if(in!=null){try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder s=new StringBuilder();String line;while((line=r.readLine())!=null)s.append(line);body=s.toString();}}con.disconnect();return new Response(code,body);}
    private static final class Response{final int code;final String body;Response(int c,String b){code=c;body=b;}}
    private static void dialog(Activity a,String title,String msg){new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK",null).show();}
}
