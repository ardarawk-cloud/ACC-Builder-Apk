#!/usr/bin/env python3
import pathlib, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-admin')
java_dir = root / 'app/src/main/java/com/baliweddingdj/app'
cloud_file = java_dir / 'BwdOwnerCloud.java'
main_file = java_dir / 'MainActivity.java'
gradle_file = root / 'app/build.gradle'

# Owner-only runtime baseline. These Firebase Android identifiers are public client
# configuration; server credentials remain Cloudflare secrets and are never embedded.
OWNER_RUNTIME = {
    'BWD_FIREBASE_API_KEY': 'AIzaSyCjxuIvMaHzS5wZoGGsmdP946nEgH9fPac',
    'BWD_FIREBASE_APP_ID': '1:636975525713:android:dd919e7631eba662032d70',
    'BWD_FIREBASE_PROJECT_ID': 'bali-wedding-dj',
    'BWD_FIREBASE_SENDER_ID': '636975525713',
    'BWD_CLOUD_BASE_URL': 'https://bali-wedding-dj-booking.ardarawk.workers.dev',
}

gradle = gradle_file.read_text().splitlines()
out = []
seen = set()
for line in gradle:
    replaced = False
    for key, value in OWNER_RUNTIME.items():
        if f'"{key}"' in line and 'buildConfigField' in line:
            indent = line[:len(line)-len(line.lstrip())]
            out.append(f'{indent}buildConfigField "String", "{key}", "\\"{value}\\""')
            seen.add(key)
            replaced = True
            break
    if not replaced:
        out.append(line)
missing = set(OWNER_RUNTIME) - seen
if missing:
    raise SystemExit('Owner runtime BuildConfig field(s) missing: ' + ', '.join(sorted(missing)))
gradle_file.write_text('\n'.join(out) + '\n')

cloud = cloud_file.read_text()

# Cloudflare now owns both the public API and Owner API. Remove the old Firebase
# Functions /api -> /ownerApi URL derivation and use the same Worker origin.
legacy_sync = '''                int slash=base.lastIndexOf('/');\n                if(slash<8)throw new IllegalStateException("invalid cloud url");\n                String ownerBase=base.substring(0,slash+1)+"ownerApi";\n                HttpURLConnection con=(HttpURLConnection)new URL(ownerBase+"/v1/admin/bookings").openConnection();'''
cloudflare_sync = '''                HttpURLConnection con=(HttpURLConnection)new URL(base+"/v1/admin/bookings").openConnection();'''
if legacy_sync in cloud:
    cloud = cloud.replace(legacy_sync, cloudflare_sync, 1)
elif 'new URL(base+"/v1/admin/bookings")' not in cloud:
    raise SystemExit('Owner Cloudflare inbox URL anchor not found')

if 'public static void updateStatus(' not in cloud:
    anchor = '    private static void finish(Runnable done){\n'
    method = r'''    public static void updateStatus(Context c,String bookingId,String status,Runnable done){
        if(!configured()){finish(done);return;}
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String token=p.getString("fcm_token","");
        if(token.length()<50){p.edit().putString("owner_sync_status","Owner device is not enrolled").apply();finish(done);return;}
        new Thread(()->{
            try{
                String base=BuildConfig.BWD_CLOUD_BASE_URL;
                while(base.endsWith("/"))base=base.substring(0,base.length()-1);
                String safeId=java.net.URLEncoder.encode(bookingId,"UTF-8");
                HttpURLConnection con=(HttpURLConnection)new URL(base+"/v1/admin/bookings/"+safeId+"/status").openConnection();
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
else:
    legacy_status = '''                int slash=base.lastIndexOf('/');\n                if(slash<8)throw new IllegalStateException("invalid cloud url");\n                String ownerBase=base.substring(0,slash+1)+"ownerApi";\n                String safeId=java.net.URLEncoder.encode(bookingId,"UTF-8");\n                HttpURLConnection con=(HttpURLConnection)new URL(ownerBase+"/v1/admin/bookings/"+safeId+"/status").openConnection();'''
    cloudflare_status = '''                String safeId=java.net.URLEncoder.encode(bookingId,"UTF-8");\n                HttpURLConnection con=(HttpURLConnection)new URL(base+"/v1/admin/bookings/"+safeId+"/status").openConnection();'''
    if legacy_status in cloud:
        cloud = cloud.replace(legacy_status, cloudflare_status, 1)

cloud_file.write_text(cloud)

main = main_file.read_text()
old = 'db.setBookingStatus(id,s[w]);'
new = 'db.setBookingStatus(id,s[w]);BwdOwnerCloud.updateStatus(this,id,s[w],null);'
if new not in main:
    if old not in main:
        raise SystemExit('Owner changeStatus anchor not found')
    main = main.replace(old, new, 1)
    main_file.write_text(main)

checks = cloud_file.read_text() + main_file.read_text() + gradle_file.read_text()
for token in [
    'public static void updateStatus(',
    'X-BWD-Admin-Device',
    'BwdOwnerCloud.updateStatus(this,id,s[w],null)',
    'new URL(base+"/v1/admin/bookings")',
    'bali-wedding-dj-booking.ardarawk.workers.dev',
    '1:636975525713:android:dd919e7631eba662032d70',
]:
    if token not in checks:
        raise SystemExit('missing Owner Cloudflare token: ' + token)

if 'ownerApi' in cloud_file.read_text():
    raise SystemExit('legacy Firebase ownerApi URL remains in Owner cloud client')

print('BWD Owner Cloudflare runtime + status write-back patch applied')
print('owner_firebase_app_id_configured=', True)
print('backend_configured=', True)
