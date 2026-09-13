#!/usr/bin/env python3
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()
start = s.find('  function renderPortal(b,id,token){')
end = s.find('\n\n  document.addEventListener', start)
if start < 0 or end < 0:
    raise SystemExit('renderPortal block not found')

new = r'''  function formatIdr(v){const n=Number(v||0);return n>0?'Rp '+n.toLocaleString('id-ID'):'—';}

  let selectedPaymentProof=null;

  async function normalizePaymentProof(file){
    if(!file)return null;
    if(file.type==='application/pdf'){
      if(file.size>950000)throw new Error('PDF proof is too large. Please use a file under 950 KB.');
      return file;
    }
    if(!String(file.type||'').startsWith('image/'))throw new Error('Use a screenshot, photo, JPG, PNG, WEBP, or PDF.');
    const bitmap=await createImageBitmap(file);
    const maxSide=1600;
    const scale=Math.min(1,maxSide/Math.max(bitmap.width,bitmap.height));
    const canvas=document.createElement('canvas');
    canvas.width=Math.max(1,Math.round(bitmap.width*scale));
    canvas.height=Math.max(1,Math.round(bitmap.height*scale));
    const ctx=canvas.getContext('2d');
    ctx.drawImage(bitmap,0,0,canvas.width,canvas.height);
    if(bitmap.close)bitmap.close();
    const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/jpeg',0.80));
    if(!blob)throw new Error('Could not prepare the payment proof image.');
    if(blob.size>950000)throw new Error('Payment proof image is still too large. Try a screenshot instead.');
    const base=(file.name||'payment-proof').replace(/\.[^.]+$/,'').slice(0,100)||'payment-proof';
    return new File([blob],base+'.jpg',{type:'image/jpeg'});
  }

  async function pickPaymentProof(file){
    const label=$('#payment-proof-name');
    try{
      selectedPaymentProof=await normalizePaymentProof(file);
      if(label)label.textContent=selectedPaymentProof?`Selected: ${selectedPaymentProof.name}`:'No proof selected';
    }catch(err){
      selectedPaymentProof=null;
      if(label)label.textContent='No proof selected';
      alert(err.message||String(err));
    }
  }

  async function submitSimplePayment(id,token){
    const btn=$('#payment-submitted');
    const option=document.querySelector('input[name="payment-option"]:checked');
    if(!option){alert('Choose 50% Deposit or Full Payment.');return;}
    if(!selectedPaymentProof){alert('Please attach your payment proof first.');return;}
    if(btn){btn.disabled=true;btn.textContent='SENDING PAYMENT PROOF...';}
    try{
      const form=new FormData();
      form.append('payment_option',option.value);
      form.append('proof',selectedPaymentProof,selectedPaymentProof.name);
      const res=await fetch(`${API}/bookings/${encodeURIComponent(id)}/payment-submitted`,{
        method:'POST',
        headers:{'X-BWD-Client-Token':token,'Accept':'application/json'},
        body:form
      });
      const out=await res.json().catch(()=>({}));
      if(!res.ok||!out.ok)throw new Error(out.error||`HTTP ${res.status}`);
      selectedPaymentProof=null;
      await loadBooking(id,token);
      const msg=`Hello Bali Wedding DJ, I have submitted ${out.payment_option==='FULL'?'full payment':'50% deposit'} (${formatIdr(out.payment_amount)}) for booking ${id}. Payment proof has been uploaded.`;
      window.open(`https://wa.me/6282247972288?text=${encodeURIComponent(msg)}`,'_blank','noopener');
    }catch(err){
      alert(`Unable to send payment proof: ${err.message}`);
      if(btn){btn.disabled=false;btn.textContent='I HAVE PAID · SEND PROOF';}
    }
  }

  function renderPortal(b,id,token){
    $('#stored-booking').innerHTML='';
    const p=$('#booking-portal');p.classList.remove('hidden');
    const status=String(b.status||'REQUEST RECEIVED');
    const confirmed=status==='BOOKING CONFIRMED';
    const submitted=status==='PAYMENT SUBMITTED';
    const hasInvoice=Number(b.invoice_total||0)>0;
    let action='';
    if(confirmed){
      action=`<div class="notice" style="margin-top:14px"><strong>BOOKING CONFIRMED</strong><br>Your payment is confirmed. See you on your wedding day.</div>`;
    }else if(submitted){
      action=`<div class="notice" style="margin-top:14px"><strong>PAYMENT SUBMITTED</strong><br>${escapeHtml(b.payment_option==='FULL'?'Full payment':'50% deposit')} · ${formatIdr(b.payment_amount)}<br>Payment proof received and waiting for owner confirmation.</div>`;
    }else if(hasInvoice){
      action=`<div style="margin-top:18px">
        <div class="kicker">INVOICE & PAYMENT</div>
        <div class="details">
          <div class="detail"><span>Invoice</span><span>${escapeHtml(b.invoice_no||'—')}</span></div>
          <div class="detail"><span>Total</span><span>${formatIdr(b.invoice_total)}</span></div>
          <div class="detail"><span>Due Date</span><span>${escapeHtml(b.invoice_due_date||'—')}</span></div>
        </div>
        <div class="kicker" style="margin-top:18px">CHOOSE PAYMENT</div>
        <label class="detail" style="cursor:pointer;margin-top:8px"><span><input type="radio" name="payment-option" value="DEPOSIT" checked> 50% Deposit</span><strong>${formatIdr(b.invoice_deposit)}</strong></label>
        <label class="detail" style="cursor:pointer;margin-top:8px"><span><input type="radio" name="payment-option" value="FULL"> Full Payment</span><strong>${formatIdr(b.invoice_total)}</strong></label>
        <div class="notice" style="margin-top:12px"><strong>BANK TRANSFER · BCA</strong><br>Bagus Putu Hardajaya<br>0080679203</div>
        <div class="kicker" style="margin-top:18px">PAYMENT PROOF</div>
        <div class="notice" style="margin-top:8px">Attach a screenshot, image, PDF, or take a photo directly.</div>
        <input id="payment-proof-file" type="file" accept="image/jpeg,image/png,image/webp,application/pdf" style="display:none">
        <input id="payment-proof-camera" type="file" accept="image/*" capture="environment" style="display:none">
        <button class="btn secondary full" type="button" style="margin-top:10px" id="choose-proof-file">UPLOAD SCREENSHOT / FILE</button>
        <button class="btn secondary full" type="button" style="margin-top:8px" id="choose-proof-camera">TAKE PHOTO</button>
        <div class="notice" id="payment-proof-name" style="margin-top:8px">No proof selected</div>
        <button class="btn primary full" style="margin-top:12px" id="payment-submitted">I HAVE PAID · SEND PROOF</button>
      </div>`;
    }else{
      action=`<div class="notice" style="margin-top:14px">We are checking your date and booking details. If everything is available, your invoice will appear here.</div>`;
    }
    p.innerHTML=`<div class="kicker">MY BOOKING</div><h3 class="package-name" style="margin-top:8px">${escapeHtml(b.bride)} &amp; ${escapeHtml(b.groom)}</h3><span class="status-pill">${escapeHtml(status)}</span><div class="details"><div class="detail"><span>Booking ID</span><span>${escapeHtml(id)}</span></div><div class="detail"><span>Wedding Date</span><span>${escapeHtml(b.wedding_date||'—')}</span></div><div class="detail"><span>Venue</span><span>${escapeHtml([b.venue_name,b.venue_location].filter(Boolean).join(' · ')||'—')}</span></div><div class="detail"><span>Package</span><span>${escapeHtml(b.package_name||'—')}</span></div><div class="detail"><span>DJ Time</span><span>${escapeHtml([b.start_time,b.finish_time].filter(Boolean).join(' – ')||'—')}</span></div></div>${action}<button class="btn secondary full" style="margin-top:14px" id="refresh-booking">REFRESH STATUS</button><a class="btn secondary full" style="margin-top:9px" href="https://wa.me/6282247972288?text=${encodeURIComponent('Hello Bali Wedding DJ, I need help with booking '+id)}" target="_blank" rel="noopener">WHATSAPP</a>`;
    $('#refresh-booking').onclick=()=>loadBooking(id,token);
    const file=$('#payment-proof-file');
    const cam=$('#payment-proof-camera');
    const fileBtn=$('#choose-proof-file');
    const camBtn=$('#choose-proof-camera');
    if(fileBtn&&file)fileBtn.onclick=()=>file.click();
    if(camBtn&&cam)camBtn.onclick=()=>cam.click();
    if(file)file.onchange=()=>pickPaymentProof(file.files&&file.files[0]);
    if(cam)cam.onchange=()=>pickPaymentProof(cam.files&&cam.files[0]);
    const pay=$('#payment-submitted');if(pay)pay.onclick=()=>submitSimplePayment(id,token);
  }'''

s = s[:start] + new + s[end:]
for token in ['I HAVE PAID · SEND PROOF','UPLOAD SCREENSHOT / FILE','TAKE PHOTO','>WHATSAPP</a>','BOOKING CONFIRMED','50% Deposit','Full Payment','0080679203','payment_option']:
    if token not in s:
        raise SystemExit('simple portal verification failed: '+token)
p.write_text(s)
print('BWD guest portal: deposit/full + BCA + screenshot/file/camera proof upload + WhatsApp')
