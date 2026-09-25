from pathlib import Path

p=Path('apps/papa-sauce-lab/index.html')
s=p.read_text(encoding='utf-8')

if 'PSL_V18_CLOUD_SYNC_WHATSAPP' in s:
    print('PSL v18 sync + WhatsApp already present')
    raise SystemExit(0)

style=r'''
<style id="PSL_V18_CLOUD_SYNC_WHATSAPP_STYLE">
.psl18-sync-badge{padding:5px 8px;border-radius:999px;font-size:9px;font-weight:900;letter-spacing:.55px;border:1px solid rgba(255,255,255,.24);background:rgba(255,255,255,.12);color:#fff;white-space:nowrap}
.psl18-sync-ok{background:rgba(24,128,74,.25)!important}
.psl18-sync-warn{background:rgba(255,152,0,.24)!important}
.psl18-sync-err{background:rgba(210,45,62,.3)!important}
.psl18-settings-box{padding:13px;border-radius:14px;border:1px solid #e8ddcf;background:#fffaf2;margin-bottom:12px}
.psl18-settings-title{font-size:11px;font-weight:900;letter-spacing:.6px;text-transform:uppercase;color:#7b1730;margin-bottom:7px}
.psl18-settings-note{font-size:11px;color:#726b66;line-height:1.45}
.psl18-wa-btn{background:#168f55!important;color:#fff!important;border-color:#168f55!important}
</style>
'''

