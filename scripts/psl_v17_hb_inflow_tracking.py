from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V17_HB_INFLOW_TRACKING' in s:
    print('PSL v17 HB inflow tracking already present')
    raise SystemExit(0)

style=r'''
<style id="PSL_V17_HB_INFLOW_TRACKING_STYLE">
.psl17-hb-card{cursor:pointer}
.psl17-hb-value{color:#8a5a16!important}
.psl17-hb-box{margin:12px 0;padding:13px;border:1px solid #e8dcc8;border-radius:15px;background:linear-gradient(180deg,#fffaf2,#fff)}
.psl17-hb-title{font-size:11px;font-weight:900;letter-spacing:.65px;text-transform:uppercase;color:#8a5a16;margin-bottom:7px}
.psl17-hb-note{font-size:10px;color:#7e7468;line-height:1.45;margin-top:8px}
.psl17-hb-row{display:grid;grid-template-columns:86px minmax(0,1fr) auto;gap:8px;align-items:start;padding:8px 0;border-bottom:1px solid #f0e7d9;font-size:11px}
.psl17-hb-row:last-child{border-bottom:0}.psl17-hb-row b{text-align:right;color:#8a5a16}
</style>
'''

js=r'''
<script id="PSL_V17_HB_INFLOW_TRACKING">
(() => {
  const rp17=n=>'Rp '+Number(n||0).toLocaleString('id-ID');
  const esc17=v=>String(v??'').replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
  const opDate17=()=>document.getElementById('operationalDateV5')?.value||new Date().toISOString().slice(0,10);
  const fmt17=d=>{const y=d.getFullYear(),m=String(d.getMonth()+1).padStart(2,'0'),day=String(d.getDate()).padStart(2,'0');return `${y}-${m}-${day}`};

  async function hb17(start,end){
    const all=await dbGetAll('transactions');
    const rows=(all||[]).filter(x=>x&&x.status==='ACTIVE'&&x.type==='hb_add'&&x.date>=start&&x.date<=end)
      .sort((a,b)=>String(a.datetime||a.date).localeCompare(String(b.datetime||b.date)));
    return {rows,total:rows.reduce((a,x)=>a+Number(x.amount||0),0),count:rows.length,start,end};
  }

  function hbBox17(m,title='Pemasukan House Bank'){
    const rows=m.rows.map(x=>`<div class="psl17-hb-row"><span>${esc17(x.date||'-')}</span><span>${esc17(x.note||'Tambah HB')}</span><b>${rp17(x.amount)}</b></div>`).join('')||'<div class="empty-state" style="padding:16px">Belum ada Tambah HB pada periode ini.</div>';
    return `<div class="psl17-hb-box"><div class="psl17-hb-title">${esc17(title)}</div><div class="report-row"><span>Total HB Masuk</span><b class="psl17-hb-value">${rp17(m.total)}</b></div><div class="report-row"><span>Jumlah Tambah HB</span><b>${m.count}</b></div>${rows}<div class="psl17-hb-note">Yang dihitung hanya <b>Tambah HB</b> sebagai modal/kas baru. HB Awal dan rollover Register hari sebelumnya tidak dihitung lagi supaya tidak double count.</div></div>`;
  }

  async function append17(start,end,title){
    const root=document.getElementById('reportContent');if(!root)return;
    root.querySelectorAll('.psl17-hb-box').forEach(x=>x.remove());
    const m=await hb17(start,end);
    const grid=root.querySelector('.psl15-grid');
    if(grid){
      grid.querySelectorAll('.psl17-hb-kpi').forEach(x=>x.remove());
      grid.insertAdjacentHTML('beforeend',`<div class="psl15-kpi psl17-hb-kpi"><span>Total HB Masuk</span><b class="psl17-hb-value">${rp17(m.total)}</b></div>`);
    }
    root.insertAdjacentHTML('beforeend',hbBox17(m,title));
  }

  function ensureDash17(){
    if(document.getElementById('cardHBIn17'))return;
    const stock=document.getElementById('cardTotalStock');
    const grid=stock?.closest('.grid-2');
    if(!grid)return;
    grid.insertAdjacentHTML('beforeend',`<div class="card psl17-hb-card" onclick="window.showHBIn17()"><div class="card-title">HB Masuk</div><div class="card-value psl17-hb-value" id="cardHBIn17">Rp 0</div><div class="card-sub">Tambah HB · tanggal operasional</div></div>`);
  }
  async function renderDash17(){
    ensureDash17();
    const m=await hb17(opDate17(),opDate17());
    const el=document.getElementById('cardHBIn17');if(el)el.textContent=rp17(m.total);
  }
  window.showHBIn17=async function(){
    const d=opDate17(),m=await hb17(d,d);
    document.getElementById('detailTitle').textContent='HB Masuk · '+d;
    document.getElementById('detailContent').innerHTML=hbBox17(m,'Rincian Tambah HB');
    UI.showModal('detail');
  };

  const oldUpdate17=UI.updateDashboard?.bind(UI);
  UI.updateDashboard=async function(){if(oldUpdate17)await oldUpdate17();await renderDash17();};

  const oldDaily17=Reports.generate?.bind(Reports);
  Reports.generate=async function(){
    if(oldDaily17)await oldDaily17();
    const d=opDate17();
    await append17(d,d,'Pemasukan HB · Harian');
  };

  const oldWeekly17=Reports.generateWeekly?.bind(Reports);
  Reports.generateWeekly=async function(){
    if(oldWeekly17)await oldWeekly17();
    const anchor=opDate17(),d=new Date(anchor+'T12:00:00'),day=d.getDay(),delta=day===0?-6:1-day,startD=new Date(d);
    startD.setDate(d.getDate()+delta);const endD=new Date(startD);endD.setDate(startD.getDate()+6);
    await append17(fmt17(startD),fmt17(endD),'Pemasukan HB · Mingguan');
  };

  const oldMonthly17=Reports.generateMonthly?.bind(Reports);
  Reports.generateMonthly=async function(){
    if(oldMonthly17)await oldMonthly17();
    const anchor=opDate17(),[y,m]=anchor.split('-').map(Number),start=`${y}-${String(m).padStart(2,'0')}-01`,end=fmt17(new Date(y,m,0,12));
    await append17(start,end,'Pemasukan HB · Bulanan');
  };

  const oldAll17=Reports.generateAll?.bind(Reports);
  Reports.generateAll=async function(){
    if(oldAll17)await oldAll17();
    const tx=await dbGetAll('transactions'),mv=await dbGetAll('stock_movements');
    const dates=[...(tx||[]).map(x=>x?.date),...(mv||[]).map(x=>x?.date)].filter(Boolean).sort();
    const end=opDate17(),start=dates[0]||end;
    await append17(start,end,'Pemasukan HB · Semua Data');
  };

  let tries=0;const timer=setInterval(async()=>{tries++;if(db&&document.getElementById('cardTotalStock')){clearInterval(timer);await renderDash17();}else if(tries>=50)clearInterval(timer)},100);
  console.log('PSL v17 HB inflow tracking active');
})();
</script>
'''

if '</head>' not in s or '</body>' not in s:
    raise SystemExit('invalid html skeleton')
s=s.replace('</head>',style+'\n</head>',1)
s=s.replace('</body>',js+'\n</body>',1)
p.write_text(s,encoding='utf-8')
print('patched PSL v17 HB inflow tracking')
