(function(){
'use strict';
var major=[
  'The Fool','The Magician','The High Priestess','The Empress','The Emperor','The Hierophant','The Lovers','The Chariot','Strength','The Hermit','Wheel of Fortune','Justice','The Hanged Man','Death','Temperance','The Devil','The Tower','The Star','The Moon','The Sun','Judgement','The World'
];
var rankNo={Ace:'01',Two:'02',Three:'03',Four:'04',Five:'05',Six:'06',Seven:'07',Eight:'08',Nine:'09',Ten:'10',Page:'11',Knight:'12',Queen:'13',King:'14'};
var suitPrefix={Cups:'cups',Pentacles:'pents',Swords:'swords',Wands:'wands'};
function fileFor(name){
  var mi=major.indexOf(name);
  if(mi>=0)return 'major-'+String(mi).padStart(2,'0')+'.jpg';
  var m=/^(Ace|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten|Page|Knight|Queen|King) of (Cups|Pentacles|Swords|Wands)$/.exec(name);
  if(!m)return null;
  return suitPrefix[m[2]]+'-'+rankNo[m[1]]+'.jpg';
}
function addStyle(){
  if(document.getElementById('oracly-rws-style'))return;
  var s=document.createElement('style');
  s.id='oracly-rws-style';
  s.textContent='\
.face{border-color:#a88245!important;background:#0d0b10!important;box-shadow:0 16px 38px #0009!important}\
.face-art{height:auto!important;aspect-ratio:1115/1920!important;display:block!important;background:#0c0a0e!important;overflow:hidden!important}\
.face-art img.rws-card-image{display:block!important;width:100%!important;height:100%!important;object-fit:cover!important;object-position:center!important}\
.reading-cards.one{max-width:238px!important;margin:18px auto 20px!important}\
.reading-cards.one .face-art{height:auto!important;aspect-ratio:1115/1920!important}\
.face.rws-ready .face-name{display:none!important}\
.reading-cards.one .pos{font-size:12px!important;margin-top:8px!important}\
.rws-fallback{height:100%;display:grid;place-items:center;padding:12px;text-align:center;color:#e6cf91;font:600 13px Georgia,serif;background:linear-gradient(155deg,#241638,#0d0b16 58%,#5e4526)}';
  document.head.appendChild(s);
}
function paintFace(face,index){
  var nameEl=face.querySelector('.face-name');
  var art=face.querySelector('.face-art');
  if(!nameEl||!art)return;
  var name=(nameEl.textContent||'').trim();
  var file=fileFor(name);
  if(!file)return;
  if(art.getAttribute('data-rws-file')===file)return;
  art.setAttribute('data-rws-file',file);
  art.innerHTML='';
  var img=new Image();
  img.className='rws-card-image';
  img.alt=name+' tarot card illustration';
  img.decoding='async';
  img.loading='eager';
  img.onload=function(){face.classList.add('rws-ready')};
  img.onerror=function(){
    face.classList.remove('rws-ready');
    art.innerHTML='<div class="rws-fallback">'+name+'<br><small>Card artwork unavailable</small></div>';
  };
  img.src='tarot/'+file;
  art.appendChild(img);
}
function paintAll(){
  addStyle();
  var faces=document.querySelectorAll('.face');
  for(var i=0;i<faces.length;i++)paintFace(faces[i],i);
}
function boot(){
  addStyle();
  paintAll();
  var root=document.getElementById('app');
  if(!root)return;
  var queued=false;
  var obs=new MutationObserver(function(){
    if(queued)return;
    queued=true;
    requestAnimationFrame(function(){queued=false;paintAll()});
  });
  obs.observe(root,{childList:true,subtree:true});
  window.__ORACLY_RWS_ART={paint:paintAll,fileFor:fileFor,majorCount:major.length};
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot);else boot();
})();

