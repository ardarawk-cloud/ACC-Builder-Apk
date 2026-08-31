#!/usr/bin/env python3
import pathlib, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-native')
java_dir = root / 'app/src/main/java/com/baliweddingdj/app'
cloud = java_dir / 'BwdCloud.java'
service = java_dir / 'BwdFirebaseMessagingService.java'
main = java_dir / 'MainActivity.java'

s = cloud.read_text()

imports = '''import android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;\nimport android.net.Uri;\nimport android.util.Base64;\n\nimport java.io.ByteArrayOutputStream;\nimport java.security.SecureRandom;\n'''
if 'import android.graphics.Bitmap;' not in s:
    s = s.replace('import android.content.SharedPreferences;\n', 'import android.content.SharedPreferences;\n' + imports)

# Separate booking queue from receipt queue.
s = s.replace('startsWith("pending_")', 'startsWith("pending_booking_")')
s = s.replace('substring("pending_".length())', 'substring("pending_booking_".length())')
s = s.replace('putString("pending_"+id,json)', 'putString("pending_booking_"+id,json)')
s = s.replace('remove("pending_"+id)', 'remove("pending_booking_"+id)')

old_submit = '''            JSONObject payload=new JSONObject(booking.toString());\n            payload.put("booking_id",bookingId);\n            payload.put("source","android");'''
new_submit = '''            JSONObject payload=new JSONObject(booking.toString());\n            payload.put("booking_id",bookingId);\n            payload.put("source","android");\n            payload.put("client_token",clientToken(context,bookingId));'''
s = s.replace(old_submit, new_submit)

anchor = '''    static void saveToken(Context c,String token){\n        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("fcm_token",token).apply();\n    }\n\n'''
receipt_methods = r'''    public static boolean receiptUploadReady(){ return backendConfigured(); }

    public static String receiptStatus(Context c,String bookingId){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(p.getBoolean("receipt_synced_"+bookingId,false)) return "Uploaded securely · Waiting for admin verification";
        if(backendConfigured()) return "Queued for secure upload";
        return "Saved on this device · cloud receipt sync unavailable";
    }

    public static void submitReceipt(Context context,String bookingId,Uri uri){
        try{
            byte[] jpeg=compressReceipt(context,uri);
            if(jpeg==null || jpeg.length==0) return;
            JSONObject body=new JSONObject();
            body.put("booking_id",bookingId);
            body.put("client_token",clientToken(context,bookingId));
            body.put("content_type","image/jpeg");
            body.put("image_base64",Base64.encodeToString(jpeg,Base64.NO_WRAP));
            String payload=body.toString();
            SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
            p.edit().putString("pending_receipt_"+bookingId,payload).putBoolean("receipt_synced_"+bookingId,false).apply();
            if(backendConfigured()) sendReceiptQueued(context,bookingId,payload);
        }catch(Exception ignored){}
    }

    private static void sendReceiptQueued(Context c,String bookingId,String json){
        new Thread(()->{
            try{
                int code=post("/v1/bookings/"+bookingId+"/payment-receipt",json,null);
                if(code>=200 && code<300){
                    c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                        .remove("pending_receipt_"+bookingId)
                        .putBoolean("receipt_synced_"+bookingId,true).apply();
                }
            }catch(Exception ignored){}
        },"bwd-cloud-receipt").start();
    }

    private static byte[] compressReceipt(Context c,Uri uri)throws Exception{
        Bitmap src;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){ src=BitmapFactory.decodeStream(in); }
        if(src==null) return null;
        int w=src.getWidth(),h=src.getHeight();
        int max=1600;
        Bitmap out=src;
        if(Math.max(w,h)>max){
            float scale=max/(float)Math.max(w,h);
            out=Bitmap.createScaledBitmap(src,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);
        }
        int quality=82;
        byte[] bytes;
        do{
            ByteArrayOutputStream bos=new ByteArrayOutputStream();
            out.compress(Bitmap.CompressFormat.JPEG,quality,bos);
            bytes=bos.toByteArray();
            quality-=10;
        }while(bytes.length>1800000 && quality>=42);
        if(out!=src) out.recycle();
        src.recycle();
        return bytes.length<=1800000?bytes:null;
    }

    private static String clientToken(Context c,String bookingId){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String key="client_token_"+bookingId;
        String existing=p.getString(key,"");
        if(existing.length()>=32) return existing;
        byte[] b=new byte[32]; new SecureRandom().nextBytes(b);
        StringBuilder out=new StringBuilder(); for(byte x:b) out.append(String.format(java.util.Locale.US,"%02x",x));
        String token=out.toString(); p.edit().putString(key,token).apply(); return token;
    }

'''
if 'public static void submitReceipt' not in s:
    s = s.replace(anchor, anchor + receipt_methods)

old_notif = '''    static void showNotification(Context c,String title,String body,String bookingId){\n        createChannel(c);\n        Intent i=new Intent(c,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(bookingId!=null)i.putExtra("booking_id",bookingId);\n        PendingIntent pi=PendingIntent.getActivity(c,1001,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);'''
new_notif = '''    static void showNotification(Context c,String title,String body,String bookingId,String url){\n        createChannel(c);\n        Intent i;\n        if(url!=null&&!url.isEmpty()){ i=new Intent(Intent.ACTION_VIEW,Uri.parse(url)); }\n        else { i=new Intent(c,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(bookingId!=null)i.putExtra("booking_id",bookingId); }\n        PendingIntent pi=PendingIntent.getActivity(c,1001,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);'''
s = s.replace(old_notif, new_notif)
cloud.write_text(s)

m = main.read_text()
old_result = '''        if(requestCode==201&&!pendingReceiptBooking.isEmpty()){db.addPaymentReceipt(pendingReceiptBooking,uri.toString());String id=pendingReceiptBooking;pendingReceiptBooking="";toast("Receipt uploaded. Payment is waiting for admin verification.");showBookingDetail(id,false);}'''
new_result = '''        if(requestCode==201&&!pendingReceiptBooking.isEmpty()){db.addPaymentReceipt(pendingReceiptBooking,uri.toString());String id=pendingReceiptBooking;BwdCloud.submitReceipt(this,id,uri);pendingReceiptBooking="";toast(BwdCloud.receiptUploadReady()?"Receipt queued for secure upload to Bali Wedding DJ.":"Receipt saved on this device. Cloud receipt sync is not configured yet.");showBookingDetail(id,false);}'''
m = m.replace(old_result,new_result)
m = m.replace('pm.optInt("verified")==1?"Verified":"Uploaded · Waiting for admin verification"', 'pm.optInt("verified")==1?"Verified":BwdCloud.receiptStatus(this,id)')
main.write_text(m)

f = service.read_text()
f = f.replace('BwdCloud.showNotification(this,title,body,message.getData().get("booking_id"));', 'BwdCloud.showNotification(this,title,body,message.getData().get("booking_id"),message.getData().get("receipt_url"));')
service.write_text(f)

print('BWD secure receipt cloud patch applied')
print('receipt_submit=', 'submitReceipt' in cloud.read_text())
print('receipt_notification_url=', 'receipt_url' in service.read_text())
