#!/usr/bin/env python3
import pathlib, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-admin')
main = root / 'app/src/main/java/com/baliweddingdj/app/MainActivity.java'
s = main.read_text()

if 'buildShell(); showHome();' not in s:
    raise SystemExit('owner launch anchor not found')
s = s.replace('buildShell(); showHome();', 'adminMode=true; buildShell(); showOwnerInbox();', 1)

start = s.find('    private void buildShell(){')
end = s.find('    private void addNav(', start)
if start < 0 or end < 0:
    raise SystemExit('buildShell block not found')
owner_shell = '''    private void buildShell(){\n        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);\n        TextView brand=Ui.text(this,"BALI WEDDING DJ OWNER",17,Ui.WARM,true);brand.setLetterSpacing(.10f);brand.setGravity(Gravity.CENTER_VERTICAL);brand.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),0);\n        root.addView(brand,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));\n        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));\n        bottom=null;\n        setContentView(root);\n    }\n'''
s = s[:start] + owner_shell + s[end:]

anchor = '    private void showHome(){'
pos = s.find(anchor)
if pos < 0:
    raise SystemExit('showHome anchor not found')
owner_inbox = '''    private void showOwnerInbox(){\n        LinearLayout p=page("Booking Dashboard","Incoming wedding requests and booking operations.");\n        LinearLayout stats=Ui.card(this);\n        addKeyValue(stats,"New Requests",String.valueOf(db.countBookings("status='REQUEST RECEIVED'",null)));\n        addKeyValue(stats,"Waiting Payments",String.valueOf(db.countBookings("status IN ('WAITING FOR DEPOSIT','DEPOSIT RECEIVED')",null)));\n        addKeyValue(stats,"Confirmed",String.valueOf(db.countBookings("status='BOOKING CONFIRMED'",null)));\n        p.addView(stats);\n        p.addView(Ui.section(this,"BOOKING INBOX"));\n        JSONArray a=db.bookings();\n        if(a.length()==0){\n            LinearLayout empty=Ui.card(this);empty.addView(Ui.text(this,"No booking requests yet.",14,Ui.MUTED,false));p.addView(empty);return;\n        }\n        for(int i=0;i<a.length();i++){\n            JSONObject b=a.optJSONObject(i);if(b==null)continue;\n            LinearLayout c=Ui.card(this);\n            c.addView(Ui.text(this,b.optString("booking_id"),13,Ui.GOLD,true));\n            c.addView(Ui.text(this,b.optString("bride")+" & "+b.optString("groom"),18,Ui.WARM,true));\n            c.addView(Ui.text(this,b.optString("wedding_date")+" · "+b.optString("venue_name"),13,Ui.MUTED,false));\n            c.addView(Ui.text(this,b.optString("status"),12,Ui.WARM,true));\n            String id=b.optString("booking_id");\n            c.addView(Ui.space(this,8));\n            c.addView(fullButton("OPEN BOOKING",true,()->showBookingDetail(id,true)));\n            p.addView(c);\n        }\n    }\n\n'''
s = s[:pos] + owner_inbox + s[pos:]

s = s.replace('JSONObject b=db.booking(id);if(b==null){showProfile();return;}',
              'JSONObject b=db.booking(id);if(b==null){if(admin)showOwnerInbox();else showProfile();return;}', 1)

admin_start = s.find('    private void showAdmin(){')
admin_end = s.find('    private void adminRoute(', admin_start)
if admin_start < 0 or admin_end < 0:
    raise SystemExit('showAdmin block not found')
s = s[:admin_start] + '    private void showAdmin(){showOwnerInbox();}\n' + s[admin_end:]

book_start = s.find('    private void showAdminBookings(){')
book_end = s.find('    private void showAdminBookingActions(', book_start)
if book_start < 0 or book_end < 0:
    raise SystemExit('showAdminBookings block not found')
s = s[:book_start] + '    private void showAdminBookings(){showOwnerInbox();}\n\n' + s[book_end:]

required = [
    'adminMode=true; buildShell(); showOwnerInbox();',
    'BALI WEDDING DJ OWNER',
    'Booking Dashboard',
    'BOOKING INBOX',
    'OPEN BOOKING',
    'private void showAdmin(){showOwnerInbox();}',
    'private void showAdminBookings(){showOwnerInbox();}',
]
for token in required:
    if token not in s:
        raise SystemExit('missing owner dashboard token: ' + token)

main.write_text(s)
print('BWD owner booking-dashboard-only patch applied')