(function(){
  'use strict';
  var KEY='oracly_language';
  var app=document.getElementById('app');
  if(!app)return;
  var idMap={
    'Good Morning,':'Selamat Pagi,','Good Afternoon,':'Selamat Siang,','Good Evening,':'Selamat Malam,','Your energy for today is waiting.':'Energi hari ini menantimu.',
    'Daily':'Harian','Horoscope':'Horoskop','Match':'Kecocokan','Your Card Today':'Kartu Hari Ini','Tap the card to reveal':'Ketuk kartu untuk membuka','Reveal Card':'Buka Kartu',"Today's Energy":'Energi Hari Ini',
    'Love':'Cinta','Career':'Karier','Money':'Keuangan','Mood':'Suasana Hati','Home':'Beranda','History':'Riwayat','Profile':'Profil','Explore':'Jelajah','Daily Reading':'Ramalan Harian',
    'Today':'Hari Ini','Week':'Minggu','Month':'Bulan','Lucky Number':'Angka Keberuntungan','Lucky Color':'Warna Keberuntungan','Best Time':'Waktu Terbaik','Choose Your Reading':'Pilih Bacaan Tarot',
    'Card of The Day':'Kartu Hari Ini','A simple guidance for today.':'Panduan sederhana untuk hari ini.','Love Reading':'Bacaan Cinta','Insights about your love life.':'Wawasan tentang kehidupan cintamu.',
    'Career Reading':'Bacaan Karier','Guidance for your career.':'Panduan untuk kariermu.','Money Reading':'Bacaan Keuangan','See your financial energy.':'Lihat energi keuanganmu.',
    'Yes / No':'Ya / Tidak','Get a reflective answer.':'Dapatkan jawaban untuk bahan refleksi.','3 Card Reading':'Bacaan 3 Kartu','Past · Present · Future':'Masa Lalu · Sekarang · Masa Depan',
    'Three Card Reading':'Bacaan Tiga Kartu','Choose a Card':'Pilih Kartu','Focus on your question, then choose three cards.':'Fokus pada pertanyaanmu, lalu pilih tiga kartu.','Focus on your question, then choose one card.':'Fokus pada pertanyaanmu, lalu pilih satu kartu.',
    'Shuffle Cards':'Kocok Kartu','Take a moment, focus on your question, then choose naturally.':'Tenangkan diri, fokus pada pertanyaanmu, lalu pilih secara intuitif.','Your Reading':'Hasil Bacaanmu','Your Card':'Kartumu',
    'Past':'Masa Lalu','Present':'Sekarang','Future':'Masa Depan','Reversed':'Terbalik','Use this as a reflection prompt, not a fixed prediction.':'Gunakan ini sebagai bahan refleksi, bukan ramalan yang pasti.',
    'Your cards suggest a theme to reflect on rather than a fixed prediction.':'Kartumu menunjukkan tema untuk direnungkan, bukan ramalan yang pasti.','YES — move forward thoughtfully.':'YA — lanjutkan dengan pertimbangan yang matang.',
    'NO / NOT YET — give the situation more space.':'TIDAK / BELUM — beri situasi ini lebih banyak ruang.','New Reading':'Bacaan Baru','Overall':'Umum','Finance':'Keuangan','Energy':'Energi','Communication':'Komunikasi','Trust':'Kepercayaan','Long-term':'Jangka Panjang',
    'Save Reading':'Simpan Bacaan','Save Result':'Simpan Hasil','Saved':'Tersimpan','Compatibility':'Kecocokan','Birth Profile':'Profil Kelahiran','Name':'Nama','Date of Birth':'Tanggal Lahir',
    'Time of Birth (Optional)':'Jam Lahir (Opsional)','Gender (Optional)':'Gender (Opsional)','Male':'Pria','Female':'Wanita','Prefer not to say':'Tidak ingin menyebutkan','Generate My Profile':'Buat Profil Saya',
    'Your Profile':'Profilmu','Sun Sign':'Zodiak Matahari','Element':'Elemen','Ruling Planet':'Planet Penguasa','Save to Profile':'Simpan ke Profil','My Readings':'Bacaan Saya','All':'Semua',
    'Your saved readings will appear here.':'Bacaan yang kamu simpan akan muncul di sini.','Saved tarot readings':'Bacaan tarot tersimpan','Your zodiac profile':'Profil zodiakmu','Premium':'Premium',
    'Unlock More with ORACLY+':'Buka Lebih Banyak dengan ORACLY+','Unlimited Tarot Readings':'Bacaan Tarot Tanpa Batas','3/5 Card Spread':'Spread 3/5 Kartu','Weekly & Monthly Horoscope':'Horoskop Mingguan & Bulanan',
    'Detailed Compatibility':'Kecocokan Lebih Detail','Premium Tarot Decks':'Deck Tarot Premium','Reading History':'Riwayat Bacaan','No Ads':'Tanpa Iklan','Start Free Trial':'Mulai Uji Coba Gratis',
    'Purchase flow is not enabled in this MVP build.':'Pembayaran belum diaktifkan pada build MVP ini.','For entertainment and personal reflection.':'Untuk hiburan dan refleksi pribadi.','Find Guidance Every Day':'Temukan Panduan Setiap Hari',
    'Tarot, Horoscope & Daily Insights':'Tarot, Horoskop & Wawasan Harian','One calm space for daily reflection.':'Satu ruang tenang untuk refleksi harian.','Continue':'Lanjutkan',

    'New beginnings, openness and a leap of faith.':'Awal baru, keterbukaan, dan keberanian mengambil langkah penuh keyakinan.',
    'Pause before acting on impulse.':'Berhenti sejenak sebelum bertindak karena dorongan sesaat.',
    'Use your skills and resources with intention.':'Gunakan kemampuan dan sumber dayamu dengan tujuan yang jelas.',
    'Scattered focus can weaken your progress.':'Fokus yang terpecah dapat menghambat kemajuanmu.',
    'Trust intuition and observe what is not being said.':'Percayai intuisi dan perhatikan hal-hal yang tidak diucapkan.',
    'Noise may be drowning out your inner voice.':'Terlalu banyak gangguan dapat menutupi suara batinmu.',
    'Growth, comfort and creative abundance surround you.':'Pertumbuhan, kenyamanan, dan kelimpahan kreatif sedang mengelilingimu.',
    'Restore your energy before giving more.':'Pulihkan energimu sebelum memberi lebih banyak kepada orang lain.',
    'Structure and clear boundaries bring stability.':'Struktur dan batasan yang jelas akan membawa kestabilan.',
    'Avoid becoming rigid or controlling.':'Hindari menjadi terlalu kaku atau ingin mengendalikan semuanya.',
    'Wisdom comes through tradition, study or guidance.':'Kebijaksanaan dapat datang melalui tradisi, pembelajaran, atau bimbingan.',
    'Question rules that no longer fit you.':'Tinjau kembali aturan yang sudah tidak sesuai dengan dirimu.',
    'Alignment, meaningful choice and deep connection.':'Keselarasan, pilihan yang bermakna, dan hubungan yang mendalam.',
    'Mixed values need an honest conversation.':'Perbedaan nilai membutuhkan percakapan yang jujur.',
    'Focused movement can overcome obstacles.':'Langkah yang terarah dapat membantumu melewati rintangan.',
    'Slow down and regain direction.':'Perlambat langkah dan temukan kembali arahmu.',
    'Quiet courage and patience are your advantage.':'Keberanian yang tenang dan kesabaran adalah kekuatanmu.',
    'Do not force what needs gentleness.':'Jangan memaksakan sesuatu yang membutuhkan kelembutan.',
    'Step inward to find clarity before moving.':'Lihat ke dalam dirimu untuk menemukan kejelasan sebelum melangkah.',
    'Isolation may be keeping you stuck.':'Terlalu mengisolasi diri mungkin membuatmu sulit bergerak maju.',
    'A cycle is turning; stay adaptable.':'Sebuah siklus sedang berubah; tetaplah fleksibel.',
    'Resistance to change may create friction.':'Menolak perubahan dapat menimbulkan hambatan.',
    'Truth, balance and accountability matter now.':'Kebenaran, keseimbangan, dan tanggung jawab menjadi hal penting saat ini.',
    'Check assumptions before judging.':'Periksa kembali asumsi sebelum mengambil kesimpulan.',
    'A new perspective is more useful than pushing.':'Sudut pandang baru akan lebih berguna daripada terus memaksakan keadaan.',
    'Delay without reflection becomes stagnation.':'Penundaan tanpa refleksi dapat berubah menjadi kebuntuan.',
    'A necessary ending creates room for transformation.':'Sebuah akhir yang diperlukan membuka ruang untuk perubahan besar.',
    'Holding on may delay renewal.':'Terus mempertahankan sesuatu dapat menunda pembaruan.',
    'Balance, moderation and integration lead forward.':'Keseimbangan, sikap tidak berlebihan, dan penyatuan akan membawamu maju.',
    'Extremes are draining your momentum.':'Sikap yang terlalu ekstrem sedang menguras momentummu.',
    'Notice attachments, habits and tempting shortcuts.':'Perhatikan keterikatan, kebiasaan, dan jalan pintas yang menggoda.',
    'You are ready to loosen an unhealthy pattern.':'Kamu siap melepaskan pola yang tidak sehat.',
    'A sudden truth can clear unstable ground.':'Kebenaran yang muncul tiba-tiba dapat meruntuhkan dasar yang tidak kokoh.',
    'Avoid rebuilding the same weak foundation.':'Jangan membangun kembali di atas fondasi lemah yang sama.',
    'Hope, healing and renewed direction are available.':'Harapan, pemulihan, dan arah baru sedang terbuka untukmu.',
    'Reconnect with faith in your own path.':'Bangun kembali keyakinan terhadap jalanmu sendiri.',
    'Not everything is clear yet; move carefully.':'Belum semuanya terlihat jelas; melangkahlah dengan hati-hati.',
    'Fear may be exaggerating uncertainty.':'Rasa takut mungkin membuat ketidakpastian terasa lebih besar dari kenyataan.',
    'Confidence, warmth and visible progress grow.':'Kepercayaan diri, kehangatan, dan kemajuan nyata sedang bertumbuh.',
    'Celebrate without ignoring practical details.':'Rayakan kemajuanmu tanpa mengabaikan hal-hal praktis.',
    'Reflection brings a chance to answer a higher call.':'Refleksi memberimu kesempatan untuk menjawab panggilan yang lebih besar.',
    'Release old self-criticism and decide.':'Lepaskan kritik lama terhadap dirimu dan ambil keputusan.',
    'Completion, integration and earned progress.':'Penyelesaian, penyatuan, dan kemajuan yang berhasil kamu raih.',
    'One final detail still needs closure.':'Masih ada satu hal terakhir yang perlu diselesaikan.',

    'A conversation you have been avoiding may finally bring clarity.':'Percakapan yang selama ini kamu hindari mungkin akhirnya membawa kejelasan.','Protect your peace without closing your heart.':'Jaga ketenanganmu tanpa menutup hati.',
    'A small sign of affection may matter more than a dramatic gesture.':'Tanda kasih sayang kecil bisa lebih berarti daripada gestur besar.','Focus on finishing one important task before starting something new.':'Fokus selesaikan satu hal penting sebelum memulai yang baru.',
    'Your consistency is more valuable than speed today.':'Konsistensimu lebih berharga daripada kecepatan hari ini.','A useful opportunity may appear through a simple conversation.':'Peluang yang berguna bisa muncul dari percakapan sederhana.',
    'Be cautious with impulsive spending today.':'Hati-hati dengan pengeluaran impulsif hari ini.','Review small recurring expenses before making a larger purchase.':'Tinjau pengeluaran kecil yang berulang sebelum melakukan pembelian besar.',
    'Keep money decisions simple and avoid pressure.':'Buat keputusan keuangan tetap sederhana dan hindari tekanan.','A day of progress and inner clarity. Trust the process.':'Hari yang membawa kemajuan dan kejernihan batin. Percayai prosesnya.',
    'Move steadily; your energy improves when your priorities are clear.':'Bergeraklah dengan stabil; energimu membaik saat prioritasmu jelas.','A quieter pace can reveal what actually matters.':'Ritme yang lebih tenang dapat menunjukkan apa yang benar-benar penting.',
    'A powerful day to take action and express your true self.':'Hari yang kuat untuk bertindak dan mengekspresikan dirimu dengan jujur.','Stay observant; small adjustments can create a smoother day.':'Tetap peka; penyesuaian kecil dapat membuat harimu lebih lancar.',
    'Your best results come from patience and selective focus.':'Hasil terbaik datang dari kesabaran dan fokus yang terarah.','Momentum grows when your actions match your values.':'Momentum tumbuh saat tindakanmu selaras dengan nilai yang kamu pegang.',
    'Choose a sustainable pace and make room for rest.':'Pilih ritme yang berkelanjutan dan sisakan ruang untuk beristirahat.'
  };
  function saved(){try{return localStorage.getItem(KEY)||'auto'}catch(e){return 'auto'}}
  function resolved(){var s=saved();if(s!=='auto')return s;return (navigator.language||'en').toLowerCase().indexOf('id')===0?'id':'en'}
  function label(code){if(code==='id')return'Indonesia';if(code==='en')return'English';return resolved()==='id'?'Ikuti Bahasa Perangkat':'System Default'}
  function rankId(r){return {Ace:'As',Two:'Dua',Three:'Tiga',Four:'Empat',Five:'Lima',Six:'Enam',Seven:'Tujuh',Eight:'Delapan',Nine:'Sembilan',Ten:'Sepuluh',Page:'Page',Knight:'Knight',Queen:'Queen',King:'King'}[r]||r}
  function mapText(t){
    if(resolved()!=='id')return t;
    if(idMap[t])return idMap[t];
    var s=t;
    s=s.replace(/^(Ace|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten|Page|Knight|Queen|King) energy highlights emotion, relationships and intuition\. Focus on what can grow through steady awareness\.$/,function(_,r){return 'Energi '+rankId(r)+' menyoroti emosi, hubungan, dan intuisi. Fokus pada hal yang dapat berkembang melalui kesadaran yang konsisten.'});
    s=s.replace(/^(Ace|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten|Page|Knight|Queen|King) energy highlights money, stability and practical matters\. Focus on what can grow through steady awareness\.$/,function(_,r){return 'Energi '+rankId(r)+' menyoroti keuangan, kestabilan, dan hal-hal praktis. Fokus pada hal yang dapat berkembang melalui kesadaran yang konsisten.'});
    s=s.replace(/^(Ace|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten|Page|Knight|Queen|King) energy highlights thoughts, truth and decisions\. Focus on what can grow through steady awareness\.$/,function(_,r){return 'Energi '+rankId(r)+' menyoroti pikiran, kebenaran, dan keputusan. Fokus pada hal yang dapat berkembang melalui kesadaran yang konsisten.'});
    s=s.replace(/^(Ace|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten|Page|Knight|Queen|King) energy highlights energy, ambition and creative action\. Focus on what can grow through steady awareness\.$/,function(_,r){return 'Energi '+rankId(r)+' menyoroti semangat, ambisi, dan tindakan kreatif. Fokus pada hal yang dapat berkembang melalui kesadaran yang konsisten.'});
    s=s.replace(/^The (ace|two|three|four|five|six|seven|eight|nine|ten|page|knight|queen|king) energy in emotion, relationships and intuition may feel blocked or delayed\. Reassess before pushing\.$/,function(_,r){return 'Energi '+rankId(r.charAt(0).toUpperCase()+r.slice(1))+' dalam emosi, hubungan, dan intuisi mungkin terasa terhambat atau tertunda. Tinjau kembali sebelum memaksakan keadaan.'});
    s=s.replace(/^The (ace|two|three|four|five|six|seven|eight|nine|ten|page|knight|queen|king) energy in money, stability and practical matters may feel blocked or delayed\. Reassess before pushing\.$/,function(_,r){return 'Energi '+rankId(r.charAt(0).toUpperCase()+r.slice(1))+' dalam keuangan, kestabilan, dan hal-hal praktis mungkin terasa terhambat atau tertunda. Tinjau kembali sebelum memaksakan keadaan.'});
    s=s.replace(/^The (ace|two|three|four|five|six|seven|eight|nine|ten|page|knight|queen|king) energy in thoughts, truth and decisions may feel blocked or delayed\. Reassess before pushing\.$/,function(_,r){return 'Energi '+rankId(r.charAt(0).toUpperCase()+r.slice(1))+' dalam pikiran, kebenaran, dan keputusan mungkin terasa terhambat atau tertunda. Tinjau kembali sebelum memaksakan keadaan.'});
    s=s.replace(/^The (ace|two|three|four|five|six|seven|eight|nine|ten|page|knight|queen|king) energy in energy, ambition and creative action may feel blocked or delayed\. Reassess before pushing\.$/,function(_,r){return 'Energi '+rankId(r.charAt(0).toUpperCase()+r.slice(1))+' dalam semangat, ambisi, dan tindakan kreatif mungkin terasa terhambat atau tertunda. Tinjau kembali sebelum memaksakan keadaan.'});
    return s;
  }
  function translate(){
    document.documentElement.lang=resolved()==='id'?'id':'en';
    if(resolved()!=='id')return;
    var w=document.createTreeWalker(app,NodeFilter.SHOW_TEXT,null),a=[];while(w.nextNode())a.push(w.currentNode);
    a.forEach(function(n){var raw=n.nodeValue;if(!raw||!raw.trim())return;var lead=raw.match(/^\s*/)[0],tail=raw.match(/\s*$/)[0],core=raw.trim(),next=mapText(core);if(next!==core)n.nodeValue=lead+next+tail});
  }
  function style(){if(document.getElementById('oracly-language-style'))return;var s=document.createElement('style');s.id='oracly-language-style';s.textContent='.oracly-sheet{position:fixed;z-index:9999;inset:0;background:#000a;display:flex;align-items:flex-end;justify-content:center}.oracly-sheet-card{width:100%;max-width:460px;background:linear-gradient(#1a1424,#0e0b14);border:1px solid #49375d;border-radius:24px 24px 0 0;padding:20px 18px calc(24px + env(safe-area-inset-bottom));color:#f6efe3;box-shadow:0 -20px 60px #000b}.oracly-sheet-title{font:500 26px Georgia,serif;margin:0 0 6px}.oracly-sheet-sub{color:#aea4bb;font-size:13px;line-height:1.45;margin-bottom:16px}.oracly-lang-option{width:100%;min-height:58px;border:1px solid #392d48;background:#151120;color:#f6efe3;border-radius:14px;padding:10px 12px;margin:8px 0;display:flex;align-items:center;justify-content:space-between;text-align:left}.oracly-lang-option b{display:block}.oracly-lang-option small{color:#aea4bb;display:block}.oracly-lang-option.active{border-color:#d9ad56;box-shadow:0 0 0 1px #d9ad56 inset}';document.head.appendChild(s)}
  function option(code,title,sub,cur){return'<button class="oracly-lang-option '+(cur===code?'active':'')+'" data-lang="'+code+'"><span><b>'+title+'</b><small>'+sub+'</small></span><span>'+(cur===code?'✓':'')+'</span></button>'}
  function openSettings(){
    style();var old=document.getElementById('oracly-settings-sheet');if(old)old.remove();var indo=resolved()==='id',cur=saved(),wrap=document.createElement('div');wrap.className='oracly-sheet';wrap.id='oracly-settings-sheet';
    wrap.innerHTML='<div class="oracly-sheet-card"><div class="oracly-sheet-title">'+(indo?'Pengaturan':'Settings')+'</div><div class="oracly-sheet-sub">'+(indo?'Pilih bahasa yang digunakan di seluruh ORACLY.':'Choose the language used across ORACLY.')+'</div>'+option('auto',indo?'Ikuti Bahasa Perangkat':'System Default',indo?'Mengikuti bahasa HP':'Follows your phone language',cur)+option('id','Indonesia','Bahasa Indonesia',cur)+option('en','English','English',cur)+'<button id="oracly-settings-close" class="btn ghostbtn" style="margin-top:12px">'+(indo?'Tutup':'Close')+'</button></div>';
    document.body.appendChild(wrap);wrap.querySelectorAll('[data-lang]').forEach(function(b){b.onclick=function(){try{localStorage.setItem(KEY,b.getAttribute('data-lang'))}catch(e){}location.reload()}});wrap.querySelector('#oracly-settings-close').onclick=function(){wrap.remove()};wrap.onclick=function(e){if(e.target===wrap)wrap.remove()};
  }
  function inject(){
    var title=app.querySelector('.title');if(!title)return;var t=(title.textContent||'').trim();if(t!=='Profile'&&t!=='Profil')return;if(app.querySelector('[data-oracly-settings]'))return;var list=app.querySelector('.list');if(!list)return;
    var indo=resolved()==='id',b=document.createElement('button');b.type='button';b.className='rowbtn';b.setAttribute('data-oracly-settings','1');b.innerHTML='<div class="rico">⚙</div><div class="rmain"><div class="rtitle">'+(indo?'Pengaturan':'Settings')+'</div><div class="rdesc">'+(indo?'Bahasa: ':'Language: ')+label(saved())+'</div></div><div class="chev">›</div>';b.onclick=openSettings;list.appendChild(b);
  }
  var busy=false;function apply(){if(busy)return;busy=true;try{inject();translate()}finally{busy=false}}
  new MutationObserver(function(){setTimeout(apply,0)}).observe(app,{childList:true,subtree:true});setTimeout(apply,50);setTimeout(apply,500);window.ORACLY_LANGUAGE={get:saved,resolved:resolved,openSettings:openSettings};
})();
