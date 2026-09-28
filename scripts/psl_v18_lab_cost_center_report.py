from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V18_LAB_COST_CENTER_REPORT' in s:
    print('PSL v18 lab cost-center report already present')
    raise SystemExit(0)

style=r'''
<style id="PSL_V18_LAB_COST_CENTER_REPORT_STYLE">
.psl18-fund-box{margin:12px 0;padding:13px;border:1px solid #e7dcc8;border-radius:15px;background:linear-gradient(180deg,#fffaf1,#fff)}
.psl18-fund-title{font-size:11px;font-weight:900;letter-spacing:.65px;text-transform:uppercase;color:#76521b;margin-bottom:8px}
.psl18-fund-row{display:flex;justify-content:space-between;gap:12px;padding:8px 0;border-bottom:1px solid #efe6d8;font-size:11px}
.psl18-fund-row:last-of-type{border-bottom:0}.psl18-fund-row b{text-align:right}
.psl18-fund-final{font-size:13px;font-weight:900;padding-top:10px}
.psl18-positive{color:#267447}.psl18-negative{color:#a45d16}
.psl18-note{margin-top:9px;padding:9px 10px;border-radius:10px;background:#fff5df;color:#6f604d;font-size:10px;line-height:1.5}
.psl18-gap{margin-top:7px;font-size:10px;font-weight:800;color:#a45d16}
</style>
'''

