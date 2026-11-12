// Papa Sauce Lab — Owner-published, read-only boss report (no automatic data exfiltration).
const NO_CACHE={"Cache-Control":"no-store, private, max-age=0","X-Robots-Tag":"noindex, nofollow, noarchive","Referrer-Policy":"no-referrer","X-Content-Type-Options":"nosniff"};
function cors() {return {...NO_CACHE,"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, content-type, x-psl-device, x-psl-timestamp, x-psl-signature","Access-Control-Allow-Methods":"POST, OPTIONS"}}
function json(v,status=200,extra={}){return new Response(JSON.stringify(v),{status,headers:{...NO_CACHE,...extra,"Content-Type":"application/json;charset=utf-8"}})}
function secureCompare(a,b){a=String(a||"");b=String(b||"");let diff=a.length^b.length;for(let i=0;i<Math.max(a.length,b.length);i++)diff|=(a.charCodeAt(i)||0)^(b.charCodeAt(i)||0);return diff===0&&a.length>0}
const PUBLISH_HASH='299f735b27e003920890906eadf07a44b06d3bf3d72c21b66272e3848afbaa5a';
const VIEW_HASH='640b10ab57f5f6746486c1cbb33f203e8d46ca09ac4a9cd113bc7916bf1258cd';
async function tokenDigest(v){const buf=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(String(v||'')));return Array.from(new Uint8Array(buf)).map(x=>x.toString(16).padStart(2,'0')).join('')}

// V36: one-time Owner-approved device enrollment. No shared secret embedded in public APK.
const OWNER_DEVICE_KEY='psl/owner-device';
const SAFE_DEVICE=/^[a-f0-9-]{24,50}$/i;
function bytesFromBase64Url(v){const txt=String(v||'').replace(/-/g,'+').replace(/_/g,'/');const padded=txt+'='.repeat((4-txt.length%4)%4);return Uint8Array.from(atob(padded),c=>c.charCodeAt(0))}
function validPublicJwk(k){return !!k&&k.kty==='EC'&&k.crv==='P-256'&&typeof k.x==='string'&&typeof k.y==='string'&&!k.d&&k.x.length>=40&&k.y.length>=40}
async function publishSnapshot(env,data,origin){
 if(data.schema!=='psl-boss-v1'||!data.months||typeof data.months!=='object'||!Object.keys(data.months).length)return json({error:'Invalid report schema'},422,cors());
 const keys=Object.keys(data.months);if(keys.length>36||keys.some(k=>!/^\d{4}-\d{2}$/.test(k)))return json({error:'Invalid report month count'},422,cors());
 data.publishedAt=new Date().toISOString();data.source='Papa Sauce Lab APK';
 await env.REPORT_KV.put('psl/latest',JSON.stringify(data));
 return json({ok:true,publishedAt:data.publishedAt,baseUrl:origin+'/'},200,cors());
}
async function deviceRoutes(request,env,url){
 const p=url.pathname;
 if(request.method==='OPTIONS')return new Response(null,{status:204,headers:cors()});
 if(p==='/api/device/enroll'){
  if(request.method!=='POST')return json({error:'Method not allowed'},405,cors());
  let data;try{const raw=await request.text();if(raw.length>5000)throw Error();data=JSON.parse(raw)}catch(e){return json({error:'Invalid enrollment data'},400,cors())}
  if(!SAFE_DEVICE.test(data.deviceId)||!validPublicJwk(data.publicKey)||!(/^[a-f0-9]{64}$/i).test(data.viewHash||''))return json({error:'Invalid device details'},422,cors());
  const owner=await env.REPORT_KV.get(OWNER_DEVICE_KEY,'json');
  if(owner?.deviceId===data.deviceId)return json({ok:true,status:'APPROVED'},200,cors());
  if(owner)return json({error:'An Owner device is already active. Contact administrator for replacement.'},403,cors());
  const pending={deviceId:data.deviceId,publicKey:{kty:'EC',crv:'P-256',x:data.publicKey.x,y:data.publicKey.y,ext:true,key_ops:['verify']},viewHash:data.viewHash,requestedAt:new Date().toISOString()};
  await env.REPORT_KV.put('psl/pending/'+data.deviceId,JSON.stringify(pending),{expirationTtl:604800});
  return json({ok:true,status:'PENDING',deviceId:data.deviceId},202,cors());
 }
 if(p==='/api/device/status'){
  const id=url.searchParams.get('id')||'';
  if(!SAFE_DEVICE.test(id))return json({error:'Invalid device id'},400,cors());
  const owner=await env.REPORT_KV.get(OWNER_DEVICE_KEY,'json');
  if(owner?.deviceId===id)return json({status:'APPROVED'},200,cors());
  const pending=await env.REPORT_KV.get('psl/pending/'+id);
  return json({status:pending?'PENDING':'NOT_REGISTERED'},200,cors());
 }
 if(p==='/api/device/publish'){
  if(request.method!=='POST')return json({error:'Method not allowed'},405,cors());
  const id=request.headers.get('x-psl-device')||'',stamp=request.headers.get('x-psl-timestamp')||'',sig=request.headers.get('x-psl-signature')||'';
  const owner=await env.REPORT_KV.get(OWNER_DEVICE_KEY,'json');
  if(!owner||owner.deviceId!==id)return json({error:'Device not approved'},403,cors());
  const ms=Number(stamp);
  if(!Number.isFinite(ms)||Math.abs(Date.now()-ms)>300000)return json({error:'Device time invalid'},401,cors());
  const raw=await request.text();if(raw.length>950000)return json({error:'Payload too large'},413,cors());
  let ok=false;
  try{
   const key=await crypto.subtle.importKey('jwk',owner.publicKey,{name:'ECDSA',namedCurve:'P-256'},false,['verify']);
   ok=await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},key,bytesFromBase64Url(sig),new TextEncoder().encode(stamp+'\n'+raw));
  }catch(e){return json({error:'Signature error'},401,cors())}
  if(!ok)return json({error:'Signature rejected'},401,cors());
  let data;try{data=JSON.parse(raw)}catch(e){return json({error:'Invalid report'},400,cors())}
  return publishSnapshot(env,data,url.origin);
 }
 return json({error:'Not found'},404,cors());
}

