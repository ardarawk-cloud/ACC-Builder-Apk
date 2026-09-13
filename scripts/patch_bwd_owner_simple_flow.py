#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-admin')
java_dir = root / 'app/src/main/java/com/baliweddingdj/app'
main_file = java_dir / 'MainActivity.java'
cloud_file = java_dir / 'BwdOwnerCloud.java'
db_file = java_dir / 'WeddingDb.java'

main = main_file.read_text()
cloud = cloud_file.read_text()
db = db_file.read_text()

# Cloud is the source of truth for the four-step flow, including client payment submission.
old_status_merge = 'if(!exists || old.optString("status","").isEmpty() || "REQUEST RECEIVED".equals(old.optString("status")))v.put("status",cloudStatus);'
if old_status_merge in db:
    db = db.replace(old_status_merge, 'v.put("status",cloudStatus);', 1)
elif 'v.put("status",cloudStatus);' not in db:
    raise SystemExit('cloud status merge anchor not found')
db_file.write_text(db)

# Add two small Owner cloud actions: send invoice and confirm payment.
anchor = '    private static void finish(Runnable done){\n'
if anchor not in cloud:
    raise SystemExit('BwdOwnerCloud finish anchor not found')
methods = r'''    private static int postJson(Context c,String path,JSONObject payload)throws Exception{
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String token=p.getString("fcm_token","");
        if(token.length()<50)return 401;
        String base=BuildConfig.BWD_CLOUD_BASE_URL;
        while(base.endsWith("/"))base=base.substring(0,base.length()-1);
        HttpURLConnection con=(HttpURLConnection)new URL(base+path).openConnection();
        con.setConnectTimeout(12000);con.setReadTimeout(15000);con.setRequestMethod("POST");con.setDoOutput(true);
        con.setRequestProperty("Accept","application/json");
        con.setRequestProperty("Content-Type","application/json; charset=utf-8");
        con.setRequestProperty("X-BWD-Admin-Device",token);
        byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);
        try(java.io.OutputStream out=con.getOutputStream()){out.write(body);}
        int code=con.getResponseCode();con.disconnect();return code;
    }

    public static void sendInvoice(Context c,String bookingId,String invoiceNo,long total,int depositPercent,String dueDate,String paymentInstructions,Runnable done){
        new Thread(()->{
            SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
            try{
                JSONObject body=new JSONObject();
                body.put("invoice_no",invoiceNo==null?"":invoiceNo);
                body.put("total",total);
                body.put("deposit_percent",depositPercent);
                body.put("due_date",dueDate==null?"":dueDate);
                body.put("payment_instructions",paymentInstructions==null?"":paymentInstructions);
                String safeId=java.net.URLEncoder.encode(bookingId,"UTF-8");
                int code=postJson(c,"/v1/admin/bookings/"+safeId+"/invoice",body);
                p.edit().putString("owner_sync_status",code>=200&&code<300?"Invoice sent to client portal":"Invoice cloud sync failed ("+code+")").apply();
            }catch(Exception e){
                p.edit().putString("owner_sync_status","Invoice saved locally; cloud sync pending").apply();
            }finally{finish(done);}
        },"bwd-owner-send-invoice").start();
    }

    public static void confirmPayment(Context c,String bookingId,Runnable done){
        new Thread(()->{
            SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
            try{
                String safeId=java.net.URLEncoder.encode(bookingId,"UTF-8");
                int code=postJson(c,"/v1/admin/bookings/"+safeId+"/payment-confirmed",new JSONObject());
                p.edit().putString("owner_sync_status",code>=200&&code<300?"Payment confirmed to client portal":"Payment confirmation sync failed ("+code+")").apply();
            }catch(Exception e){
                p.edit().putString("owner_sync_status","Payment confirmation cloud sync pending").apply();
            }finally{finish(done);}
        },"bwd-owner-confirm-payment").start();
    }

'''
if 'public static void sendInvoice(' not in cloud:
    cloud = cloud.replace(anchor, methods + anchor, 1)
cloud_file.write_text(cloud)

