#!/usr/bin/env python3
import os, pathlib, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-admin')
java_dir = root / 'app/src/main/java/com/baliweddingdj/app'
gradle = root / 'app/build.gradle'
main = java_dir / 'MainActivity.java'
db_file = java_dir / 'WeddingDb.java'
service = java_dir / 'BwdFirebaseMessagingService.java'

# Owner must use its own Firebase Android app id when production config is supplied.
owner_app_id = os.environ.get('BWD_FIREBASE_OWNER_APP_ID', '').strip()
if owner_app_id and gradle.exists():
    lines = gradle.read_text().splitlines()
    out = []
    changed = False
    for line in lines:
        if '"BWD_FIREBASE_APP_ID"' in line and 'buildConfigField' in line:
            indent = line[:len(line)-len(line.lstrip())]
            out.append(f'{indent}buildConfigField "String", "BWD_FIREBASE_APP_ID", "\\"{owner_app_id}\\""')
            changed = True
        else:
            out.append(line)
    if not changed:
        raise SystemExit('owner Firebase app-id field not found')
    gradle.write_text('\n'.join(out) + '\n')

# Add idempotent cloud-booking upsert to the local DB used by Owner dashboard/details.
d = db_file.read_text()
anchor = '    public JSONArray bookings(){ return queryArray("SELECT * FROM bookings ORDER BY id DESC",null); }\n'
upsert = r'''    public void upsertCloudBooking(JSONObject b){
        String bookingId=b.optString("booking_id","").trim(); if(bookingId.isEmpty())return;
        JSONObject old=booking(bookingId); boolean exists=old!=null;
        ContentValues v=new ContentValues();
        v.put("booking_id",bookingId);
        String created=b.optString("created_at","");v.put("created_at",created.isEmpty()?now():created);
        String[] fields={"bride","groom","email","whatsapp","wedding_date","venue_name","venue_location","planner","package_name","sections","start_time","finish_time","music_pref","favorite_songs","must_play","do_not_play","special_requests"};
        for(String f:fields)v.put(f,b.optString(f,""));
        v.put("guests",b.optInt("guests",0));
        String cloudStatus=b.optString("status","REQUEST RECEIVED");
        if(!exists || old.optString("status","").isEmpty() || "REQUEST RECEIVED".equals(old.optString("status")))v.put("status",cloudStatus);
        if(!exists){
            v.put("admin_notes","");v.put("timeline","");v.put("music_plan","");
            getWritableDatabase().insertWithOnConflict("bookings",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }else{
            getWritableDatabase().update("bookings",v,"booking_id=?",new String[]{bookingId});
        }
    }

'''
if 'public void upsertCloudBooking(JSONObject b)' not in d:
    if anchor not in d:
        raise SystemExit('WeddingDb bookings anchor not found')
    d = d.replace(anchor, upsert + anchor, 1)
db_file.write_text(d)