const html=String.raw`<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>Papa Sauce Lab | Laporan Operasional</title><style>
:root{font-family:system-ui,Segoe UI,Arial,sans-serif;color:#241a1e;background:#f9f6f4}*{box-sizing:border-box}body{margin:0}header{background:linear-gradient(110deg,#700b2b,#ae1745);padding:25px max(18px,calc((100% - 1150px)/2));color:#fff}header h1{margin:0;font-size:clamp(23px,3vw,34px)}header p{margin:5px 0;color:#ffe4e9}main{max-width:1150px;margin:auto;padding:22px 16px 60px}.bar{display:flex;gap:12px;justify-content:space-between;align-items:center;flex-wrap:wrap}.tag{display:inline-block;padding:6px 12px;border-radius:20px;background:#ffe6ea;color:#802037;font-size:12px;font-weight:800}.notice{background:#fff8e8;border:1px solid #eed5a5;border-radius:12px;padding:12px 15px;font-size:13px;margin:16px 0}.hidden{display:none!important}.kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:18px 0}.metric{border:1px solid #efdedf;background:#fff;border-radius:14px;padding:16px;box-shadow:0 6px 18px #41121b08}.metric b{display:block;color:#891532;font-size:clamp(18px,2.5vw,26px);margin-top:8px}.metric span{font-size:12px;color:#766a6d}.panel{background:#fff;border:1px solid #efe4e3;border-radius:16px;padding:17px;margin:16px 0;overflow:auto}h2{font-size:18px;margin:0 0 14px}h3{font-size:15px;margin:14px 0}input,select{border:1px solid #d8c5c8;border-radius:8px;background:#fff;padding:10px;font-size:14px}label{font-size:13px}table{border-collapse:collapse;width:100%;font-size:13px;white-space:nowrap}td,th{padding:10px 7px;border-bottom:1px solid #f0e8e8;text-align:right}th:first-child,td:first-child{text-align:left}th{background:#fff4f5;color:#7b203a}tbody tr:nth-child(even){background:#fdfaf9}.controls{display:flex;gap:10px;flex-wrap:wrap;align-items:center}.controls label{display:flex;gap:6px;align-items:center}.small{font-size:12px;color:#756d6f}.warn{color:#a54c16;font-weight:700}footer{margin-top:25px;color:#7a6b70;font-size:12px}.compact-list{display:none}.compact-row{background:#fff;border:1px solid #f0e6e7;border-radius:11px;padding:11px 12px;min-width:0}.compact-title{font-size:14px;font-weight:800;line-height:1.4;overflow-wrap:anywhere}.compact-details{font-size:12px;color:#635b5f;margin-top:6px;line-height:1.5;white-space:normal;overflow-wrap:anywhere}
/* PSL dashboard v2: one compact outlet matrix, like the Android LAB report. */
.outlet-matrix .outlet-totals{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin:12px 0 14px}
.outlet-matrix .outlet-total{border:1px solid #e7dcdc;border-radius:14px;text-align:center;padding:11px 4px;background:#fffdfa;min-width:0}
.outlet-matrix .outlet-total span{display:block;color:#84746d;font-size:11px;font-weight:800;letter-spacing:.04em}
.outlet-matrix .outlet-total strong{display:block;color:#841632;font-size:22px;margin-top:4px}
.outlet-matrix .outlet-help{font-size:12px;color:#76696b;line-height:1.45;margin:0 0 12px}
.outlet-matrix table{display:table!important;width:100%;table-layout:fixed;white-space:normal;font-size:13px}
.outlet-matrix th,.outlet-matrix td{padding:10px 5px}
.outlet-matrix th:first-child,.outlet-matrix td:first-child{width:43%;text-align:left;overflow-wrap:anywhere}
.outlet-matrix th:not(:first-child),.outlet-matrix td:not(:first-child){width:19%;text-align:right}
.outlet-matrix tfoot td{font-weight:800;color:#821e39;border-top:2px solid #e5d7d9;background:#fff6f7}
@media(max-width:380px){.outlet-matrix table{font-size:12px}.outlet-matrix .outlet-total strong{font-size:19px}.outlet-matrix th,.outlet-matrix td{padding:9px 4px}}
@media(max-width:700px){main{padding:12px 10px 36px}header{padding:20px 16px}header h1{font-size:23px}header p{font-size:13px}.kpis{grid-template-columns:minmax(0,1fr);gap:7px;margin:12px 0}.panel{padding:13px 11px;margin:11px 0;overflow:hidden;border-radius:13px}.metric{padding:11px 12px;display:flex;align-items:center;justify-content:space-between;gap:12px}.metric b{font-size:18px;margin:0;text-align:right}.metric span{font-size:13px}.controls{display:flex;flex-direction:column;align-items:stretch}.controls label{display:flex;align-items:stretch;flex-direction:column;gap:4px}.controls select{width:100%;min-height:44px}.compact-list{display:grid;grid-template-columns:minmax(0,1fr);gap:7px}.panel table{display:none}.bar{font-size:12px}h2{font-size:18px;margin-bottom:10px}}

/* PSL: stock summary and monthly stock card remain tables, including on phones. */
@media(max-width:700px){
 #stock .compact-list,#card .compact-list{display:none!important}
 #stock table,#card table{display:table!important;width:100%;table-layout:fixed;white-space:normal;font-size:12px}
 #stock th,#stock td,#card th,#card td{padding:10px 5px;text-align:right;overflow-wrap:anywhere}
 #stock th:first-child,#stock td:first-child,#card th:first-child,#card td:first-child{text-align:left}
 #stock th:first-child,#stock td:first-child{width:38%}
 #stock th:not(:first-child),#stock td:not(:first-child){width:15.5%}
 #card th:first-child,#card td:first-child{width:18%}
 #card th:not(:first-child),#card td:not(:first-child){width:27.33%}
}
</style></head><body><header><h1>PAPA SAUCE LAB</h1><p>Dashboard Laporan Operasional</p></header><main><div class="bar"><span id="updated" class="small">Menunggu data...</span></div>
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
const MENU_ORDER=['Ayam Woku','Cumi','Tongkol','Babi Rica','Babi Kecap','Sup Brenebone'];
const OUTLET_ORDER=['PSK','PSP','PSB'];
function stockOrder(a,b){const i=MENU_ORDER.indexOf(a),j=MENU_ORDER.indexOf(b);return (i<0?999:i)-(j<0?999:j)||String(a).localeCompare(String(b))}
function table(target,head,rows,compact){
 target.replaceChildren();
 const t=document.createElement('table'),h=t.createTHead().insertRow();
 head.forEach(x=>{const th=document.createElement('th');th.textContent=x;h.append(th)});
 const body=t.createTBody();
 rows.forEach(values=>{const tr=body.insertRow();values.forEach(v=>cell(tr,v))});
 target.append(t);
 const list=document.createElement('div');list.className='compact-list';
 const items=compact||rows.map(values=>({title:values[0],details:head.slice(1).map((name,i)=>name+': '+String(values[i+1]??'–'))}));
 items.forEach(item=>{
  const box=document.createElement('div');box.className='compact-row';
  const title=document.createElement('div');title.className='compact-title';title.textContent=String(item.title??'');
  const details=document.createElement('div');details.className='compact-details';
  details.textContent=Array.isArray(item.details)?item.details.join(' · '):String(item.details||'');
  box.append(title,details);list.append(box);
 });
 if(!items.length){const empty=document.createElement('div');empty.className='small';empty.textContent='Belum ada data pada periode ini.';list.append(empty)}
 target.append(list);
}
function outletMatrix(target,rows){
 // Display-only grouping. Do not alter persisted report, totals, or stock movements.
 target.replaceChildren();
 const matrix=document.createElement('div');matrix.className='outlet-matrix';
 const grouped=new Map(), totals={PSK:0,PSP:0,PSB:0}, unknown=[];
 for(const entry of rows||[]){
  const name=String(entry.product||'Tanpa Produk');
  const original=String(entry.outlet||'').toUpperCase();
  const outlet=original==='PSBK'?'PSB':original;
  const qty=Number(entry.qty);
  if(!Number.isFinite(qty))continue;
  if(!OUTLET_ORDER.includes(outlet)){unknown.push({outlet:original,product:name,qty});continue}
  if(!grouped.has(name))grouped.set(name,{PSK:0,PSP:0,PSB:0});
  grouped.get(name)[outlet]+=qty;totals[outlet]+=qty;
 }
 const summary=document.createElement('div');summary.className='outlet-totals';
 OUTLET_ORDER.forEach(outlet=>{
  const box=document.createElement('div');box.className='outlet-total';
  const name=document.createElement('span');name.textContent=outlet==='PSB'?'PSBK':outlet;
  const num=document.createElement('strong');num.textContent=fmt(totals[outlet]);
  box.append(name,num);summary.append(box);
 });
 const help=document.createElement('p');help.className='outlet-help';
 help.textContent='Jumlah pengiriman LAB ke setiap outlet pada bulan yang dipilih. Bukan stok milik outlet.';
 matrix.append(help,summary);
 const t=document.createElement('table');t.setAttribute('aria-label','Distribusi LAB per produk dan outlet');
 const h=t.createTHead().insertRow();
 ['PRODUK','PSK','PSP','PSBK'].forEach(text=>{
  const th=document.createElement('th');th.textContent=text;h.append(th);
 });
 const body=t.createTBody();
 const items=[...grouped.entries()].sort((a,b)=>stockOrder(a[0],b[0]));
 for(const [name,counts] of items){
  const tr=body.insertRow();
  [name,...OUTLET_ORDER.map(o=>fmt(counts[o]))].forEach(value=>cell(tr,value));
 }
 const foot=t.createTFoot().insertRow();
 ['TOTAL',...OUTLET_ORDER.map(o=>fmt(totals[o]))].forEach(value=>cell(foot,value));
 matrix.append(t);
 if(!items.length){const p=document.createElement('p');p.className='small';p.textContent='Belum ada pengiriman LAB ke outlet bulan ini.';matrix.append(p)}
 if(unknown.length){
  const additional=unknown.reduce((sum,x)=>sum+x.qty,0);
  const note=document.createElement('p');note.className='outlet-help';
  note.textContent='Stock Out tanpa outlet teridentifikasi: '+fmt(additional)+' pax. Dihitung terpisah, tidak dimasukkan ke PSK/PSP/PSBK.';
  matrix.append(note);
 }
 target.append(matrix);
}
let report=null;
function render(){
 const month=el('month').value,p=el('product').value,m=report.months[month];if(!m)return;
 const st=Object.values(m.products||{}), negative=st.some(x=>x.negative||x.closing<0);
 el('audit').classList.toggle('hidden',!negative);if(negative)el('audit').textContent='PERLU AUDIT: terdapat saldo stok negatif atau catatan belum cocok. Jangan anggap saldo sebagai stok fisik terverifikasi.';
 const kpis=[['Produksi',m.production+' pax'],['Stock Out',m.stockOut+' pax'],['Sisa LAB',fmt(st.reduce((n,v)=>n+v.closing,0))+' pax'],['Penjualan LAB',money(m.sales)],['Pengeluaran LAB',money(m.expenses)],['Tambah Dana HB',money(m.hbAdd)],['Jumlah Distribusi',m.distributed+' pax'],['Status','Snapshot Laporan']];
 el('kpis').replaceChildren();for(const [title,value] of kpis){let a=document.createElement('div');a.className='metric';let t=document.createElement('span'),b=document.createElement('b');t.textContent=title;b.textContent=value;a.append(t,b);el('kpis').append(a)}
 table(el('stock'),['Produk','Awal','Masuk','Keluar','Sisa'],st.slice().sort((a,b)=>stockOrder(a.product,b.product)).map(x=>[x.product,fmt(x.opening),fmt(x.incoming),fmt(x.outgoing),fmt(x.closing)]));
 const c=m.products[p];table(el('card'),['Tgl.','Masuk','Keluar','Sisa'],(c?.rows||[]).map(x=>[x.day,x.incoming||'–',x.outgoing||'–',fmt(x.remaining)]));
 outletMatrix(el('outlets'),m.outlets||[]);
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
 function products(){el('product').replaceChildren();for(const p of Object.keys(report.months[el('month').value].products||{}).sort(stockOrder)){const op=document.createElement('option');op.value=p;op.textContent=p;el('product').append(op)}}
 el('month').addEventListener('change',()=>{products();render()});el('product').addEventListener('change',render);
 products();el('updated').textContent='Terakhir dikirim: '+new Date(report.publishedAt).toLocaleString('id-ID',{timeZone:'Asia/Makassar'});
 el('scope').textContent='Data hingga '+(report.asOf||'?');el('app').classList.remove('hidden');el('error').classList.add('hidden');render();
 }catch(e){el('error').textContent=e.message||'Laporan belum tersedia.'}
})();
</script></body></html>`;
export default {
 async fetch(request,env){
  const url=new URL(request.url),p=url.pathname;
  if(url.hostname==='laporanpapa.nadmo.id' && (request.method==='GET'||request.method==='HEAD') && (p==='/'||p==='')){
   return Response.redirect('https://laporanpsl.nadmo.id/'+url.search,308);
  }
  if(p.startsWith('/api/device/'))return deviceRoutes(request,env,url);
  if(request.method==='OPTIONS'&&p==='/api/publish')return new Response(null,{status:204,headers:cors()});
  if(p==='/api/publish'){
    const supplied=(request.headers.get('Authorization')||'').replace(/^Bearer\s+/i,'').trim();
    if(request.method!=='POST')return json({error:'Method not allowed'},405,cors());
    if(!secureCompare(await tokenDigest(supplied),PUBLISH_HASH))return json({error:'Unauthorized'},401,cors());
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
    return json({ok:true,publishedAt:data.publishedAt,baseUrl:url.origin+'/'},200,cors());
  }
  if(p==='/api/report'){
   const key=url.searchParams.get('key')||'';
   const owner=await env.REPORT_KV.get(OWNER_DEVICE_KEY,'json');
   const digest=await tokenDigest(key);
   if(!secureCompare(digest,VIEW_HASH)&&!secureCompare(digest,owner?.viewHash))return json({error:'No access'},403);
   const latest=await env.REPORT_KV.get('psl/latest');
   if(!latest)return json({error:'No report published yet'},404);
   return new Response(latest,{headers:{...NO_CACHE,'Content-Type':'application/json;charset=utf-8'}});
  }
  if(p==='/'&&request.method==='GET')return new Response(html,{headers:{...NO_CACHE,'Content-Type':'text/html;charset=utf-8','Content-Security-Policy':"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"}});
  return new Response('Not found',{status:404,headers:NO_CACHE});
 }
};
