#!/usr/bin/env python3
import os, pathlib, re, shutil, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-native')
repo = pathlib.Path(__file__).resolve().parents[1]
app = root / 'app'
java = app / 'src/main/java/com/baliweddingdj/app'
main = java / 'MainActivity.java'
dbfile = java / 'WeddingDb.java'
gradle = app / 'build.gradle'
manifest = app / 'src/main/AndroidManifest.xml'

def env(name): return os.environ.get(name,'').replace('\\','\\\\').replace('"','\\"')
def template(name): return repo / 'native/bwd-master-v1' / name

# Android build config
g = gradle.read_text()
g = re.sub(r'compileSdk\s+\d+', 'compileSdk 36', g)
g = re.sub(r'targetSdk\s+\d+', 'targetSdk 36', g)
g = re.sub(r'versionCode\s+\d+', 'versionCode 10', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '1.0.0-rc1'", g)
fields = ("\n"
    + '        buildConfigField "String", "BWD_FIREBASE_API_KEY", "\\"' + env('BWD_FIREBASE_API_KEY') + '\\""\n'
    + '        buildConfigField "String", "BWD_FIREBASE_APP_ID", "\\"' + env('BWD_FIREBASE_APP_ID') + '\\""\n'
    + '        buildConfigField "String", "BWD_FIREBASE_OWNER_APP_ID", "\\"' + env('BWD_FIREBASE_OWNER_APP_ID') + '\\""\n'
    + '        buildConfigField "String", "BWD_FIREBASE_PROJECT_ID", "\\"' + env('BWD_FIREBASE_PROJECT_ID') + '\\""\n'
    + '        buildConfigField "String", "BWD_FIREBASE_SENDER_ID", "\\"' + env('BWD_FIREBASE_SENDER_ID') + '\\""\n'
    + '        buildConfigField "String", "BWD_CLOUD_BASE_URL", "\\"' + env('BWD_CLOUD_BASE_URL') + '\\""\n')
if 'BWD_FIREBASE_API_KEY' not in g:
    g = g.replace("        versionName '1.0.0-rc1'\n", "        versionName '1.0.0-rc1'\n" + fields)
if 'buildFeatures' not in g:
    g = g.replace('\n    buildTypes {','\n    buildFeatures {\n        buildConfig true\n    }\n\n    buildTypes {')
if 'firebase-bom' not in g:
    g += "\n\ndependencies {\n    implementation platform('com.google.firebase:firebase-bom:34.18.0')\n    implementation 'com.google.firebase:firebase-messaging'\n}\n"
gradle.write_text(g)

m = manifest.read_text()
service = '''\n        <service\n            android:name=".BwdFirebaseMessagingService"\n            android:exported="false">\n            <intent-filter>\n                <action android:name="com.google.firebase.MESSAGING_EVENT" />\n            </intent-filter>\n        </service>'''
if 'BwdFirebaseMessagingService' not in m:
    m = m.replace('        <activity android:name=".MainActivity"', service+'\n        <activity android:name=".MainActivity"')
manifest.write_text(m)

shutil.copyfile(template('BwdCloud.java'), java/'BwdCloud.java')
shutil.copyfile(template('BwdFirebaseMessagingService.java'), java/'BwdFirebaseMessagingService.java')

# SQLite offline cache hydration
d = dbfile.read_text()
if 'public void applyCloudBooking(JSONObject b)' not in d:
    inject = r'''
    public void applyCloudBookings(JSONArray rows){ if(rows==null)return; for(int i=0;i<rows.length();i++){JSONObject b=rows.optJSONObject(i);if(b!=null)applyCloudBooking(b);} }
    public void applyCloudBooking(JSONObject b){
        if(b==null)return;String bookingId=b.optString("booking_id","");if(bookingId.isEmpty())return;
        ContentValues v=new ContentValues();v.put("booking_id",bookingId);v.put("created_at",b.optString("created_at",now()));
        String[] fields={"bride","groom","email","whatsapp","wedding_date","venue_name","venue_location","planner","package_name","sections","start_time","finish_time","music_pref","favorite_songs","must_play","do_not_play","special_requests","status","admin_notes","timeline","music_plan"};
        for(String f:fields)v.put(f,b.optString(f,""));v.put("guests",b.optInt("guests",0));
        getWritableDatabase().insertWithOnConflict("bookings",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        JSONObject q=b.optJSONObject("quote");if(q!=null){ContentValues x=new ContentValues();x.put("booking_id",bookingId);x.put("quote_no",q.optString("quote_no"));x.put("additional_services",q.optString("additional_services"));x.put("base_price",q.optLong("base_price"));x.put("addons",q.optLong("addons"));x.put("discount",q.optLong("discount"));x.put("total",q.optLong("total"));x.put("deposit_percent",q.optInt("deposit_percent",50));x.put("deposit_required",q.optLong("deposit_required"));x.put("balance",q.optLong("balance"));x.put("due_date",q.optString("due_date"));x.put("notes",q.optString("notes"));x.put("terms",q.optString("terms"));x.put("accepted",q.optBoolean("accepted",false)?1:0);x.put("created_at",q.optString("updated_at",now()));getWritableDatabase().insertWithOnConflict("quotes",null,x,SQLiteDatabase.CONFLICT_REPLACE);if(q.has("bank_account"))setSetting("bank_account",q.optString("bank_account"));if(q.has("payment_instructions"))setSetting("payment_instructions",q.optString("payment_instructions"));}
        JSONObject inv=b.optJSONObject("invoice");if(inv!=null){ContentValues x=new ContentValues();x.put("booking_id",bookingId);x.put("invoice_no",inv.optString("invoice_no"));x.put("total",inv.optLong("total"));x.put("deposit_required",inv.optLong("deposit_required"));x.put("balance",inv.optLong("balance"));x.put("status",inv.optString("status","UNPAID"));x.put("due_date",inv.optString("due_date"));x.put("created_at",inv.optString("updated_at",now()));getWritableDatabase().insertWithOnConflict("invoices",null,x,SQLiteDatabase.CONFLICT_REPLACE);}
        JSONObject pay=b.optJSONObject("payment");if(pay!=null){getWritableDatabase().delete("payments","booking_id=?",new String[]{bookingId});ContentValues x=new ContentValues();x.put("booking_id",bookingId);x.put("receipt_uri","");x.put("amount",pay.optLong("amount"));x.put("verified",pay.optBoolean("verified",false)?1:0);x.put("status",pay.optString("status"));x.put("created_at",pay.optString("uploaded_at",now()));getWritableDatabase().insert("payments",null,x);}
    }
'''
    d = d[:d.rfind('}')] + inject + d[d.rfind('}'):]
    dbfile.write_text(d)

# Main UI/operations
s = main.read_text()
if 'import android.Manifest;' not in s:
    s = s.replace('package com.baliweddingdj.app;\n\n','package com.baliweddingdj.app;\n\nimport android.Manifest;\n')
if 'private void requestPushPermission()' not in s:
    s = s.replace('    private void buildShell(){','    private void requestPushPermission(){if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9001);}\n\n    private void buildShell(){')
s = s.replace('        db=new WeddingDb(this); prefs=getSharedPreferences("bwd_secure",MODE_PRIVATE);\n        buildShell(); showHome();','        db=new WeddingDb(this); prefs=getSharedPreferences("bwd_secure",MODE_PRIVATE);\n        BwdCloud.init(this); requestPushPermission();\n        buildShell(); showHome();')
s = s.replace('String id=db.addBooking(b);clearDraft();showBookingSuccess(id);','String id=db.addBooking(b);BwdCloud.submitBooking(this,b,id);clearDraft();showBookingSuccess(id);')
s = s.replace('if(requestCode==201&&!pendingReceiptBooking.isEmpty()){db.addPaymentReceipt(pendingReceiptBooking,uri.toString());String id=pendingReceiptBooking;pendingReceiptBooking="";toast("Receipt uploaded. Payment is waiting for admin verification.");showBookingDetail(id,false);}', 'if(requestCode==201&&!pendingReceiptBooking.isEmpty()){db.addPaymentReceipt(pendingReceiptBooking,uri.toString());String id=pendingReceiptBooking;BwdCloud.submitReceipt(this,id,uri);pendingReceiptBooking="";toast(BwdCloud.receiptUploadReady()?"Receipt queued for secure upload to Bali Wedding DJ.":"Receipt saved on this device. Cloud receipt sync is not configured yet.");showBookingDetail(id,false);}')
s = s.replace('pm.optInt("verified")==1?"Verified":"Uploaded · Waiting for admin verification"','pm.optInt("verified")==1?"Verified":BwdCloud.receiptStatus(this,id)')
if 'private boolean isOwnerBuild()' not in s:
    s = s.replace('    private void buildShell(){','    private boolean isOwnerBuild(){return BwdCloud.isOwner(this);}\n\n    @Override protected void onResume(){super.onResume();if(db!=null){if(isOwnerBuild())BwdCloud.syncOwner(this,db,null);else BwdCloud.syncClientBookings(this,db,null);}}\n\n    private void buildShell(){')
s = s.replace('        buildShell(); showHome();','        buildShell(); if(isOwnerBuild()){adminMode=true;showAdmin();BwdCloud.syncOwner(this,db,null);}else{showHome();BwdCloud.syncClientBookings(this,db,null);}')
s = s.replace('        addNav("HOME",this::showHome);addNav("PACKAGES",this::showPackages);addNav("BOOK MY WEDDING",()->{bookingStep=1;showBooking();});addNav("PROFILE",this::showProfile);','        if(isOwnerBuild()){addNav("DASHBOARD",this::showAdmin);addNav("BOOKINGS",this::showAdminBookings);addNav("PAYMENTS",this::showAdminPayments);addNav("NOTIFICATIONS",this::showAdminNotifications);addNav("SETTINGS",this::showAdminSettings);}\n        else{addNav("HOME",this::showHome);addNav("PACKAGES",this::showPackages);addNav("MY BOOKING",this::showProfile);addNav("PROFILE",this::showProfile);}')
s = s.replace('LinearLayout p=page("Profile & My Booking","Browse freely. Account creation is only required when cloud Email + OTP is activated for production sync.");','LinearLayout p=page("Profile & My Booking","Your booking is cached on this phone and securely synchronized when cloud is available.");p.addView(fullButton("SYNC MY BOOKING",false,()->BwdCloud.syncClientBookings(this,db,this::showProfile)));p.addView(Ui.space(this,8));')
s = s.replace('Production authentication: Email + OTP','Secure booking access')
s = s.replace('This native foundation keeps customer data locally on the device until the secure cloud backend is provisioned. No API secret or admin credential is embedded in this APK.','Each booking uses a private device key. Booking updates, quotation, payment and timeline sync through Bali Wedding DJ Cloud when connected.')
s = s.replace('db.acceptQuote(id);showBookingDetail(id,false);','db.acceptQuote(id);BwdCloud.clientAcceptQuote(this,db,id,()->showBookingDetail(id,false));')
s = s.replace('db.setMusicPlan(id,s.toString().trim());showBookingDetail(id,false);','String plan=s.toString().trim();db.setMusicPlan(id,plan);BwdCloud.clientMusic(this,db,id,plan,()->showBookingDetail(id,false));')
s = s.replace('db.setTimeline(id,e.getText().toString().trim());showBookingDetail(id,admin);','String t=e.getText().toString().trim();db.setTimeline(id,t);if(admin)BwdCloud.adminTimeline(this,db,id,t,()->showBookingDetail(id,true));else BwdCloud.clientTimeline(this,db,id,t,()->showBookingDetail(id,false));')
s = s.replace('        p.addView(Ui.section(this,"PAYMENT"));LinearLayout pay=Ui.card(this);','        p.addView(Ui.section(this,"PAYMENT"));LinearLayout pay=Ui.card(this);String bankInfo=db.setting("bank_account","");String payInfo=db.setting("payment_instructions","");if(!bankInfo.isEmpty())addKeyValue(pay,"Bank Transfer",bankInfo);if(!payInfo.isEmpty())addKeyValue(pay,"Payment Instructions",payInfo);')
s = s.replace('if(!adminMode){showAdminAuth();return;}LinearLayout p=page("Admin Dashboard","Bali Wedding DJ operations · device foundation");','if(!adminMode&&!isOwnerBuild()){showAdminAuth();return;}LinearLayout p=page("Owner Dashboard","Bali Wedding DJ operations · cloud booking system");')
if 'SYNC CLOUD NOW' not in s:
    s = s.replace('        LinearLayout stats=Ui.card(this);','        LinearLayout cloud=Ui.card(this);cloud.addView(Ui.text(this,"CLOUD STATUS",11,Ui.GOLD,true));cloud.addView(Ui.text(this,BwdCloud.statusText(this),13,Ui.MUTED,false));cloud.addView(Ui.space(this,8));cloud.addView(fullButton(BwdCloud.adminReady(this)?"RECONNECT OWNER CLOUD":"CONNECT OWNER CLOUD",false,()->BwdCloud.showAdminEnrollment(this)));cloud.addView(Ui.space(this,8));cloud.addView(fullButton("SYNC CLOUD NOW",true,()->BwdCloud.syncOwner(this,db,this::showAdmin)));p.addView(cloud);\n        LinearLayout stats=Ui.card(this);')
s = s.replace('        p.addView(Ui.section(this,"MANAGEMENT"));String[][] actions={{"MANAGE BOOKINGS","bookings"},{"MANAGE CALENDAR","calendar"},{"MANAGE PACKAGES","packages"},{"PAYMENT SETTINGS","settings"},{"GALLERY","gallery"},{"REVIEWS","reviews"},{"ADMIN NOTIFICATIONS","notifications"}};for(String[] a:actions){p.addView(fullButton(a[0],false,()->adminRoute(a[1])));p.addView(Ui.space(this,8));}p.addView(fullButton("LOCK ADMIN",true,()->{adminMode=false;showProfile();}));','        p.addView(Ui.section(this,"OPERATIONS"));p.addView(fullButton("OPEN BOOKINGS",false,this::showAdminBookings));p.addView(Ui.space(this,8));p.addView(fullButton("OPEN PAYMENTS",false,this::showAdminPayments));p.addView(Ui.space(this,8));p.addView(fullButton("AVAILABILITY CALENDAR",false,this::showAdminCalendar));p.addView(Ui.space(this,8));p.addView(fullButton("PACKAGES",false,this::showAdminPackages));')
s = s.replace('private void showAdminBookings(){LinearLayout p=page("Manage Bookings","Review availability, quotation, payment and event preparation.");','private void showAdminBookings(){LinearLayout p=page("Bookings","Review availability, quotation, payment and event preparation.");p.addView(fullButton("SYNC CLOUD NOW",true,()->BwdCloud.syncOwner(this,db,this::showAdminBookings)));p.addView(Ui.space(this,8));')
if 'private void showAdminPayments()' not in s:
    method='''    private void showAdminPayments(){LinearLayout p=page("Payments","Receipts awaiting verification and verified deposits.");p.addView(fullButton("SYNC CLOUD NOW",true,()->BwdCloud.syncOwner(this,db,this::showAdminPayments)));JSONArray a=db.bookings();boolean any=false;for(int i=0;i<a.length();i++){JSONObject b=a.optJSONObject(i);if(b==null)continue;String id=b.optString("booking_id");JSONObject pay=db.latestPayment(id);if(pay==null)continue;any=true;LinearLayout c=Ui.card(this);c.addView(Ui.text(this,id,14,Ui.GOLD,true));c.addView(Ui.text(this,b.optString("bride")+" & "+b.optString("groom"),16,Ui.WARM,true));c.addView(Ui.text(this,b.optString("wedding_date")+" · "+pay.optString("status"),13,Ui.MUTED,false));c.addView(Ui.space(this,8));c.addView(fullButton("VIEW RECEIPT",false,()->BwdCloud.viewAdminReceipt(this,id)));c.addView(Ui.space(this,8));c.addView(fullButton("OPEN BOOKING",false,()->showBookingDetail(id,true)));p.addView(c);}if(!any)p.addView(Ui.text(this,"No payment receipts yet.",14,Ui.MUTED,false));}\n\n'''
    s=s.replace('    private void showAdminBookingActions(LinearLayout p,JSONObject b){',method+'    private void showAdminBookingActions(LinearLayout p,JSONObject b){')
s=s.replace('JSONObject pay=db.latestPayment(id);if(pay!=null&&pay.optInt("verified")==0){p.addView(fullButton("VERIFY PAYMENT",true,()->verifyPaymentDialog(id)));p.addView(Ui.space(this,8));}','JSONObject pay=db.latestPayment(id);if(pay!=null){p.addView(fullButton("VIEW PAYMENT RECEIPT",false,()->BwdCloud.viewAdminReceipt(this,id)));p.addView(Ui.space(this,8));if(pay.optInt("verified")==0){p.addView(fullButton("VERIFY PAYMENT",true,()->verifyPaymentDialog(id)));p.addView(Ui.space(this,8));}}')
s=s.replace('db.setBookingStatus(id,s[w]);if("AVAILABLE".equals(s[w])){JSONObject b=db.booking(id);if(b!=null)db.setAvailability(b.optString("wedding_date"),"PENDING");}if("BOOKING CONFIRMED".equals(s[w])){JSONObject b=db.booking(id);if(b!=null)db.setAvailability(b.optString("wedding_date"),"BOOKED");}showBookingDetail(id,true);','db.setBookingStatus(id,s[w]);if("AVAILABLE".equals(s[w])){JSONObject b=db.booking(id);if(b!=null)db.setAvailability(b.optString("wedding_date"),"PENDING");}if("BOOKING CONFIRMED".equals(s[w])){JSONObject b=db.booking(id);if(b!=null)db.setAvailability(b.optString("wedding_date"),"BOOKED");}BwdCloud.adminStatus(this,db,id,s[w],()->showBookingDetail(id,true));')
s=s.replace('db.saveQuote(id,add.getText().toString(),parseMoney(base),parseMoney(addons),parseMoney(disc),dpct,due.getText().toString(),notes.getText().toString(),terms.getText().toString());showBookingDetail(id,true);','db.saveQuote(id,add.getText().toString(),parseMoney(base),parseMoney(addons),parseMoney(disc),dpct,due.getText().toString(),notes.getText().toString(),terms.getText().toString());try{JSONObject cq=new JSONObject();cq.put("additional_services",add.getText().toString());cq.put("base_price",parseMoney(base));cq.put("addons",parseMoney(addons));cq.put("discount",parseMoney(disc));cq.put("deposit_percent",dpct);cq.put("due_date",due.getText().toString());cq.put("notes",notes.getText().toString());cq.put("terms",terms.getText().toString());cq.put("bank_account",db.setting("bank_account",""));cq.put("payment_instructions",db.setting("payment_instructions",""));BwdCloud.adminQuote(this,db,id,cq,()->showBookingDetail(id,true));}catch(Exception ex){showBookingDetail(id,true);}')
s=s.replace('db.verifyPayment(id,parseMoney(amount),full.isChecked());showBookingDetail(id,true);','long verified=parseMoney(amount);db.verifyPayment(id,verified,full.isChecked());BwdCloud.adminVerifyPayment(this,db,id,verified,full.isChecked(),()->showBookingDetail(id,true));')
s=s.replace('db.setAdminNotes(id,e.getText().toString());showBookingDetail(id,true);','String notes=e.getText().toString();db.setAdminNotes(id,notes);BwdCloud.adminNotes(this,db,id,notes,()->showBookingDetail(id,true));')
s=s.replace('private void showAdminSettings(){LinearLayout p=page("Payment & Contact Settings","These values are stored in the app database and can be updated without an APK rebuild.");','private void showAdminSettings(){LinearLayout p=page("Owner Settings","Business contact, payment instructions and owner cloud connection.");p.addView(fullButton("CONNECT / RECONNECT OWNER CLOUD",false,()->BwdCloud.showAdminEnrollment(this)));p.addView(Ui.space(this,10));')
main.write_text(s)

print('BWD master v1 patch applied')
print('owner_nav=', 'DASHBOARD' in s and 'showAdminPayments' in s)
print('client_sync=', 'syncClientBookings' in s)
print('cloud_runtime=', (java/'BwdCloud.java').exists())
