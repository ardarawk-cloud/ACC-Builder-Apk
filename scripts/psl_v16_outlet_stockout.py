from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V16_OUTLET_STOCKOUT' in s:
    print('PSL v16 outlet stockout already present')
    raise SystemExit(0)

style=r'''
<style id="PSL_V16_OUTLET_STOCKOUT_STYLE">
.psl16-outlet-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:10px 0}
.psl16-outlet-kpi{border:1px solid #e8deda;border-radius:13px;padding:10px;background:linear-gradient(180deg,#fffdf9,#fff);text-align:center}
.psl16-outlet-kpi span{display:block;font-size:9px;font-weight:900;letter-spacing:.6px;color:#8b6b53;margin-bottom:4px}
.psl16-outlet-kpi b{font-size:20px;color:#7b1730}
.psl16-outlet-box{margin:12px 0;padding:13px;border:1px solid #e8deda;border-radius:15px;background:#fff}
.psl16-outlet-title{font-size:11px;font-weight:900;letter-spacing:.65px;text-transform:uppercase;color:#7b1730;margin-bottom:8px}
.psl16-outlet-table{display:grid;grid-template-columns:minmax(0,1fr) 46px 46px 46px;gap:6px;align-items:center;padding:7px 0;border-bottom:1px solid #f0eaea;font-size:11px}
.psl16-outlet-table:last-child{border-bottom:0}.psl16-outlet-table b{text-align:right}.psl16-muted{color:#8a8a8a;font-size:10px}
</style>
'''

