#!/usr/bin/env python3
import pathlib, sys

root=pathlib.Path(sys.argv[1] if len(sys.argv)>1 else 'bwd-native')
java=root/'app/src/main/java/com/baliweddingdj/app'
main=java/'MainActivity.java'
db=java/'WeddingDb.java'

s=main.read_text()
s=s.replace(
    'buildShell(); if(isOwnerBuild()){adminMode=true;showAdmin();BwdCloud.syncOwner(this,db,null);}else{showHome();BwdCloud.syncClientBookings(this,db,null);}',
    'buildShell(); String openId=getIntent()!=null?getIntent().getStringExtra("booking_id"):null;if(isOwnerBuild()){adminMode=true;showAdmin();BwdCloud.syncOwner(this,db,()->{if(openId!=null&&!openId.isEmpty())showBookingDetail(openId,true);});}else{showHome();BwdCloud.syncClientBookings(this,db,()->{if(openId!=null&&!openId.isEmpty())showBookingDetail(openId,false);});}'
)
s=s.replace(
    'c.drawText("Payment instructions: "+db.setting("payment_instructions","Contact Bali Wedding DJ for bank details."),45,y,p);y+=24;c.drawText("WhatsApp: +62 822-4797-2288",45,y,p);',
    'c.drawText("Bank account: "+db.setting("bank_account","Contact Bali Wedding DJ for bank details."),45,y,p);y+=20;c.drawText("Payment instructions: "+db.setting("payment_instructions","Contact Bali Wedding DJ for bank details."),45,y,p);y+=24;c.drawText("WhatsApp: +62 822-4797-2288",45,y,p);'
)
main.write_text(s)

d=db.read_text()
d=d.replace(
    'x.put("accepted",q.optBoolean("accepted",false)?1:0);',
    'String cloudStatus=b.optString("status","");boolean cloudAccepted=q.optBoolean("accepted",false)||"WAITING FOR DEPOSIT".equals(cloudStatus)||"DEPOSIT RECEIVED".equals(cloudStatus)||"BOOKING CONFIRMED".equals(cloudStatus)||"EVENT PREPARATION".equals(cloudStatus)||"COMPLETED".equals(cloudStatus);x.put("accepted",cloudAccepted?1:0);'
)
db.write_text(d)

print('BWD release fixups applied')
print('notification_deeplink=', 'String openId=' in s)
print('invoice_bank=', 'Bank account:' in s)
print('quote_accept_guard=', 'cloudAccepted' in d)
