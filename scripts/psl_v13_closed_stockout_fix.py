from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V13_CLOSED_STOCKOUT_CORRECTION' in s:
    print('PSL v13 already present')
    raise SystemExit(0)

old="""  Stock.stockOut=async function(){ const date=stockoutDateV5.value||operationalDateV5,product=stockoutProduct.value,qty=Number(stockoutQty.value),note=stockoutReason.value;if(qty<=0||!note)return alert('Isi jumlah dan alasan');if(!await ensureOpenV5(date))return;await dbPut('stock_movements',{id:uidV5(),date,datetime:new Date().toISOString(),product,qty,type:'stockout',note,user:currentRole,status:'ACTIVE'});UI.closeModal('stockout');operationalDateV5=date;document.getElementById('operationalDateV5').value=date;await refreshV5();alert('Stock keluar berhasil');};"""

new="""  // PSL_V13_CLOSED_STOCKOUT_CORRECTION
  Stock.stockOut=async function(){
    const date=stockoutDateV5.value||operationalDateV5,product=stockoutProduct.value,qty=Number(stockoutQty.value),note=(stockoutReason.value||'').trim();
    if(qty<=0||!note)return alert('Isi jumlah dan alasan');
    const closed=await isClosedV5(date);
    if(closed){
      if(currentRole!=='OWNER')return alert('Tanggal '+date+' sudah CLOSED. Hanya OWNER yang dapat membuat koreksi stock keluar.');
      const ok=confirm('Tanggal '+date+' sudah CLOSED. Tambahkan Stock Keluar sebagai KOREKSI tanpa menimpa snapshot lama?');
      if(!ok)return;
      await dbPut('stock_movements',{id:uidV5(),date,datetime:new Date().toISOString(),product,qty,type:'stockout',note,user:currentRole,status:'ACTIVE',isCorrection:true,correctionKind:'late_stockout_after_close',snapshotUnchanged:true});
      UI.closeModal('stockout');operationalDateV5=date;document.getElementById('operationalDateV5').value=date;await refreshV5();alert('Koreksi Stock Keluar '+date+' berhasil. Snapshot CLOSED lama tetap tidak ditimpa.');
      return;
    }
    await dbPut('stock_movements',{id:uidV5(),date,datetime:new Date().toISOString(),product,qty,type:'stockout',note,user:currentRole,status:'ACTIVE'});
    UI.closeModal('stockout');operationalDateV5=date;document.getElementById('operationalDateV5').value=date;await refreshV5();alert('Stock keluar berhasil');
  };"""

if old not in s:
    raise SystemExit('target Stock.stockOut implementation not found')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')
print('patched PSL v13 closed stockout correction')
