#!/usr/bin/env python3
import pathlib, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-admin')
java_dir = root / 'app/src/main/java/com/baliweddingdj/app'
cloud_file = java_dir / 'BwdOwnerCloud.java'
main_file = java_dir / 'MainActivity.java'

cloud = cloud_file.read_text()
if 'public static void updateStatus(' not in cloud:
    anchor = '    private static void finish(Runnable done){\n'
    method = r'''    public static void updateStatus(Context c,String bookingId,String status,Runnable done){
        if(!configured()){finish(done);return;}
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String token=p.getString("fcm_token","");
        if(token.length()<50){p.edit().putString("owner_sync_status","Owner device is not enrolled").apply();finish(done);return;}
        Context app=c.getApplicationContext();
        new Thread(()->{
            try{
                String base=BuildConfig.BWD_CLOUD_BASE_URL;
                while(base.endsWith("/"))base=base.substring(0,base.length()-1);
                int slash=base.lastIndexOf('/');
                if(slash<8)throw new IllegalStateException("invalid cloud url");
                String ownerBase=base.substring(0,slash+1)+"ownerApi";
                String safeId=java.net.URLEncoder.encode(bookingId,"UTF-8");
                HttpURLConnection con=(HttpURLConnection)new URL(ownerBase+"/v1/admin/bookings/"+safeId+"/status").openConnection();
                con.setConnectTimeout(12000);con.setReadTimeout(15000);con.setRequestMethod("POST");con.setDoOutput(true);
                con.setRequestProperty("Accept","application/json");
                con.setRequestProperty("Content-Type","application/json; charset=utf-8");
                con.setRequestProperty("X-BWD-Admin-Device",token);
                byte[] payload=new JSONObject().put("status",status).toString().getBytes(StandardCharsets.UTF_8);
                try(java.io.OutputStream out=con.getOutputStream()){out.write(payload);}
                int code=con.getResponseCode();con.disconnect();
                if(code>=200&&code<300)p.edit().putString("owner_sync_status","Booking status synced to guest portal").apply();
                else p.edit().putString("owner_sync_status","Status cloud sync failed ("+code+")").apply();
            }catch(Exception e){
                p.edit().putString("owner_sync_status","Status saved locally; cloud sync pending").apply();
            }finally{finish(done);}
        },"bwd-owner-status-sync").start();
    }

'''
    if anchor not in cloud:
        raise SystemExit('BwdOwnerCloud finish anchor not found')
    cloud = cloud.replace(anchor, method + anchor, 1)
    cloud_file.write_text(cloud)

main = main_file.read_text()
old = 'db.setBookingStatus(id,s[w]);'
new = 'db.setBookingStatus(id,s[w]);BwdOwnerCloud.updateStatus(this,id,s[w],null);'
if new not in main:
    if old not in main:
        raise SystemExit('Owner changeStatus anchor not found')
    main = main.replace(old, new, 1)
    main_file.write_text(main)

checks = cloud_file.read_text() + main_file.read_text()
for token in ['public static void updateStatus(', 'X-BWD-Admin-Device', 'BwdOwnerCloud.updateStatus(this,id,s[w],null)']:
    if token not in checks:
        raise SystemExit('missing Owner status cloud token: ' + token)

print('BWD Owner status cloud write-back patch applied')
