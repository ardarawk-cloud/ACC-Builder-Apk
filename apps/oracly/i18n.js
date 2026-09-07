(function(){
  'use strict';
  var KEY='oracly_language';
  var app=document.getElementById('app');
  if(!app) return;

  var dict={
    id:{
      'Good Morning,':'Selamat Pagi,','Good Afternoon,':'Selamat Siang,','Good Evening,':'Selamat Malam,',
      'Your energy for today is waiting.':'Energi hari ini menantimu.','Daily':'Harian','Tarot':'Tarot','Horoscope':'Horoskop','Match':'Kecocokan',
      'Your Card Today':'Kartu Hari Ini','Tap the card to reveal':'Ketuk kartu untuk membuka','Reveal Card':'Buka Kartu',"Today's Energy":'Energi Hari Ini',
      'Love':'Cinta','Career':'Karier','Money':'Keuangan','Mood':'Suasana Hati','Home':'Beranda','History':'Riwayat','Profile':'Profil','Explore':'Jelajah',
      'Daily Reading':'Ramalan Harian','Today':'Hari Ini','Week':'Minggu','Month':'Bulan','Lucky Number':'Angka Keberuntungan',
      'Lucky Color':'Warna Keberuntungan','Best Time':'Waktu Terbaik','Choose Your Reading':'Pilih Bacaan Tarot','Card of The Day':'Kartu Hari Ini',
      'A simple guidance for today.':'Panduan sederhana untuk hari ini.','Love Reading':'Bacaan Cinta','Insights about your love life.':'Wawasan tentang kehidupan cintamu.',
      'Career Reading':'Bacaan Karier','Guidance for your career.':'Panduan untuk kariermu.','Money Reading':'Bacaan Keuangan','See your financial energy.':'Lihat energi keuanganmu.',
      'Yes / No':'Ya / Tidak','Get a reflective answer.':'Dapatkan jawaban untuk bahan refleksi.','3 Card Reading':'Bacaan 3 Kartu','Past · Present · Future':'Masa Lalu · Sekarang · Masa Depan',
      'Three Card Reading':'Bacaan Tiga Kartu','Choose a Card':'Pilih Kartu','Focus on your question, then choose three cards.':'Fokus pada pertanyaanmu, lalu pilih tiga kartu.',
      'Focus on your question, then choose one card.':'Fokus pada pertanyaanmu, lalu pilih satu kartu.','Shuffle Cards':'Kocok Kartu',
      'Take a moment, focus on your question, then choose naturally.':'Tenangkan diri, fokus pada pertanyaanmu, lalu pilih secara intuitif.',
      'Your Reading':'Hasil Bacaanmu','Your Card':'Kartumu','Past':'Masa Lalu','Present':'Sekarang','Future':'Masa Depan','Reversed':'Terbalik',
      'Use this as a reflection prompt, not a fixed prediction.':'Gunakan ini sebagai bahan refleksi, bukan ramalan yang pasti.',
      'Your cards suggest a theme to reflect on rather than a fixed prediction.':'Kartumu menunjukkan tema untuk direnungkan, bukan ramalan yang pasti.',
      'YES — move forward thoughtfully.':'YA — lanjutkan dengan pertimbangan yang matang.','NO / NOT YET — give the situation more space.':'TIDAK / BELUM — beri situasi ini lebih banyak ruang.',
      'New Reading':'Bacaan Baru','Overall':'Umum','Finance':'Keuangan','Energy':'Energi','Chemistry':'Chemistry','Communication':'Komunikasi','Trust':'Kepercayaan','Long-term':'Jangka Panjang',
      'Save Reading':'Simpan Bacaan','Save Result':'Simpan Hasil','Saved':'Tersimpan','Compatibility':'Kecocokan',
      'Birth Profile':'Profil Kelahiran','Name':'Nama','Date of Birth':'Tanggal Lahir','Time of Birth (Optional)':'Jam Lahir (Opsional)','Gender (Optional)':'Gender (Opsional)',
      'Male':'Pria','Female':'Wanita','Prefer not to say':'Tidak ingin menyebutkan','Generate My Profile':'Buat Profil Saya','Your Profile':'Profilmu','Sun Sign':'Zodiak Matahari',
      'Element':'Elemen','Ruling Planet':'Planet Penguasa','Save to Profile':'Simpan ke Profil','My Readings':'Bacaan Saya','All':'Semua',
      'Your saved readings will appear here.':'Bacaan yang kamu simpan akan muncul di sini.','Saved tarot readings':'Bacaan tarot tersimpan','Your zodiac profile':'Profil zodiakmu',
      'Premium':'Premium','Unlock More with ORACLY+':'Buka Lebih Banyak dengan ORACLY+','Unlimited Tarot Readings':'Bacaan Tarot Tanpa Batas','3/5 Card Spread':'Spread 3/5 Kartu',
      'Weekly & Monthly Horoscope':'Horoskop Mingguan & Bulanan','Detailed Compatibility':'Kecocokan Lebih Detail','Premium Tarot Decks':'Deck Tarot Premium','Reading History':'Riwayat Bacaan','No Ads':'Tanpa Iklan',
      'Start Free Trial':'Mulai Uji Coba Gratis','Purchase flow is not enabled in this MVP build.':'Pembayaran belum diaktifkan pada build MVP ini.',
      'For entertainment and personal reflection.':'Untuk hiburan dan refleksi pribadi.','Find Guidance Every Day':'Temukan Panduan Setiap Hari',
      'Tarot, Horoscope & Daily Insights':'Tarot, Horoskop & Wawasan Harian','One calm space for daily reflection.':'Satu ruang tenang untuk refleksi harian.','Continue':'Lanjutkan',
      'Settings':'Pengaturan','Language':'Bahasa','App Language':'Bahasa Aplikasi','System Default':'Ikuti Bahasa Perangkat','Indonesian':'Indonesia','English':'English','Close':'Tutup',
      'Choose the language used across ORACLY.':'Pilih bahasa yang digunakan di seluruh ORACLY.','Language saved':'Bahasa tersimpan',
      'About ORACLY':'Tentang ORACLY','Reset local data':'Reset data lokal','Clear History':'Hapus Riwayat','Clear reading history?':'Hapus semua riwayat bacaan?',
      'A conversation you have been avoiding may finally bring clarity.':'Percakapan yang selama ini kamu hindari mungkin akhirnya membawa kejelasan.',
      'Protect your peace without closing your heart.':'Jaga ketenanganmu tanpa menutup hati.','A small sign of affection may matter more than a dramatic gesture.':'Tanda kasih sayang kecil bisa lebih berarti daripada gestur besar.',
      'Focus on finishing one important task before starting something new.':'Fokus selesaikan satu hal penting sebelum memulai yang baru.','Your consistency is more valuable than speed today.':'Konsistensimu lebih berharga daripada kecepatan hari ini.',
      'A useful opportunity may appear through a simple conversation.':'Peluang yang berguna bisa muncul dari percakapan sederhana.','Be cautious with impulsive spending today.':'Hati-hati dengan pengeluaran impulsif hari ini.',
      'Review small recurring expenses before making a larger purchase.':'Tinjau pengeluaran kecil yang berulang sebelum melakukan pembelian besar.','Keep money decisions simple and avoid pressure.':'Buat keputusan keuangan tetap sederhana dan hindari tekanan.',
      'A day of progress and inner clarity. Trust the process.':'Hari yang membawa kemajuan dan kejernihan batin. Percayai prosesnya.','Move steadily; your energy improves when your priorities are clear.':'Bergeraklah dengan stabil; energimu membaik saat prioritasmu jelas.',
      'A quieter pace can reveal what actually matters.':'Ritme yang lebih tenang dapat menunjukkan apa yang benar-benar penting.',
      'A powerful day to take action and express your true self.':'Hari yang kuat untuk bertindak dan mengekspresikan dirimu dengan jujur.',
      'Stay observant; small adjustments can create a smoother day.':'Tetap peka; penyesuaian kecil dapat membuat harimu lebih lancar.',
      'Your best results come from patience and selective focus.':'Hasil terbaik datang dari kesabaran dan fokus yang terarah.','Momentum grows when your actions match your values.':'Momentum tumbuh saat tindakanmu selaras dengan nilai yang kamu pegang.',
      'Choose a sustainable pace and make room for rest.':'Pilih ritme yang berkelanjutan dan sisakan ruang untuk beristirahat.'
    }
  };

  function saved(){try{return localStorage.getItem(KEY)||'auto'}catch(e){return 'auto'}}
  function resolved(){var s=saved();if(s!=='auto')return s;var n=(navigator.language||'en').toLowerCase();return n.indexOf('id')===0?'id':'en'}
  function labelFor(code){return code==='id'?'Indonesia':code==='en'?'English':(resolved()==='id'?'Ikuti Bahasa Perangkat':'System Default')}
  function mapText(t){
    if(resolved()!=='id') return t;
    var m=dict.id;
    if(m[t]) return m[t];
    var s=t;
    s=s.replace(/^([A-Za-z]+) energy highlights emotion, relationships and intuition\. Focus on what can grow through steady awareness\.$/, 'Energi $1 menyoroti emosi, hubungan, dan intuisi. Fokus pada hal yang dapat berkembang melalui kesadaran yang konsisten.');
    s=s.replace(/^([A-Za-z]+) energy highlights money, stability and practical matters\. Focus on what can grow through steady awareness\.$/, 'Energi $1 menyoroti keuangan, kestabilan, dan hal-hal praktis. Fokus pada hal yang dapat berkembang melalui kesadaran yang konsisten.');
    s=s.replace(/^([A-Za-z]+) energy highlights thoughts, truth and decisions\. Focus on what can grow through steady awareness\.$/, 'Energi $1 menyoroti pikiran, kebenaran, dan keputusan. Fokus pada hal yang dapat berkembang melalui kesadaran yang konsisten.');
    s=s.replace(/^([A-Za-z]+) energy highlights energy, ambition and creative action\. Focus on what can grow through steady awareness\.$/, 'Energi $1 menyoroti semangat, ambisi, dan tindakan kreatif. Fokus pada hal yang dapat berkembang melalui kesadaran yang konsisten.');
    s=s.replace(/^The ([a-z]+) energy in emotion, relationships and intuition may feel blocked or delayed\. Reassess before pushing\.$/, 'Energi $1 dalam emosi, hubungan, dan intuisi mungkin terasa terhambat atau tertunda. Tinjau kembali sebelum memaksa.');
    s=s.replace(/^The ([a-z]+) energy in money, stability and practical matters may feel blocked or delayed\. Reassess before pushing\.$/, 'Energi $1 dalam keuangan, kestabilan, dan hal praktis mungkin terasa terhambat atau tertunda. Tinjau kembali sebelum memaksa.');
    s=s.replace(/^The ([a-z]+) energy in thoughts, truth and decisions may feel blocked or delayed\. Reassess before pushing\.$/, 'Energi $1 dalam pikiran, kebenaran, dan keputusan mungkin terasa terhambat atau tertunda. Tinjau kembali sebelum memaksa.');
    s=s.replace(/^The ([a-z]+) energy in energy, ambition and creative action may feel blocked or delayed\. Reassess before pushing\.$/, 'Energi $1 dalam semangat, ambisi, dan tindakan kreatif mungkin terasa terhambat atau tertunda. Tinjau kembali sebelum memaksa.');
    s=s.replace(/ can build a meaningful connection when both people respect differences in pace, communication and emotional needs\.$/,' dapat membangun hubungan yang bermakna ketika keduanya menghargai perbedaan ritme, komunikasi, dan kebutuhan emosional.');
    return s;
  }

  function translate(root){
    document.documentElement.lang=resolved()==='id'?'id':'en';
    if(resolved()!=='id') return;
    var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null);
    var nodes=[];while(w.nextNode())nodes.push(w.currentNode);
    nodes.forEach(function(n){
      var raw=n.nodeValue;if(!raw||!raw.trim())return;
      var p=raw.match(/^\s*/)[0],q=raw.match(/\s*$/)[0],core=raw.trim(),next=mapText(core);
      if(next!==core)n.nodeValue=p+next+q;
    });
  }

  function addStyle(){
    if(document.getElementById('oracly-lang-style'))return;
    var s=document.createElement('style');s.id='oracly-lang-style';
    s.textContent='.oracly-sheet{position:fixed;z-index:9999;inset:0;background:#000a;display:flex;align-items:flex-end;justify-content:center}.oracly-sheet-card{width:100%;max-width:460px;background:linear-gradient(#1a1424,#0e0b14);border:1px solid #49375d;border-radius:24px 24px 0 0;padding:20px 18px calc(24px + env(safe-area-inset-bottom));color:#f6efe3;box-shadow:0 -20px 60px #000b}.oracly-sheet-title{font:500 26px Georgia,serif;margin:0 0 6px}.oracly-sheet-sub{color:#aea4bb;font-size:13px;line-height:1.45;margin-bottom:16px}.oracly-lang-option{width:100%;min-height:58px;border:1px solid #392d48;background:#151120;color:#f6efe3;border-radius:14px;padding:10px 12px;margin:8px 0;display:flex;align-items:center;justify-content:space-between;text-align:left}.oracly-lang-option b{display:block}.oracly-lang-option small{color:#aea4bb}.oracly-lang-option.active{border-color:#d9ad56;box-shadow:0 0 0 1px #d9ad56 inset}.oracly-settings-row{margin-top:0}';
    document.head.appendChild(s);
  }

  function openSettings(){
    addStyle();
    var old=document.getElementById('oracly-settings-sheet');if(old)old.remove();
    var id=resolved()==='id';var cur=saved();
    var wrap=document.createElement('div');wrap.className='oracly-sheet';wrap.id='oracly-settings-sheet';
    wrap.innerHTML='<div class="oracly-sheet-card"><div class="oracly-sheet-title">'+(id?'Pengaturan':'Settings')+'</div><div class="oracly-sheet-sub">'+(id?'Pilih bahasa yang digunakan di seluruh ORACLY.':'Choose the language used across ORACLY.')+'</div>'+
      option('auto',id?'Ikuti Bahasa Perangkat':'System Default',id?'Mengikuti bahasa HP':'Follows your phone language',cur)+
      option('id','Indonesia','Bahasa Indonesia',cur)+option('en','English','English',cur)+
      '<button id="oracly-settings-close" class="btn ghostbtn" style="margin-top:12px">'+(id?'Tutup':'Close')+'</button></div>';
    document.body.appendChild(wrap);
    wrap.querySelectorAll('[data-lang]').forEach(function(b){b.onclick=function(){try{localStorage.setItem(KEY,b.getAttribute('data-lang'))}catch(e){} location.reload()}});
    wrap.querySelector('#oracly-settings-close').onclick=function(){wrap.remove()};
    wrap.onclick=function(e){if(e.target===wrap)wrap.remove()};
  }
  function option(code,title,sub,cur){return '<button class="oracly-lang-option '+(cur===code?'active':'')+'" data-lang="'+code+'"><span><b>'+title+'</b><small>'+sub+'</small></span><span>'+(cur===code?'✓':'')+'</span></button>'}

  function injectSettings(){
    var title=app.querySelector('.title');
    if(!title)return;
    var raw=(title.textContent||'').trim();
    if(raw!=='Profile'&&raw!=='Profil')return;
    if(app.querySelector('[data-oracly-settings]'))return;
    var list=app.querySelector('.list');if(!list)return;
    var b=document.createElement('button');b.className='rowbtn oracly-settings-row';b.type='button';b.setAttribute('data-oracly-settings','1');
    var id=resolved()==='id';
    b.innerHTML='<div class="rico">⚙</div><div class="rmain"><div class="rtitle">'+(id?'Pengaturan':'Settings')+'</div><div class="rdesc">'+(id?'Bahasa: ':'Language: ')+labelFor(saved())+'</div></div><div class="chev">›</div>';
    b.onclick=openSettings;list.appendChild(b);
  }

  var busy=false;
  function apply(){if(busy)return;busy=true;try{injectSettings();translate(app)}finally{busy=false}}
  var obs=new MutationObserver(function(){setTimeout(apply,0)});obs.observe(app,{childList:true,subtree:true});
  document.addEventListener('DOMContentLoaded',apply);setTimeout(apply,50);setTimeout(apply,500);
  window.ORACLY_LANGUAGE={get:saved,resolved:resolved,openSettings:openSettings};
})();