# Make the dashboard stats match the simple flow.
main = main.replace('addKeyValue(stats,"Waiting Payments",String.valueOf(db.countBookings("status IN (\'WAITING FOR DEPOSIT\',\'DEPOSIT RECEIVED\')",null)));',
                    'addKeyValue(stats,"Awaiting Payment",String.valueOf(db.countBookings("status IN (\'INVOICE SENT\',\'PAYMENT SUBMITTED\')",null)));', 1)

# Replace the owner controls with only the actions actually needed.
start = main.find('    private void showAdminBookingActions(LinearLayout p,JSONObject b){')
end = main.find('\n\n    private void changeStatus(', start)
if start < 0 or end < 0:
    raise SystemExit('showAdminBookingActions block not found')
new_actions = r'''    private void showAdminBookingActions(LinearLayout p,JSONObject b){
        String id=b.optString("booking_id");String status=b.optString("status","REQUEST RECEIVED");
        p.addView(Ui.section(this,"BOOKING ACTION"));
        if("BOOKING CONFIRMED".equals(status)){
            LinearLayout ok=Ui.card(this);ok.addView(Ui.text(this,"PAYMENT CONFIRMED",15,Ui.GOLD,true));ok.addView(Ui.text(this,"Booking confirmed. Wait for the wedding day.",13,Ui.MUTED,false));p.addView(ok);
        }else{
            p.addView(fullButton(("INVOICE SENT".equals(status)||"PAYMENT SUBMITTED".equals(status))?"UPDATE INVOICE":"DEAL & SEND INVOICE",true,()->simpleInvoiceDialog(id)));
            p.addView(Ui.space(this,8));
            if("PAYMENT SUBMITTED".equals(status)){
                LinearLayout proof=Ui.card(this);
                String option=b.optString("payment_option","DEPOSIT");
                long paid=b.optLong("payment_amount",0);
                proof.addView(Ui.text(this,"PAYMENT PROOF RECEIVED",14,Ui.GOLD,true));
                proof.addView(Ui.text(this,("FULL".equals(option)?"Full payment":"50% deposit")+" · "+money(paid),14,Ui.WARM,true));
                String proofName=b.optString("payment_proof_name","");
                if(!proofName.isEmpty())proof.addView(Ui.text(this,proofName,12,Ui.MUTED,false));
                p.addView(proof);
                String proofUrl=b.optString("payment_proof_url","");
                if(!proofUrl.isEmpty()){p.addView(fullButton("VIEW PAYMENT PROOF",true,()->openPaymentProof(proofUrl)));p.addView(Ui.space(this,8));}
                p.addView(fullButton("CONFIRM PAYMENT",true,()->simpleConfirmPayment(id)));p.addView(Ui.space(this,8));
            }
            if(db.invoice(id)!=null){p.addView(fullButton("SHARE INVOICE PDF",false,()->createInvoicePdf(id)));p.addView(Ui.space(this,8));}
        }
        p.addView(fullButton("BACK TO BOOKINGS",false,this::showAdminBookings));
    }

    private void openPaymentProof(String url){
        try{
            android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url));
            startActivity(i);
        }catch(Exception e){toast("Unable to open payment proof.");}
    }

    private void simpleInvoiceDialog(String id){
        JSONObject b=db.booking(id),pkg=b==null?null:db.packageByName(b.optString("package_name"));
        JSONObject q=db.quote(id);
        LinearLayout box=Ui.column(this);box.setPadding(20,6,20,6);
        EditText total=Ui.input(this,"Invoice total IDR");
        EditText due=Ui.input(this,"Payment due date YYYY-MM-DD");
        long defaultTotal=q!=null?q.optLong("total"):(pkg==null?0:pkg.optLong("price"));
        total.setText(defaultTotal>0?String.valueOf(defaultTotal):"");
        if(q!=null)due.setText(q.optString("due_date"));
        box.addView(total);box.addView(due);
        LinearLayout bank=Ui.card(this);
        bank.addView(Ui.text(this,"PAYMENT",12,Ui.GOLD,true));
        bank.addView(Ui.text(this,"Client can choose 50% deposit or full payment.",13,Ui.MUTED,false));
        bank.addView(Ui.text(this,"BCA · Bagus Putu Hardajaya · 0080679203",13,Ui.WARM,true));
        box.addView(bank);
        new AlertDialog.Builder(this).setTitle("Deal & Send Invoice · "+id).setView(box).setPositiveButton("SEND INVOICE",(d,w)->{
            long amount=parseMoney(total);if(amount<=0){toast("Enter invoice total.");return;}
            int pct=50;
            String dueText=due.getText().toString().trim();
            String payText="BCA\nBagus Putu Hardajaya\n0080679203\nPayment option: 50% deposit or full payment";
            db.saveQuote(id,"",amount,0,0,pct,dueText,"","");
            db.generateInvoice(id);db.setBookingStatus(id,"INVOICE SENT");
            JSONObject inv=db.invoice(id);String invNo=inv==null?"":inv.optString("invoice_no");
            BwdOwnerCloud.sendInvoice(this,id,invNo,amount,pct,dueText,payText,()->BwdOwnerCloud.sync(this,db,()->showBookingDetail(id,true)));
        }).setNegativeButton("CANCEL",null).show();
    }

    private void simpleConfirmPayment(String id){
        JSONObject b=db.booking(id);
        String option=b==null?"":b.optString("payment_option","");
        long paid=b==null?0:b.optLong("payment_amount",0);
        String what=("FULL".equals(option)?"full payment":"50% deposit")+(paid>0?" · "+money(paid):"");
        new AlertDialog.Builder(this).setTitle("Confirm Payment").setMessage("Payment proof checked? Confirm "+what+" for this booking?").setPositiveButton("CONFIRM",(d,w)->{
            BwdOwnerCloud.confirmPayment(this,id,()->BwdOwnerCloud.sync(this,db,()->showBookingDetail(id,true)));
        }).setNegativeButton("CANCEL",null).show();
    }'''
