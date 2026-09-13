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

  async function submitSimplePayment(id,token){
    const btn=$('#payment-submitted');if(btn){btn.disabled=true;btn.textContent='SENDING...';}
    try{
      const res=await fetch(`${API}/bookings/${encodeURIComponent(id)}/payment-submitted`,{method:'POST',headers:{'X-BWD-Client-Token':token,'Accept':'application/json','Content-Type':'application/json'},body:'{}'});
      const out=await res.json().catch(()=>({}));
      if(!res.ok||!out.ok)throw new Error(out.error||`HTTP ${res.status}`);
      await loadBooking(id,token);
      const msg=`Hello Bali Wedding DJ, I have submitted payment for booking ${id}. Please verify my payment.`;
      window.open(`https://wa.me/6282247972288?text=${encodeURIComponent(msg)}`,'_blank','noopener');
    }catch(err){alert(`Unable to submit payment status: ${err.message}`);if(btn){btn.disabled=false;btn.textContent='I HAVE PAID';}}
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
      action=`<div class="notice" style="margin-top:14px"><strong>PAYMENT SUBMITTED</strong><br>Your payment has been sent for owner verification.</div>`;
    }else if(hasInvoice){
      action=`<div style="margin-top:18px"><div class="kicker">INVOICE & PAYMENT</div><div class="details"><div class="detail"><span>Invoice</span><span>${escapeHtml(b.invoice_no||'—')}</span></div><div class="detail"><span>Total</span><span>${formatIdr(b.invoice_total)}</span></div><div class="detail"><span>Deposit Due</span><span>${formatIdr(b.invoice_deposit)}</span></div><div class="detail"><span>Balance</span><span>${formatIdr(b.invoice_balance)}</span></div><div class="detail"><span>Due Date</span><span>${escapeHtml(b.invoice_due_date||'—')}</span></div></div><div class="notice" style="white-space:pre-wrap">${escapeHtml(b.payment_instructions||'Please contact Bali Wedding DJ on WhatsApp for payment details.')}</div><button class="btn primary full" style="margin-top:12px" id="payment-submitted">I HAVE PAID</button></div>`;
    }else{
      action=`<div class="notice" style="margin-top:14px">We are checking your date and booking details. If everything is available, your invoice will appear here.</div>`;
    }
    p.innerHTML=`<div class="kicker">MY BOOKING</div><h3 class="package-name" style="margin-top:8px">${escapeHtml(b.bride)} &amp; ${escapeHtml(b.groom)}</h3><span class="status-pill">${escapeHtml(status)}</span><div class="details"><div class="detail"><span>Booking ID</span><span>${escapeHtml(id)}</span></div><div class="detail"><span>Wedding Date</span><span>${escapeHtml(b.wedding_date||'—')}</span></div><div class="detail"><span>Venue</span><span>${escapeHtml([b.venue_name,b.venue_location].filter(Boolean).join(' · ')||'—')}</span></div><div class="detail"><span>Package</span><span>${escapeHtml(b.package_name||'—')}</span></div><div class="detail"><span>DJ Time</span><span>${escapeHtml([b.start_time,b.finish_time].filter(Boolean).join(' – ')||'—')}</span></div></div>${action}<button class="btn secondary full" style="margin-top:14px" id="refresh-booking">REFRESH STATUS</button><a class="btn secondary full" style="margin-top:9px" href="https://wa.me/6282247972288?text=${encodeURIComponent('Hello Bali Wedding DJ, I need help with booking '+id)}" target="_blank" rel="noopener">WHATSAPP</a>`;
    $('#refresh-booking').onclick=()=>loadBooking(id,token);
    const pay=$('#payment-submitted');if(pay)pay.onclick=()=>submitSimplePayment(id,token);
  }'''

s = s[:start] + new + s[end:]
if 'I HAVE PAID' not in s or '>WHATSAPP</a>' not in s or 'BOOKING CONFIRMED' not in s:
    raise SystemExit('simple portal verification failed')
p.write_text(s)
print('BWD guest portal simplified: booking -> invoice/payment -> confirmed + WhatsApp')