# Owner-only cloud inbox synchronizer. It never embeds admin enrollment credentials.
(java_dir / 'BwdOwnerCloud.java').write_text(r'''package com.baliweddingdj.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BwdOwnerCloud {
    private static final String PREF="bwd_cloud";
    private static final AtomicBoolean syncing=new AtomicBoolean(false);
    private BwdOwnerCloud(){}

    public static boolean configured(){
        return "com.baliweddingdj.owner".equals(BuildConfig.APPLICATION_ID)
            && BuildConfig.BWD_CLOUD_BASE_URL!=null
            && BuildConfig.BWD_CLOUD_BASE_URL.startsWith("https://");
    }

    public static String status(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(!configured())return "Cloud API not configured";
        if(p.getString("fcm_token","").length()<50)return "Waiting for Owner notification token";
        return p.getString("owner_sync_status","Ready to sync booking inbox");
    }

    public static void sync(Context c,WeddingDb db,Runnable done){
        if(!configured()){finish(done);return;}
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String token=p.getString("fcm_token","");
        if(token.length()<50){p.edit().putString("owner_sync_status","Waiting for Owner notification token").apply();finish(done);return;}
        if(!syncing.compareAndSet(false,true)){return;}
        Context app=c.getApplicationContext();
        new Thread(()->{
            try{
                String base=BuildConfig.BWD_CLOUD_BASE_URL;
                while(base.endsWith("/"))base=base.substring(0,base.length()-1);
                int slash=base.lastIndexOf('/');
                if(slash<8)throw new IllegalStateException("invalid cloud url");
                String ownerBase=base.substring(0,slash+1)+"ownerApi";
                HttpURLConnection con=(HttpURLConnection)new URL(ownerBase+"/v1/admin/bookings").openConnection();
                con.setConnectTimeout(12000);con.setReadTimeout(15000);con.setRequestMethod("GET");
                con.setRequestProperty("Accept","application/json");
                con.setRequestProperty("X-BWD-Admin-Device",token);
                int code=con.getResponseCode();
                InputStream in=code>=400?con.getErrorStream():con.getInputStream();
                StringBuilder body=new StringBuilder();
                if(in!=null){try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)body.append(line);}}
                con.disconnect();
                if(code>=200&&code<300){
                    JSONArray a=new JSONObject(body.toString()).optJSONArray("bookings");
                    int n=0;
                    if(a!=null)for(int i=0;i<a.length();i++){JSONObject b=a.optJSONObject(i);if(b!=null){db.upsertCloudBooking(b);n++;}}
                    p.edit().putString("owner_sync_status","Synced "+n+" booking(s)").putLong("owner_sync_at",System.currentTimeMillis()).apply();
                }else if(code==401){
                    p.edit().putString("owner_sync_status","Owner device not enrolled for cloud inbox").apply();
                }else{
                    p.edit().putString("owner_sync_status","Cloud inbox sync failed ("+code+")").apply();
                }
            }catch(Exception e){
                p.edit().putString("owner_sync_status","Cloud inbox unreachable").apply();
            }finally{
                syncing.set(false);finish(done);
            }
        },"bwd-owner-inbox-sync").start();
    }

    private static void finish(Runnable done){
        if(done!=null)new Handler(Looper.getMainLooper()).post(done);
    }
}
''')

# Sync after Owner launch and whenever Owner resumes.
m = main.read_text()
launch = 'adminMode=true; buildShell(); showOwnerInbox();'
if launch not in m:
    raise SystemExit('owner launch anchor not found')
if 'BwdOwnerCloud.sync(this,db,this::showOwnerInbox);' not in m:
    m = m.replace(launch, launch + ' BwdOwnerCloud.sync(this,db,this::showOwnerInbox);', 1)
resume_anchor = '    private void buildShell(){'
resume = '''    @Override protected void onResume(){\n        super.onResume();\n        if(db!=null && adminMode)BwdOwnerCloud.sync(this,db,()->{if(adminMode)showOwnerInbox();});\n    }\n\n'''
if '@Override protected void onResume()' not in m:
    if resume_anchor not in m:
        raise SystemExit('owner buildShell anchor not found')
    m = m.replace(resume_anchor, resume + resume_anchor, 1)
main.write_text(m)

# Token creation and incoming push both trigger an Owner inbox refresh in the background.
s = service.read_text()
s = s.replace('BwdCloud.saveToken(this,token);', 'BwdCloud.saveToken(this,token);BwdOwnerCloud.sync(this,new WeddingDb(this),null);')
notify = 'BwdCloud.showNotification(this,title,body,message.getData().get("booking_id"),message.getData().get("receipt_url"));'
if notify in s and 'BwdOwnerCloud.sync(this,new WeddingDb(this),null);' not in s.split(notify,1)[1][:120]:
    s = s.replace(notify, notify + 'BwdOwnerCloud.sync(this,new WeddingDb(this),null);', 1)
service.write_text(s)

# Initial Firebase token retrieval can complete after MainActivity.onResume, so sync there too.
c = java_dir / 'BwdCloud.java'
cloud = c.read_text()
token_line = 'context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("fcm_token",token).apply();'
if token_line in cloud and 'BwdOwnerCloud.sync(context,new WeddingDb(context),null);' not in cloud:
    cloud = cloud.replace(token_line, token_line + 'BwdOwnerCloud.sync(context,new WeddingDb(context),null);', 1)
c.write_text(cloud)

required = [
    'class BwdOwnerCloud',
    'upsertCloudBooking',
    'X-BWD-Admin-Device',
    'BwdOwnerCloud.sync(this,db,this::showOwnerInbox)',
]
all_text = (java_dir/'BwdOwnerCloud.java').read_text() + db_file.read_text() + main.read_text()
for token in required:
    if token not in all_text:
        raise SystemExit('missing owner cloud inbox token: '+token)

print('BWD Owner cloud inbox sync patch applied')
print('owner_firebase_app_id_configured=', bool(owner_app_id))