js=r'''
<script id="PSL_V18_CLOUD_SYNC_WHATSAPP">
(() => {
  const DEFAULT_API18='__PSL_SYNC_API__';
  const STORES18=['transactions','stock_movements','snapshots','corrections','periods'];
  let syncing18=false;
  let lastSync18=0;

  const api18=()=>{
    const custom=(localStorage.getItem('psl_sync_api_v18')||'').trim().replace(/\/+$/,'');
    const d=(DEFAULT_API18||'').trim().replace(/\/+$/,'');
    return custom || (d.startsWith('__')?'':d);
  };
  const key18=()=>localStorage.getItem('psl_sync_key_v18')||'';
  const recKey18=x=>String(x?.id ?? x?.key ?? x?.date ?? '');
  const ts18=x=>{
    const v=x?.syncUpdatedAt||x?.correctedAt||x?.correctionTime||x?.updatedAt||x?.datetime||x?.time||x?.closedAt||x?.createdAt||x?.date||0;
    if(typeof v==='number') return v;
    const n=Date.parse(v); return Number.isFinite(n)?n:0;
  };

  function ensureBadge18(){
    const actions=document.querySelector('.header-actions'); if(!actions)return;
    let el=document.getElementById('pslSyncBadge18');
    if(!el){el=document.createElement('span');el.id='pslSyncBadge18';el.className='psl18-sync-badge psl18-sync-warn';el.textContent='SYNC SETUP';actions.insertBefore(el,actions.firstChild);}
  }
  function badge18(text,kind='warn'){
    ensureBadge18();const el=document.getElementById('pslSyncBadge18');if(!el)return;
    el.textContent=text;el.className='psl18-sync-badge '+(kind==='ok'?'psl18-sync-ok':kind==='err'?'psl18-sync-err':'psl18-sync-warn');
  }

  async function collect18(){
    const records=[];
    for(const store of STORES18){
      let rows=[];try{rows=await dbGetAll(store)}catch(_){rows=[]}
      for(const row of rows||[]){
        const key=recKey18(row);if(!key)continue;
        records.push({store,key,payload:row,ts:ts18(row)});
      }
    }
    return records;
  }

  async function request18(path,opts={}){
    const api=api18(),key=key18();
    if(!api||!key)throw new Error('SYNC_NOT_CONFIGURED');
    const res=await fetch(api+path,{
      ...opts,
      headers:{'content-type':'application/json','x-psl-sync-key':key,...(opts.headers||{})}
    });
    if(!res.ok)throw new Error('HTTP_'+res.status);
    return res.json();
  }

  async function merge18(remote){
    const localMaps={};
    for(const store of STORES18){
      try{
        const rows=await dbGetAll(store);localMaps[store]=new Map((rows||[]).map(x=>[recKey18(x),x]));
      }catch(_){localMaps[store]=new Map()}
    }
    let pulled=0;
    for(const rec of remote||[]){
      if(!STORES18.includes(rec?.store)||!rec?.payload)continue;
      const key=String(rec.key||'');if(!key)continue;
      const local=localMaps[rec.store].get(key);
      if(!local || Number(rec.ts||0)>=ts18(local)){
        await dbPut(rec.store,rec.payload);pulled++;
      }
    }
    return pulled;
  }

  async function refreshAfterSync18(){
    try{
      if(UI.updateDashboard)await UI.updateDashboard();
      if(UI.renderTodayTransactions)await UI.renderTodayTransactions();
      if(UI.renderStock)await UI.renderStock();
      if(UI.renderHistory)await UI.renderHistory();
    }catch(_){}
  }

  window.syncNow18=async function(silent=false){
    if(syncing18)return;
    if(!api18()||!key18()){badge18('SYNC SETUP','warn');if(!silent)alert('Masukkan Sync Code di Pengaturan sekali saja.');return;}
    if(!navigator.onLine){badge18('OFFLINE','warn');return;}
    syncing18=true;badge18('SYNC...','warn');
    try{
      const local=await collect18();
      const push=await request18('/sync/push',{method:'POST',body:JSON.stringify({records:local})});
      const pull=await request18('/sync/pull');
      const pulled=await merge18(pull.records||[]);
      lastSync18=Date.now();
      badge18('SYNC OK','ok');
      await refreshAfterSync18();
      if(!silent)alert('Sync selesai · '+Number(push.accepted||0)+' terkirim · '+pulled+' diterima');
    }catch(e){
      badge18('SYNC ERROR','err');
      if(!silent)alert('Sync gagal. Cek internet / Sync Code.');
      console.error('PSL sync failed',e);
    }finally{syncing18=false}
  };

  window.saveSyncSettings18=async function(){
    const code=(document.getElementById('pslSyncKey18')?.value||'').trim();
    const api=(document.getElementById('pslSyncApi18')?.value||'').trim();
    if(code)localStorage.setItem('psl_sync_key_v18',code);
    else localStorage.removeItem('psl_sync_key_v18');
    if(api)localStorage.setItem('psl_sync_api_v18',api.replace(/\/+$/,''));
    else localStorage.removeItem('psl_sync_api_v18');
    await syncNow18(false);
  };

  UI.showSettings=function(){
    const api=api18();
    detailTitle.textContent='Pengaturan';
    detailContent.innerHTML=`
      <div class="form-group">
        <label class="form-label">Role</label>
        <select class="form-select" id="settingRole" onchange="Settings.setRole(this.value)">
          <option value="OWNER" ${currentRole==='OWNER'?'selected':''}>OWNER</option>
          <option value="STAFF" ${currentRole==='STAFF'?'selected':''}>STAFF</option>
        </select>
      </div>
      <div class="psl18-settings-box">
        <div class="psl18-settings-title">Cloud Sync HP + Desktop</div>
        <div class="psl18-settings-note">Masukkan Sync Code sekali di HP dan PC. Setelah itu transaksi, stock, HB, outlet, koreksi, dan laporan akan tersinkron otomatis.</div>
      </div>
      <div class="form-group">
        <label class="form-label">Sync Code</label>
        <input class="form-input" id="pslSyncKey18" type="password" value="${key18()?esc18(key18()):''}" placeholder="Sync Code">
      </div>
      <div class="form-group" style="${api?'display:none':''}">
        <label class="form-label">Sync Server</label>
        <input class="form-input" id="pslSyncApi18" value="${esc18(api)}" placeholder="https://...">
      </div>
      <button class="btn btn-primary btn-block" onclick="saveSyncSettings18()" style="margin-bottom:8px">☁️ Simpan & Sync Sekarang</button>
      <button class="btn btn-secondary btn-block" onclick="syncNow18(false)">↻ Sync Sekarang</button>
      <p style="font-size:10px;color:#777;margin-top:10px">Jika internet putus, data tetap tersimpan lokal dan akan disinkronkan saat koneksi kembali.</p>
    `;
    UI.showModal('detail');
  };

  function esc18(v){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}

  window.shareCurrentReportWA18=async function(){
    const root=document.getElementById('reportContent');
    if(!root)return;
    if(!root.innerText.trim() && Reports.generate)await Reports.generate();
    const date=document.getElementById('operationalDateV5')?.value||'';
    const text='PAPA SAUCE LAB · LAPORAN\nTanggal acuan: '+date+'\n\n'+root.innerText.trim();
    const url='https://wa.me/?text='+encodeURIComponent(text);
    window.open(url,'_blank');
  };

  function ensureWA18(){
    const tab=document.getElementById('tab-laporan');if(!tab||document.getElementById('pslWaReport18'))return;
    const date=document.getElementById('reportDate');
    const wrap=date?.parentElement?.querySelector('div[style*="display:flex"]');
    if(wrap)wrap.insertAdjacentHTML('beforeend','<button id="pslWaReport18" class="btn btn-sm psl18-wa-btn" onclick="shareCurrentReportWA18()">WhatsApp</button>');
  }

  const oldWA18=Export.whatsapp?.bind(Export);
  Export.whatsapp=async function(){await shareCurrentReportWA18()};

  window.addEventListener('online',()=>syncNow18(true));
  document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')syncNow18(true)});

  let boot18=0;
  const timer18=setInterval(async()=>{
    boot18++;
    if(typeof db!=='undefined'&&db){
      clearInterval(timer18);ensureBadge18();ensureWA18();
      if(api18()&&key18())await syncNow18(true);else badge18('SYNC SETUP','warn');
      setInterval(()=>{if(api18()&&key18()&&Date.now()-lastSync18>12000)syncNow18(true)},15000);
    }else if(boot18>60)clearInterval(timer18);
  },100);

  console.log('PSL v18 cloud sync + WhatsApp active');
})();
</script>
'''

if '</head>' not in s or '</body>' not in s:
    raise SystemExit('invalid html')
s=s.replace('</head>',style+'\n</head>',1)
s=s.replace('</body>',js+'\n</body>',1)
p.write_text(s,encoding='utf-8')
print('patched PSL v18 cloud sync + WhatsApp')