js=r'''
<script id="PSL_V18_LAB_COST_CENTER_REPORT">
(() => {
  const rp18=n=>'Rp '+Math.abs(Number(n||0)).toLocaleString('id-ID');
  const signedRp18=n=>(Number(n||0)<0?'-':'')+rp18(n);
  const opDate18=()=>document.getElementById('operationalDateV5')?.value||new Date().toISOString().slice(0,10);
  const fmt18=d=>{const y=d.getFullYear(),m=String(d.getMonth()+1).padStart(2,'0'),day=String(d.getDate()).padStart(2,'0');return `${y}-${m}-${day}`};
  const prev18=d=>{const x=new Date(d+'T12:00:00');x.setDate(x.getDate()-1);return fmt18(x)};

  async function opening18(date){
    const snaps=await dbGetAll('snapshots');
    const prev=(snaps||[]).find(x=>x&&x.date===prev18(date)&&x.type!=='period_end');
    if(prev)return Number(prev.closingRegister??prev.totalRegister??0);

    const tx=await dbGetAll('transactions');
    const active=(tx||[]).filter(x=>x&&x.date===date&&x.status==='ACTIVE');
    const initial=active.filter(x=>x.type==='hb_initial').reduce((a,x)=>a+Number(x.amount||0),0);
    if(initial)return initial;

    const daily=(snaps||[]).filter(x=>x&&x.type!=='period_end');
    if(!daily.length){
      const legacy=await dbGet('settings','openingHB');
      return Number(legacy?.value||0);
    }
    return 0;
  }

  async function funding18(start,end){
    const tx=await dbGetAll('transactions');
    const active=(tx||[]).filter(x=>x&&x.status==='ACTIVE'&&x.date>=start&&x.date<=end);
    const hbAdd=active.filter(x=>x.type==='hb_add').reduce((a,x)=>a+Number(x.amount||0),0);
    const sales=active.filter(x=>x.type==='sales').reduce((a,x)=>a+Number(x.amount||0),0);
    const out=active.filter(x=>x.type==='expense').reduce((a,x)=>a+Number(x.amount||0),0);
    const open=await opening18(start);
    const movement=hbAdd+sales-out;
    const close=open+movement;
    return {open,hbAdd,sales,out,movement,close};
  }

  function renameReport18(root){
    root.querySelectorAll('.psl15-kpi span,.report-row span,.report-label').forEach(el=>{
      const t=(el.textContent||'').trim();
      if(t==='Total Sales')el.textContent='Sales Lab';
      if(t==='Total Pengeluaran')el.textContent='Pengeluaran Lab';
      if(t==='Opening House Bank')el.textContent='Saldo Dana Awal';
      if(t==='Total Register')el.textContent='Saldo Dana Lab';
    });
    root.querySelectorAll('.report-row').forEach(row=>{
      const label=(row.querySelector('span')?.textContent||'').trim();
      if(label==='Selisih Sales - Pengeluaran')row.remove();
    });
  }

  function box18(m,start,end){
    const cls=m.close<0?'psl18-negative':'psl18-positive';
    const gap=m.close<0?`<div class="psl18-gap">Dana operasional belum tertutup: ${rp18(-m.close)}</div>`:'';
    return `<div class="psl18-fund-box">
      <div class="psl18-fund-title">Dana Operasional Lab</div>
      <div class="psl18-fund-row"><span>Saldo Dana Awal</span><b>${signedRp18(m.open)}</b></div>
      <div class="psl18-fund-row"><span>HB Masuk Periode</span><b class="psl18-positive">+${rp18(m.hbAdd)}</b></div>
      <div class="psl18-fund-row"><span>Sales Lab</span><b class="psl18-positive">+${rp18(m.sales)}</b></div>
      <div class="psl18-fund-row"><span>Pengeluaran Lab</span><b class="psl18-negative">-${rp18(m.out)}</b></div>
      <div class="psl18-fund-row psl18-fund-final"><span>Saldo Dana Akhir Periode</span><b class="${cls}">${signedRp18(m.close)}</b></div>
      ${gap}
      <div class="psl18-note"><b>Bukan laporan laba/rugi.</b> Papa Sauce Lab diperlakukan sebagai pusat produksi. Stock Out ke PSK / PSP / PSBK adalah distribusi internal dan tidak dihitung sebagai penjualan Lab. Pembukuan outlet tetap terpisah.</div>
    </div>`;
  }

  async function apply18(start,end){
    const root=document.getElementById('reportContent');if(!root)return;
    renameReport18(root);
    root.querySelectorAll('.psl18-fund-box').forEach(x=>x.remove());
    const m=await funding18(start,end);
    const summary=root.querySelector('.psl15-summary');
    const grid=root.querySelector('.psl15-grid');
    if(grid)grid.insertAdjacentHTML('afterend',box18(m,start,end));
    else {
      const section=root.querySelector('.report-section')||root;
      const title=section.querySelector('.card-title');
      if(title)title.insertAdjacentHTML('afterend',box18(m,start,end));
      else section.insertAdjacentHTML('afterbegin',box18(m,start,end));
    }
  }

  function relabelDashboard18(){
    const sales=document.getElementById('cardTotalSales')?.closest('.card')?.querySelector('.card-title');
    const out=document.getElementById('cardTotalOut')?.closest('.card')?.querySelector('.card-title');
    const reg=document.getElementById('cardTotalRegister')?.closest('.card')?.querySelector('.card-title');
    if(sales)sales.textContent='Sales Lab';
    if(out)out.textContent='Pengeluaran Lab';
    if(reg)reg.textContent='Saldo Dana Lab';
  }

  const oldDaily18=Reports.generate?.bind(Reports);
  Reports.generate=async function(){
    if(oldDaily18)await oldDaily18();
    const d=document.getElementById('reportDate')?.value||opDate18();
    await apply18(d,d);
  };

  const oldWeekly18=Reports.generateWeekly?.bind(Reports);
  Reports.generateWeekly=async function(){
    if(oldWeekly18)await oldWeekly18();
    const anchor=document.getElementById('reportDate')?.value||opDate18(),d=new Date(anchor+'T12:00:00'),day=d.getDay(),delta=day===0?-6:1-day,startD=new Date(d);
    startD.setDate(d.getDate()+delta);const endD=new Date(startD);endD.setDate(startD.getDate()+6);
    await apply18(fmt18(startD),fmt18(endD));
  };

  const oldMonthly18=Reports.generateMonthly?.bind(Reports);
  Reports.generateMonthly=async function(){
    if(oldMonthly18)await oldMonthly18();
    const anchor=document.getElementById('reportDate')?.value||opDate18(),[y,m]=anchor.split('-').map(Number);
    await apply18(`${y}-${String(m).padStart(2,'0')}-01`,fmt18(new Date(y,m,0,12)));
  };

  const oldAll18=Reports.generateAll?.bind(Reports);
  Reports.generateAll=async function(){
    if(oldAll18)await oldAll18();
    const tx=await dbGetAll('transactions'),mv=await dbGetAll('stock_movements');
    const dates=[...(tx||[]).map(x=>x?.date),...(mv||[]).map(x=>x?.date)].filter(Boolean).sort();
    const end=opDate18(),start=dates[0]||end;
    await apply18(start,end);
  };

  relabelDashboard18();
  console.log('PSL v18 lab cost-center reporting active');
})();
</script>
'''

if '</head>' not in s or '</body>' not in s:
    raise SystemExit('invalid html skeleton')
s=s.replace('</head>',style+'\n</head>',1)
s=s.replace('</body>',js+'\n</body>',1)
p.write_text(s,encoding='utf-8')
print('patched PSL v18 lab cost-center report')
