#!/usr/bin/env python3
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()

old = '<div class=\"notice\" style=\"margin-top:12px\"><strong>BANK TRANSFER · BCA</strong><br>Bagus Putu Hardajaya<br>0080679203</div>'
new = '<div class=\"notice\" style=\"margin-top:12px\"><strong>BANK TRANSFER · BCA</strong><br>Bagus Putu Hardajaya<br><span style=\"font-size:18px;font-weight:800;letter-spacing:.06em\">0080679203</span><button class=\"btn secondary full\" type=\"button\" style=\"margin-top:10px\" id=\"copy-bca-account\">COPY ACCOUNT NUMBER</button></div>'
if old not in s:
    raise SystemExit('BCA account card anchor not found')
s = s.replace(old, new, 1)

anchor = "    const pay=$('#payment-submitted');if(pay)pay.onclick=()=>submitSimplePayment(id,token);"
insert = """    const copyBca=$('#copy-bca-account');
    if(copyBca)copyBca.onclick=async()=>{
      const account='0080679203';
      try{
        if(navigator.clipboard&&window.isSecureContext){await navigator.clipboard.writeText(account);}
        else{
          const ta=document.createElement('textarea');ta.value=account;ta.style.position='fixed';ta.style.opacity='0';document.body.appendChild(ta);ta.select();document.execCommand('copy');ta.remove();
        }
        copyBca.textContent='COPIED';setTimeout(()=>{copyBca.textContent='COPY ACCOUNT NUMBER';},1400);
      }catch(_){alert('Account number: '+account);}
    };
    const pay=$('#payment-submitted');if(pay)pay.onclick=()=>submitSimplePayment(id,token);"""
if anchor not in s:
    raise SystemExit('payment button anchor not found')
s = s.replace(anchor, insert, 1)

for token in ['COPY ACCOUNT NUMBER', "navigator.clipboard", '0080679203']:
    if token not in s:
        raise SystemExit('copy-account verification failed: '+token)

p.write_text(s)
print('BWD client: BCA account number copy button enabled')