js=r'''
<script id="PSL_V16_OUTLET_STOCKOUT">
(() => {
  const OUTLETS16=['PSK','PSP','PSBK'];
  const esc16=v=>String(v??'').replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
  const opDate16=()=>document.getElementById('operationalDateV5')?.value||new Date().toISOString().slice(0,10);
  const uid16=()=>`so-${Date.now()}-${Math.random().toString(36).slice(2,9)}`;
  const fmt16=d=>{const y=d.getFullYear(),m=String(d.getMonth()+1).padStart(2,'0'),day=String(d.getDate()).padStart(2,'0');return `${y}-${m}-${day}`};

  function ensureOutletField16(){
    if(document.getElementById('stockoutOutlet16'))return true;
    const reason=document.getElementById('stockoutReason');
    const group=reason?.closest('.form-group');
    if(!group)return false;
    group.insertAdjacentHTML('beforebegin',`<div class="form-group" id="stockoutOutletGroup16"><label class="form-label">Outlet Tujuan</label><select class="form-select" id="stockoutOutlet16"><option value="">Pilih outlet</option><option value="PSK">PSK</option><option value="PSP">PSP</option><option value="PSBK">PSBK</option></select><div class="psl16-muted" style="margin-top:5px">Wajib dipilih untuk Stock Keluar manual agar distribusi outlet bisa dimonitor.</div></div>`);
    return true;
  }

  async function closed16(date){
    const snaps=await dbGetAll('snapshots');
    return (snaps||[]).some(x=>x&&x.date===date&&(x.type==='daily'||x.id===`daily-${date}`));
  }
  async function refresh16(){
    if(UI.updateDashboard)await UI.updateDashboard();
    if(UI.renderTodayTransactions)await UI.renderTodayTransactions();
    if(UI.renderStock)await UI.renderStock();
    if(UI.renderHistory)await UI.renderHistory();
  }

  Stock.stockOut=async function(){
    ensureOutletField16();
    const date=document.getElementById('stockoutDateV5')?.value||opDate16();
    const product=document.getElementById('stockoutProduct')?.value||'';
    const qty=Number(document.getElementById('stockoutQty')?.value||0);
    const reason=(document.getElementById('stockoutReason')?.value||'').trim();
    const outlet=document.getElementById('stockoutOutlet16')?.value||'';
    if(!outlet||!OUTLETS16.includes(outlet))return alert('Pilih outlet tujuan: PSK, PSP, atau PSBK');
    if(qty<=0)return alert('Isi jumlah Stock Keluar');
    if(!reason)return alert('Isi alasan / catatan');
    const isClosed=await closed16(date);
    if(isClosed){
      if(currentRole!=='OWNER')return alert('Tanggal '+date+' sudah CLOSED. Hanya OWNER yang dapat membuat koreksi Stock Keluar.');
      if(!confirm('Tanggal '+date+' sudah CLOSED. Tambahkan Stock Keluar ke '+outlet+' sebagai KOREKSI tanpa menimpa snapshot lama?'))return;
    }
    await dbPut('stock_movements',{
      id:uid16(),date,datetime:new Date().toISOString(),product,qty,type:'stockout',
      outlet,destinationOutlet:outlet,movementKind:'outlet_transfer',
      note:`${outlet} · ${reason}`,reasonOriginal:reason,user:currentRole,status:'ACTIVE',
      ...(isClosed?{isCorrection:true,correctionKind:'late_stockout_after_close',snapshotUnchanged:true}:{})
    });
    UI.closeModal('stockout');
    const q=document.getElementById('stockoutQty');if(q)q.value='';
    const r=document.getElementById('stockoutReason');if(r)r.value='';
    await refresh16();
    alert((isClosed?'Koreksi Stock Keluar':'Stock Keluar')+' ke '+outlet+' berhasil');
  };

  async function summary16(start,end){
    const all=await dbGetAll('stock_movements');
    const moves=(all||[]).filter(x=>x&&x.type==='stockout'&&x.status!=='CORRECTED'&&x.date>=start&&x.date<=end);
    const outlets={PSK:0,PSP:0,PSBK:0,OTHER:0};
    const per={};
    for(const x of moves){
      const o=OUTLETS16.includes(x.outlet)?x.outlet:'OTHER',q=Number(x.qty||0),p=x.product||'Tanpa Produk';
      outlets[o]+=q;
      if(!per[p])per[p]={PSK:0,PSP:0,PSBK:0,OTHER:0};
      per[p][o]+=q;
    }
    return {outlets,per,total:moves.reduce((a,x)=>a+Number(x.qty||0),0)};
  }

  function outletBox16(m,title='Distribusi Stock Out per Outlet'){
    const rows=Object.entries(m.per).filter(([,v])=>v.PSK||v.PSP||v.PSBK).sort((a,b)=>a[0].localeCompare(b[0])).map(([p,v])=>`<div class="psl16-outlet-table"><span>${esc16(p)}</span><b>${v.PSK}</b><b>${v.PSP}</b><b>${v.PSBK}</b></div>`).join('')||'<div class="empty-state" style="padding:18px">Belum ada distribusi ke outlet pada periode ini.</div>';
    const other=m.outlets.OTHER?`<div class="report-row"><span>Non-outlet / data lama</span><b>${m.outlets.OTHER}</b></div>`:'';
    return `<div class="psl16-outlet-box"><div class="psl16-outlet-title">${esc16(title)}</div><div class="psl16-outlet-grid"><div class="psl16-outlet-kpi"><span>PSK</span><b>${m.outlets.PSK}</b></div><div class="psl16-outlet-kpi"><span>PSP</span><b>${m.outlets.PSP}</b></div><div class="psl16-outlet-kpi"><span>PSBK</span><b>${m.outlets.PSBK}</b></div></div>${other}<div class="psl16-outlet-table" style="font-size:9px;font-weight:900;text-transform:uppercase;color:#7b6e70"><span>Produk</span><b>PSK</b><b>PSP</b><b>PSBK</b></div>${rows}</div>`;
  }
  async function appendReport16(start,end,title){
    const root=document.getElementById('reportContent');if(!root)return;
    root.querySelectorAll('.psl16-outlet-box').forEach(x=>x.remove());
    root.insertAdjacentHTML('beforeend',outletBox16(await summary16(start,end),title));
  }

  const oldDaily16=Reports.generate?.bind(Reports);
  Reports.generate=async function(){
    if(oldDaily16)await oldDaily16();
    const d=opDate16();
    const rd=document.getElementById('reportDate');if(rd)rd.value=d;
    await appendReport16(d,d,'Distribusi Outlet · Harian');
  };
  const oldWeekly16=Reports.generateWeekly?.bind(Reports);
  Reports.generateWeekly=async function(){
    if(oldWeekly16)await oldWeekly16();
    const anchor=opDate16(),d=new Date(anchor+'T12:00:00'),day=d.getDay(),delta=day===0?-6:1-day,startD=new Date(d);startD.setDate(d.getDate()+delta);const endD=new Date(startD);endD.setDate(startD.getDate()+6);
    await appendReport16(fmt16(startD),fmt16(endD),'Distribusi Outlet · Mingguan');
  };
  const oldMonthly16=Reports.generateMonthly?.bind(Reports);
  Reports.generateMonthly=async function(){
    if(oldMonthly16)await oldMonthly16();
    const anchor=opDate16(),[y,m]=anchor.split('-').map(Number),start=`${y}-${String(m).padStart(2,'0')}-01`,end=fmt16(new Date(y,m,0,12));
    await appendReport16(start,end,'Distribusi Outlet · Bulanan');
  };
  const oldAll16=Reports.generateAll?.bind(Reports);
  Reports.generateAll=async function(){
    if(oldAll16)await oldAll16();
    const all=await dbGetAll('stock_movements'),dates=(all||[]).map(x=>x?.date).filter(Boolean).sort(),end=opDate16(),start=dates[0]||end;
    await appendReport16(start,end,'Distribusi Outlet · Semua Data');
  };

  const oldStock16=UI.renderStock?.bind(UI);
  UI.renderStock=async function(){
    if(oldStock16)await oldStock16();
    const root=document.getElementById('stockLevels');if(!root)return;
    root.querySelectorAll('.psl16-stock-outlets').forEach(x=>x.remove());
    const m=await summary16(opDate16(),opDate16());
    const el=document.createElement('div');el.className='psl16-stock-outlets';el.innerHTML=outletBox16(m,'Oper ke Outlet · Hari Terpilih');
    root.prepend(el);
  };

  let tries=0;const timer=setInterval(async()=>{tries++;if(ensureOutletField16()){clearInterval(timer);if(db&&UI.renderStock)await UI.renderStock();}else if(tries>=50)clearInterval(timer)},100);
  console.log('PSL v16 outlet stockout active');
})();
</script>
'''

if '</head>' not in s or '</body>' not in s:
    raise SystemExit('invalid html skeleton')
s=s.replace('</head>',style+'\n</head>',1)
s=s.replace('</body>',js+'\n</body>',1)
p.write_text(s,encoding='utf-8')
print('patched PSL v16 outlet stockout')
