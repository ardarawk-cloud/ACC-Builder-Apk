// Papa Sauce Lab — Owner-published, read-only boss report (no automatic data exfiltration).
const NO_CACHE={"Cache-Control":"no-store, private, max-age=0","X-Robots-Tag":"noindex, nofollow, noarchive","Referrer-Policy":"no-referrer","X-Content-Type-Options":"nosniff"};
function cors() {return {...NO_CACHE,"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, content-type","Access-Control-Allow-Methods":"POST, OPTIONS"}}
function json(v,status=200,extra={}){return new Response(JSON.stringify(v),{status,headers:{...NO_CACHE,...extra,"Content-Type":"application/json;charset=utf-8"}})}
function secureCompare(a,b){a=String(a||"");b=String(b||"");let diff=a.length^b.length;for(let i=0;i<Math.max(a.length,b.length);i++)diff|=(a.charCodeAt(i)||0)^(b.charCodeAt(i)||0);return diff===0&&a.length>0}
const html=String.raw`<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>Papa Sauce | Laporan Owner</title><style>
:root{font-family:system-ui,Segoe UI,Arial,sans-serif;color:#241a1e;background:#f9f6f4}*{box-sizing:border-box}body{margin:0}header{background:linear-gradient(110deg,#700b2b,#ae1745);padding:25px max(18px,calc((100% - 1150px)/2));color:#fff}header h1{margin:0;font-size:clamp(23px,3vw,34px)}header p{margin:5px 0;color:#ffe4e9}main{max-width:1150px;margin:auto;padding:22px 16px 60px}.bar{display:flex;gap:12px;justify-content:space-between;align-items:center;flex-wrap:wrap}.tag{display:inline-block;padding:6px 12px;border-radius:20px;background:#ffe6ea;color:#802037;font-size:12px;font-weight:800}.notice{background:#fff8e8;border:1px solid #eed5a5;border-radius:12px;padding:12px 15px;font-size:13px;margin:16px 0}.hidden{display:none!important}.kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:18px 0}.metric{border:1px solid #efdedf;background:#fff;border-radius:14px;padding:16px;box-shadow:0 6px 18px #41121b08}.metric b{display:block;color:#891532;font-size:clamp(18px,2.5vw,26px);margin-top:8px}.metric span{font-size:12px;color:#766a6d}.panel{background:#fff;border:1px solid #efe4e3;border-radius:16px;padding:17px;margin:16px 0;overflow:auto}h2{font-size:18px;margin:0 0 14px}h3{font-size:15px;margin:14px 0}input,select{border:1px solid #d8c5c8;border-radius:8px;background:#fff;padding:10px;font-size:14px}label{font-size:13px}table{border-collapse:collapse;width:100%;font-size:13px;white-space:nowrap}td,th{padding:10px 7px;border-bottom:1px solid #f0e8e8;text-align:right}th:first-child,td:first-child{text-align:left}th{background:#fff4f5;color:#7b203a}tbody tr:nth-child(even){background:#fdfaf9}.controls{display:flex;gap:10px;flex-wrap:wrap;align-items:center}.controls label{display:flex;gap:6px;align-items:center}.small{font-size:12px;color:#756d6f}.warn{color:#a54c16;font-weight:700}footer{margin-top:25px;color:#7a6b70;font-size:12px}@media(max-width:700px){.kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.panel{padding:12px}.metric{padding:12px}}
</style></head><body><header><h1>PAPA SAUCE LAB</h1><p>Dashboard Laporan Operasional · Hanya Baca</p></header><main><div class="bar"><span class="tag">AKSES BOS · READ ONLY</span><span id="updated" class="small">Menunggu data...</span></div>
<div id="error" class="notice">Membuka laporan tersimpan...</div><div id="app" class="hidden">
<div class="panel controls"><label>Bulan <select id="month"></select></label><label>Produk Kartu Stok <select id="product"></select></label><span class="small" id="scope"></span></div><div id="audit" class="notice hidden"></div>
<div class="kpis" id="kpis"></div><div class="panel"><h2>Stok Produk LAB</h2><div id="stock"></div></div>
<div class="panel"><h2>Kartu Stok Bulanan</h2><p class="small">Tanggal · Produksi Masuk · Keluar · Sisa. Dihitung dari riwayat yang ada di APK.</p><div id="card"></div></div>
<div class="panel"><h2>Distribusi ke Outlet</h2><div id="outlets"></div></div>
<div class="panel"><h2>Ringkasan Keuangan LAB</h2><div id="finance"></div></div>
<div class="panel"><h2>Aktivitas Harian</h2><div id="daily"></div></div>
</div><footer>Dashboard adalah salinan laporan dari Papa Sauce Lab. Bukan stok real-time; angka berubah setelah Owner mengirim pembaruan dari APK. Laporan negatif atau tidak direkonsiliasi harus diperiksa sebelum digunakan.</footer></main>
<script>
const el=id=>document.getElementById(id), fmt=n=>Number(n||0).toLocaleString('id-ID'), money=n=>'Rp '+fmt(n);
const cell=(tr,v)=>{let td=document.createElement('td');td.textContent=String(v??'–');tr.append(td)};
function table(target,head,rows){target.replaceChildren();let t=document.createElement('table'),h=t.createTHead().insertRow();head.forEach(x=>{let th=document.createElement('th');th.textContent=x;h.append(th)});let b=t.createTBody();rows.forEach(values=>{let tr=b.insertRow();values.forEach(v=>cell(tr,v))});target.append(t)}
let report=null;
function render(){
 const month=el('month').value,p=el('product').value,m=report.months[month];if(!m)return;
 const st=Object.values(m.products||{}), negative=st.some(x=>x.negative||x.closing<0);
 el('audit').classList.toggle('hidden',!negative);if(negative)el('audit').textContent='PERLU AUDIT: terdapat saldo stok negatif atau catatan belum cocok. Jangan anggap saldo sebagai stok fisik terverifikasi.';
 const kpis=[['Produksi',m.production+' pax'],['Stock Out',m.stockOut+' pax'],['Sisa LAB',fmt(st.reduce((n,v)=>n+v.closing,0))+' pax'],['Penjualan LAB',money(m.sales)],['Pengeluaran LAB',money(m.expenses)],['Tambah Dana HB',money(m.hbAdd)],['Jumlah Distribusi',m.distributed+' pax'],['Status','Snapshot Laporan']];
 el('kpis').replaceChildren();for(const [title,value] of kpis){let a=document.createElement('div');a.className='metric';let t=document.createElement('span'),b=document.createElement('b');t.textContent=title;b.textContent=value;a.append(t,b);el('kpis').append(a)}
 table(el('stock'),['Produk','Awal','Masuk','Keluar','Sisa'],st.map(x=>[x.product,fmt(x.opening),fmt(x.incoming),fmt(x.outgoing),fmt(x.closing)]));
 const c=m.products[p];table(el('card'),['Tgl.','Masuk','Keluar','Sisa'],(c?.rows||[]).map(x=>[x.day,x.incoming||'–',x.outgoing||'–',fmt(x.remaining)]));
 table(el('outlets'),['Outlet','Produk','Pax'],(m.outlets||[]).map(x=>[x.outlet,x.product,fmt(x.qty)]));
 table(el('finance'),['Komponen','Nominal'],[['Penjualan LAB',money(m.sales)],['Pengeluaran LAB',money(m.expenses)],['Tambah HB',money(m.hbAdd)],...Object.entries(m.categories||{}).map(([k,v])=>['Biaya: '+k,money(v)])]);
 table(el('daily'),['Tanggal','Produksi','Keluar','Sales LAB','Expenses'],(m.daily||[]).map(x=>[x.day,x.production,x.stockOut,money(x.sales),money(x.expenses)]));
}
(async()=>{
 const key=new URLSearchParams(location.search).get('key')||'';
 if(!key){el('error').textContent='Akses privat diperlukan. Gunakan link laporan yang diberikan Owner.';return}
 try{
 const r=await fetch('/api/report?key='+encodeURIComponent(key),{cache:'no-store'});
 if(!r.ok)throw Error(r.status===404?'Belum ada laporan yang dikirim dari APK.':r.status===403?'Link tidak berlaku / tidak punya akses.':'Gagal mengambil laporan.');
 report=await r.json();if(!report.months||!Object.keys(report.months).length)throw Error('Laporan belum tersedia.');
 const months=Object.keys(report.months).sort().reverse();
 for(const m of months){let op=document.createElement('option');op.value=m;op.textContent=m;el('month').append(op)}
 function products(){el('product').replaceChildren();for(const p of Object.keys(report.months[el('month').value].products||{})){const op=document.createElement('option');op.value=p;op.textContent=p;el('product').append(op)}}
 el('month').addEventListener('change',()=>{products();render()});el('product').addEventListener('change',render);
 products();el('updated').textContent='Terakhir dikirim: '+new Date(report.publishedAt).toLocaleString('id-ID',{timeZone:'Asia/Makassar'});
 el('scope').textContent='Data hingga '+(report.asOf||'?');el('app').classList.remove('hidden');el('error').classList.add('hidden');render();
 }catch(e){el('error').textContent=e.message||'Laporan belum tersedia.'}
})();
</script></body></html>`;
export default {
 async fetch(request,env){
  const url=new URL(request.url),p=url.pathname;
  if(request.method==='OPTIONS'&&p==='/api/publish')return new Response(null,{status:204,headers:cors()});
  if(p==='/api/publish'){
    const supplied=(request.headers.get('Authorization')||'').replace(/^Bearer\s+/i,'').trim();
    if(request.method!=='POST')return json({error:'Method not allowed'},405,cors());
    if(!secureCompare(supplied,env.PUBLISH_TOKEN))return json({error:'Unauthorized'},401,cors());
    const len=Number(request.headers.get('content-length')||0);
    if(len>950000)return json({error:'Payload too large'},413,cors());
    let data;
    try{const raw=await request.text();if(raw.length>950000)throw Error('Data terlalu besar');data=JSON.parse(raw)}catch(e){return json({error:'Invalid JSON or too large'},400,cors())}
    if(data.schema!=='psl-boss-v1'||!data.months||typeof data.months!=='object'||!Object.keys(data.months).length)return json({error:'Invalid report schema'},422,cors());
    const keys=Object.keys(data.months);
    if(keys.length>36||keys.some(k=>!/^\d{4}-\d{2}$/.test(k)))return json({error:'Invalid report month count'},422,cors());
    data.publishedAt=new Date().toISOString();
    data.source='Papa Sauce Lab APK';
    await env.REPORT_KV.put('psl/latest',JSON.stringify(data));
    return json({ok:true,publishedAt:data.publishedAt,shareUrl:url.origin+'/?key='+env.VIEW_TOKEN},200,cors());
  }
  if(p==='/api/report'){
   const key=url.searchParams.get('key')||'';
   if(!secureCompare(key,env.VIEW_TOKEN))return json({error:'No access'},403);
   const latest=await env.REPORT_KV.get('psl/latest');
   if(!latest)return json({error:'No report published yet'},404);
   return new Response(latest,{headers:{...NO_CACHE,'Content-Type':'application/json;charset=utf-8'}});
  }
  if(p==='/'&&request.method==='GET')return new Response(html,{headers:{...NO_CACHE,'Content-Type':'text/html;charset=utf-8','Content-Security-Policy':"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"}});
  return new Response('Not found',{status:404,headers:NO_CACHE});
 }
};
