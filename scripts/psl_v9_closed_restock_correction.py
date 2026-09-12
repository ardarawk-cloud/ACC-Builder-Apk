from pathlib import Path

p = Path('apps/papa-sauce-lab/index.html')
s = p.read_text(encoding='utf-8')

if 'PSL_V9_CLOSED_RESTOCK_CORRECTION' in s:
    print('PSL v9 closed restock correction already present')
    raise SystemExit(0)

old = """  Stock.restock=async function(){ const date=restockDateV5.value||operationalDateV5,product=restockProduct.value,qty=Number(restockQty.value);if(qty<=0)return alert('Masukkan jumlah valid');if(!await ensureOpenV5(date))return;await dbPut('stock_movements',{id:uidV5(),date,datetime:new Date().toISOString(),product,qty,type:'restock',note:'Restock',user:currentRole,status:'ACTIVE'});UI.closeModal('restock');operationalDateV5=date;operationalDateV5 && (document.getElementById('operationalDateV5').value=date);await refreshV5();alert('Restock berhasil');};"""

new = """  // PSL_V9_CLOSED_RESTOCK_CORRECTION
  Stock.restock=async function(){
    const date=restockDateV5.value||operationalDateV5,product=restockProduct.value,qty=Number(restockQty.value);
    if(qty<=0)return alert('Masukkan jumlah valid');
    const closed=await isClosedV5(date);
    if(closed){
      if(currentRole!=='OWNER')return alert('Tanggal '+date+' sudah CLOSED. Hanya OWNER yang dapat membuat koreksi restock.');
      if(!confirm('Tanggal '+date+' sudah CLOSED. Simpan sebagai KOREKSI RESTOCK tanpa menimpa snapshot lama?'))return;
      const reason=prompt('Alasan koreksi restock:','Lupa input restock');
      if(reason===null)return;
      if(!reason.trim())return alert('Alasan koreksi wajib diisi');
      const id=uidV5(),now=new Date().toISOString();
      await dbPut('stock_movements',{id,date,datetime:now,product,qty,type:'restock',note:'KOREKSI CLOSED: '+reason.trim(),user:currentRole,status:'ACTIVE',isCorrection:true,closedCorrection:true});
      await dbPut('corrections',{id:'corr-'+id,date,datetime:now,entity:'stock_movement',entityId:id,action:'ADD_MISSED_RESTOCK',product,qty,reason:reason.trim(),user:currentRole,closedSnapshot:'daily-'+date});
      UI.closeModal('restock');
      operationalDateV5=date;
      document.getElementById('operationalDateV5').value=date;
      await refreshV5();
      alert('Koreksi restock tersimpan. Snapshot CLOSED tidak ditimpa dan audit koreksi tercatat.');
      return;
    }
    await dbPut('stock_movements',{id:uidV5(),date,datetime:new Date().toISOString(),product,qty,type:'restock',note:'Restock',user:currentRole,status:'ACTIVE'});
    UI.closeModal('restock');operationalDateV5=date;document.getElementById('operationalDateV5').value=date;await refreshV5();alert('Restock berhasil');
  };"""

if old not in s:
    raise SystemExit('target Stock.restock function not found')
s = s.replace(old, new, 1)

old_hist = """stockHistory.innerHTML=all.length?all.map(x=>`<div class=\"transaction-item\"><div>${x.type==='restock'?'📦':'📤'} ${escV5(x.product)}<div class=\"transaction-meta\">${x.date} · audit ${new Date(x.datetime).toLocaleString('id-ID')}</div></div><b>${x.type==='restock'?'+':'-'}${x.qty}</b></div>`).join(''):'<div class=\"empty-state\">Belum ada pergerakan stock tanggal ini.</div>';"""
new_hist = """stockHistory.innerHTML=all.length?all.map(x=>`<div class=\"transaction-item\"><div>${x.type==='restock'?'📦':'📤'} ${escV5(x.product)}${x.isCorrection?' <span class=\"badge badge-orange\">KOREKSI</span>':''}<div class=\"transaction-meta\">${x.date} · audit ${new Date(x.datetime).toLocaleString('id-ID')} · ${escV5(x.note||'')}</div></div><b>${x.type==='restock'?'+':'-'}${x.qty}</b></div>`).join(''):'<div class=\"empty-state\">Belum ada pergerakan stock tanggal ini.</div>';"""
if old_hist not in s:
    raise SystemExit('target stock history renderer not found')
s = s.replace(old_hist, new_hist, 1)

p.write_text(s, encoding='utf-8')
print('patched PSL v9 closed restock correction')