main = main[:start] + new_actions + main[end:]

# Replace the long status ladder with the only four states the business needs.
status_start = main.find('    private LinearLayout statusTimeline(String status){')
status_end = main.find('\n\n    private LinearLayout musicCard(', status_start)
if status_start < 0 or status_end < 0:
    raise SystemExit('statusTimeline block not found')
new_timeline = r'''    private LinearLayout statusTimeline(String status){
        String[] flow={"REQUEST RECEIVED","INVOICE SENT","PAYMENT SUBMITTED","BOOKING CONFIRMED"};
        LinearLayout c=Ui.card(this);int current=0;
        for(int i=0;i<flow.length;i++)if(flow[i].equals(status)){current=i;break;}
        for(int i=0;i<flow.length;i++){int color=i<=current?Ui.GOLD:Ui.MUTED;c.addView(Ui.text(this,(i<=current?"● ":"○ ")+flow[i],13,color,i==current));}
        return c;
    }'''
main = main[:status_start] + new_timeline + main[status_end:]

main_file.write_text(main)

all_text = main_file.read_text() + cloud_file.read_text() + db_file.read_text()
required = [
    'DEAL & SEND INVOICE',
    'VIEW PAYMENT PROOF',
    'CONFIRM PAYMENT',
    'SHARE INVOICE PDF',
    'public static void sendInvoice(',
    'public static void confirmPayment(',
    'INVOICE SENT',
    'PAYMENT SUBMITTED',
    'BOOKING CONFIRMED',
    '0080679203',
    'v.put("status",cloudStatus);',
]
for token in required:
    if token not in all_text:
        raise SystemExit('missing simple Owner token: '+token)

for removed in ['CHANGE BOOKING STATUS','CREATE / UPDATE QUOTATION','EDIT ADMIN NOTES']:
    actions = main_file.read_text()[main_file.read_text().find('private void showAdminBookingActions'):main_file.read_text().find('private void changeStatus')]
    if removed in actions:
        raise SystemExit('legacy Owner action still visible: '+removed)

print('BWD Owner simplified: request -> invoice -> uploaded proof -> confirm payment')
