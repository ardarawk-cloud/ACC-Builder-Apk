(function(){
'use strict';
var app=document.getElementById('app');
if(!app)return;

function lang(){
  try{if(window.ORACLY_LANGUAGE&&window.ORACLY_LANGUAGE.resolved)return window.ORACLY_LANGUAGE.resolved()}catch(e){}
  try{var s=localStorage.getItem('oracly_language')||'auto';if(s==='id'||s==='en')return s}catch(e){}
  return (navigator.language||'en').toLowerCase().indexOf('id')===0?'id':'en';
}
function isID(){return lang()==='id'}
function hash(str){var h=2166136261;for(var i=0;i<str.length;i++){h^=str.charCodeAt(i);h=Math.imul(h,16777619)}return Math.abs(h)}
function dateKey(){var d=new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0')}
function esc(s){return String(s==null?'':s).replace(/[&<>\"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]})}

/* Fix dynamic tarot meanings. Core renders card name in <b> and meaning in a separate text node beginning with ':'. */
var majorID={
'New beginnings, openness and a leap of faith.':'Awal baru, keterbukaan, dan keberanian untuk melangkah dengan keyakinan.',
'Pause before acting on impulse.':'Berhenti sejenak sebelum bertindak hanya karena dorongan sesaat.',
'Use your skills and resources with intention.':'Gunakan kemampuan dan sumber daya yang kamu miliki dengan tujuan yang jelas.',
'Scattered focus can weaken your progress.':'Fokus yang terpecah dapat melemahkan kemajuanmu.',
'Trust intuition and observe what is not being said.':'Percayai intuisi dan perhatikan hal-hal yang tidak diucapkan secara langsung.',
'Noise may be drowning out your inner voice.':'Terlalu banyak gangguan mungkin menutupi suara batinmu.',
'Growth, comfort and creative abundance surround you.':'Pertumbuhan, kenyamanan, dan kelimpahan kreatif sedang mengelilingimu.',
'Restore your energy before giving more.':'Pulihkan energimu sebelum memberi lebih banyak kepada orang lain.',
'Structure and clear boundaries bring stability.':'Struktur dan batas yang jelas akan membawa kestabilan.',
'Avoid becoming rigid or controlling.':'Hindari menjadi terlalu kaku atau ingin mengendalikan semuanya.',
'Wisdom comes through tradition, study or guidance.':'Kebijaksanaan dapat datang melalui tradisi, pembelajaran, atau bimbingan.',
'Question rules that no longer fit you.':'Tinjau kembali aturan yang sudah tidak sesuai dengan dirimu.',
'Alignment, meaningful choice and deep connection.':'Keselarasan, pilihan yang bermakna, dan hubungan yang mendalam menjadi tema utama.',
'Mixed values need an honest conversation.':'Perbedaan nilai membutuhkan percakapan yang jujur.',
'Focused movement can overcome obstacles.':'Langkah yang terarah dapat membantumu melewati hambatan.',
'Slow down and regain direction.':'Kurangi kecepatan dan temukan kembali arahmu.',
'Quiet courage and patience are your advantage.':'Keberanian yang tenang dan kesabaran adalah kekuatanmu saat ini.',
'Do not force what needs gentleness.':'Jangan memaksakan sesuatu yang sebenarnya membutuhkan kelembutan.',
'Step inward to find clarity before moving.':'Lihat ke dalam diri untuk menemukan kejernihan sebelum melangkah.',
'Isolation may be keeping you stuck.':'Terlalu mengisolasi diri mungkin membuatmu tetap terjebak.',
'A cycle is turning; stay adaptable.':'Sebuah siklus sedang berubah; tetaplah fleksibel menghadapi perubahan.',
'Resistance to change may create friction.':'Menolak perubahan dapat menciptakan gesekan yang tidak perlu.',
'Truth, balance and accountability matter now.':'Kebenaran, keseimbangan, dan tanggung jawab penting untuk diperhatikan sekarang.',
'Check assumptions before judging.':'Periksa kembali asumsi sebelum mengambil kesimpulan.',
'A new perspective is more useful than pushing.':'Sudut pandang baru akan lebih berguna daripada terus memaksakan keadaan.',
'Delay without reflection becomes stagnation.':'Penundaan tanpa refleksi dapat berubah menjadi stagnasi.',
'A necessary ending creates room for transformation.':'Sebuah akhir yang diperlukan akan membuka ruang bagi perubahan besar.',
'Holding on may delay renewal.':'Terus berpegang pada hal lama dapat menunda pembaruan.',
'Balance, moderation and integration lead forward.':'Keseimbangan, pengendalian diri, dan kemampuan menyatukan berbagai hal akan membawamu maju.',
'Extremes are draining your momentum.':'Sikap yang terlalu ekstrem sedang menguras momentummu.',
'Notice attachments, habits and tempting shortcuts.':'Perhatikan keterikatan, kebiasaan, dan jalan pintas yang menggoda.',
'You are ready to loosen an unhealthy pattern.':'Kamu siap melepaskan pola yang tidak sehat.',
'A sudden truth can clear unstable ground.':'Kebenaran yang muncul tiba-tiba dapat membersihkan dasar yang tidak stabil.',
'Avoid rebuilding the same weak foundation.':'Jangan membangun kembali di atas fondasi lemah yang sama.',
'Hope, healing and renewed direction are available.':'Harapan, pemulihan, dan arah baru sedang terbuka untukmu.',
'Reconnect with faith in your own path.':'Bangun kembali keyakinan pada jalanmu sendiri.',
'Not everything is clear yet; move carefully.':'Belum semuanya terlihat jelas; melangkahlah dengan hati-hati.',
'Fear may be exaggerating uncertainty.':'Rasa takut mungkin membuat ketidakpastian terasa lebih besar daripada kenyataannya.',
'Confidence, warmth and visible progress grow.':'Kepercayaan diri, kehangatan, dan kemajuan yang nyata sedang tumbuh.',
'Celebrate without ignoring practical details.':'Nikmati keberhasilan tanpa mengabaikan detail praktis.',
'Reflection brings a chance to answer a higher call.':'Refleksi membuka kesempatan untuk menjawab panggilan yang lebih besar dalam hidupmu.',
'Release old self-criticism and decide.':'Lepaskan kritik lama terhadap diri sendiri lalu ambil keputusan.',
'Completion, integration and earned progress.':'Penyelesaian, kesatuan, dan kemajuan hasil usahamu menjadi tema utama.',
'One final detail still needs closure.':'Masih ada satu detail terakhir yang perlu diselesaikan.'
};
var rankID={Ace:'As',Two:'Dua',Three:'Tiga',Four:'Empat',Five:'Lima',Six:'Enam',Seven:'Tujuh',Eight:'Delapan',Nine:'Sembilan',Ten:'Sepuluh',Page:'Page',Knight:'Ksatria',Queen:'Ratu',King:'Raja'};
var topicID={
'emotion, relationships and intuition':'emosi, hubungan, dan intuisi',
'money, stability and practical matters':'keuangan, kestabilan, dan hal-hal praktis',
'thoughts, truth and decisions':'pikiran, kebenaran, dan keputusan',
'energy, ambition and creative action':'energi, ambisi, dan tindakan kreatif'
};
function translateMeaning(s){
  if(!isID())return s;
  if(majorID[s])return majorID[s];
  var m=s.match(/^(Ace|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten|Page|Knight|Queen|King) energy highlights (emotion, relationships and intuition|money, stability and practical matters|thoughts, truth and decisions|energy, ambition and creative action)\. Focus on what can grow through steady awareness\.$/);
  if(m)return 'Energi '+rankID[m[1]]+' menyoroti '+topicID[m[2]]+'. Fokus pada hal yang dapat berkembang melalui kesadaran yang stabil.';
  m=s.match(/^The (ace|two|three|four|five|six|seven|eight|nine|ten|page|knight|queen|king) energy in (emotion, relationships and intuition|money, stability and practical matters|thoughts, truth and decisions|energy, ambition and creative action) may feel blocked or delayed\. Reassess before pushing\.$/);
  if(m){var key=m[1].charAt(0).toUpperCase()+m[1].slice(1);return 'Energi '+rankID[key]+' pada '+topicID[m[2]]+' mungkin terasa terhambat atau tertunda. Tinjau kembali sebelum memaksakannya.'}
  return s;
}
function fixTarotMeaningNodes(){
  if(!isID())return;
  var boxes=app.querySelectorAll('.reading-text');
  for(var b=0;b<boxes.length;b++){
    var walker=document.createTreeWalker(boxes[b],NodeFilter.SHOW_TEXT,null),nodes=[];
    while(walker.nextNode())nodes.push(walker.currentNode);
    for(var i=0;i<nodes.length;i++){
      var raw=nodes[i].nodeValue||'',trim=raw.trim();
      if(!trim)continue;
      var colon=trim.charAt(0)===':';
      var body=colon?trim.slice(1).trim():trim;
      var trans=translateMeaning(body);
      if(trans!==body){
        var lead=raw.match(/^\s*/)[0],tail=raw.match(/\s*$/)[0];
        nodes[i].nodeValue=lead+(colon?': ':'')+trans+tail;
      }
    }
  }
}

/* Other-person birth-date reader */
var zodiac=[
{name:'Aries',sym:'♈',el:'Fire',planet:'Mars'}, {name:'Taurus',sym:'♉',el:'Earth',planet:'Venus'},
{name:'Gemini',sym:'♊',el:'Air',planet:'Mercury'}, {name:'Cancer',sym:'♋',el:'Water',planet:'Moon'},
{name:'Leo',sym:'♌',el:'Fire',planet:'Sun'}, {name:'Virgo',sym:'♍',el:'Earth',planet:'Mercury'},
{name:'Libra',sym:'♎',el:'Air',planet:'Venus'}, {name:'Scorpio',sym:'♏',el:'Water',planet:'Pluto'},
{name:'Sagittarius',sym:'♐',el:'Fire',planet:'Jupiter'}, {name:'Capricorn',sym:'♑',el:'Earth',planet:'Saturn'},
{name:'Aquarius',sym:'♒',el:'Air',planet:'Uranus'}, {name:'Pisces',sym:'♓',el:'Water',planet:'Neptune'}
];
var signPersonalityID=[
'Berani, spontan, dan cenderung bergerak lebih dulu ketika sudah yakin.',
'StabiI, setia, dan menghargai rasa aman, kenyamanan, serta konsistensi.',
'Cepat menangkap informasi, komunikatif, dan membutuhkan variasi agar tetap tertarik.',
'Peka, protektif, dan kuat dalam membaca suasana emosional orang di sekitarnya.',
'Hangat, ekspresif, dan biasanya berkembang ketika bisa menunjukkan kemampuan terbaiknya.',
'Teliti, realistis, dan cenderung menunjukkan perhatian melalui tindakan yang berguna.',
'Mengutamakan keseimbangan, hubungan yang harmonis, dan keputusan yang terasa adil.',
'Intens, intuitif, dan tidak mudah puas dengan hubungan atau tujuan yang setengah-setengah.',
'Terbuka, optimistis, dan terdorong oleh pengalaman baru serta kebebasan untuk berkembang.',
'Disiplin, bertanggung jawab, dan nyaman membangun sesuatu secara bertahap untuk jangka panjang.',
'Independen, berpikiran maju, dan sering melihat kemungkinan yang tidak terpikirkan orang lain.',
'Empatik, imajinatif, dan kuat dalam merasakan nuansa yang tidak selalu terlihat di permukaan.'
];
var signPersonalityEN=[
'Bold, spontaneous, and likely to move first once convinced.',
'Steady, loyal, and strongly values security, comfort, and consistency.',
'Quick-minded, communicative, and needs variety to stay engaged.',
'Sensitive, protective, and naturally aware of the emotional atmosphere around them.',
'Warm, expressive, and often thrives when able to show their strengths openly.',
'Detail-oriented, practical, and tends to show care through useful actions.',
'Values balance, harmonious relationships, and decisions that feel fair.',
'Intense, intuitive, and rarely satisfied with half-hearted goals or connections.',
'Open, optimistic, and motivated by new experiences and freedom to grow.',
'Disciplined, responsible, and comfortable building slowly for the long term.',
'Independent, forward-looking, and often sees possibilities others miss.',
'Empathetic, imaginative, and tuned into subtle things beneath the surface.'
];
function signIndex(day,month){
  var cuts=[20,19,20,20,21,21,22,22,21,21,20,19];
  var next=[10,11,0,1,2,3,4,5,6,7,8,9];
  var current=[9,10,11,0,1,2,3,4,5,6,7,8];
  return day>=cuts[month-1]?next[month-1]:current[month-1];
}
function lifePath(d,m,y){
  function reduce(n){while(n>9&&n!==11&&n!==22){var s=0;String(n).split('').forEach(function(x){s+=Number(x)});n=s}return n}
  return reduce(reduce(d)+reduce(m)+reduce(y));
}
function planetLabel(p){if(!isID())return p;return {Sun:'Matahari',Moon:'Bulan',Mercury:'Merkurius',Venus:'Venus',Mars:'Mars',Jupiter:'Jupiter',Saturn:'Saturn',Uranus:'Uranus',Neptune:'Neptunus',Pluto:'Pluto'}[p]||p}
function elementLabel(e){if(!isID())return e;return {Fire:'Api',Earth:'Tanah',Air:'Udara',Water:'Air'}[e]||e}
var readingsID={
 overall:['Hari ini orang ini cenderung lebih kuat ketika bergerak dengan ritme yang tenang dan jelas.','Ada dorongan untuk menyelesaikan sesuatu yang lama tertunda; fokus akan lebih berguna daripada terburu-buru.','Energinya hari ini lebih reflektif. Mengamati sebelum bertindak dapat menghasilkan keputusan yang lebih tepat.','Momentum meningkat ketika ia memilih satu prioritas utama dan tidak membagi perhatian terlalu banyak.'],
 love:['Dalam hubungan, komunikasi sederhana dan jujur lebih efektif daripada menebak-nebak perasaan.','Ada kebutuhan untuk merasa dihargai tanpa harus selalu meminta perhatian secara langsung.','Batas yang sehat justru dapat membuat hubungan terasa lebih aman dan nyaman.','Gestur kecil yang konsisten lebih berarti daripada janji besar yang sulit dipenuhi.'],
 career:['Di pekerjaan, hasil terbaik datang dari menyelesaikan satu tugas penting sebelum mengambil beban baru.','Kemampuan praktisnya lebih menonjol hari ini; keputusan berdasarkan fakta akan membantu.','Ada peluang dari percakapan atau koneksi sederhana yang sebelumnya dianggap biasa.','Kecepatan bukan prioritas utama; konsistensi dan ketelitian lebih menguntungkan.'],
 money:['Keuangan lebih aman jika keputusan dibuat tanpa tekanan dan tanpa belanja impulsif.','Baik untuk memeriksa pengeluaran kecil yang berulang sebelum mengambil keputusan besar.','Pendekatan konservatif dan terukur lebih sesuai daripada mengejar hasil cepat.','Fokus pada kestabilan dan kebutuhan nyata akan membantu menjaga keseimbangan keuangan.']
};
var readingsEN={
 overall:['Today this person is strongest when moving at a calm, clear pace.','There is momentum to finish something delayed; focus will help more than rushing.','Their energy is more reflective today. Observing before acting can lead to a better decision.','Momentum improves when they choose one main priority instead of splitting attention.'],
 love:['In relationships, simple honest communication works better than guessing feelings.','There may be a need to feel appreciated without always asking for attention directly.','Healthy boundaries can make a relationship feel safer and more comfortable.','Small consistent gestures matter more than large promises that are hard to keep.'],
 career:['At work, the best result comes from finishing one important task before adding new load.','Practical ability stands out today; fact-based decisions will help.','An opportunity may come through a simple conversation or an overlooked connection.','Speed is not the priority; consistency and accuracy are more useful.'],
 money:['Money decisions are safer when made without pressure or impulse.','A good time to review small recurring expenses before making a larger decision.','A conservative, measured approach fits better than chasing quick results.','Focusing on stability and real needs helps preserve financial balance.']
};
function validDate(d,m,y){var dt=new Date(y,m-1,d);return dt.getFullYear()===y&&dt.getMonth()===m-1&&dt.getDate()===d&&y>=1900&&y<=new Date().getFullYear()}
function readingFor(d,m,y,kind){var sets=isID()?readingsID:readingsEN;var arr=sets[kind];return arr[hash(y+'-'+m+'-'+d+'-'+dateKey()+'-'+kind)%arr.length]}
function injectStyle(){
 if(document.getElementById('oracly-enhance-style'))return;
 var s=document.createElement('style');s.id='oracly-enhance-style';s.textContent='\
#oracly-other-panel{margin:16px 0 18px}.oracly-other-head{display:flex;align-items:center;gap:12px;margin-bottom:12px}.oracly-other-icon{width:44px;height:44px;border-radius:14px;display:grid;place-items:center;background:#221830;border:1px solid #4c3a61;color:#f2d58e;font-size:22px}.oracly-other-grid{display:grid;grid-template-columns:1fr 1fr 1.25fr;gap:8px}.oracly-other-grid label{font-size:10px;color:#b9afc2}.oracly-other-grid input{width:100%;min-height:44px;margin-top:5px;border:1px solid #392d48;background:#0f0c16;color:#f6efe3;border-radius:10px;padding:9px;text-align:center}.oracly-other-name{width:100%;min-height:44px;border:1px solid #392d48;background:#0f0c16;color:#f6efe3;border-radius:10px;padding:10px 12px;margin-bottom:10px}.oracly-other-result{margin-top:14px;border-top:1px solid #30263f;padding-top:14px}.oracly-result-hero{display:flex;align-items:center;gap:12px;margin-bottom:12px}.oracly-result-sym{width:58px;height:58px;flex:0 0 58px;border-radius:50%;display:grid;place-items:center;border:1px solid #8f7042;color:#f2d58e;font-size:30px;background:#100d17}.oracly-mini-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:10px 0}.oracly-mini{background:#100d17;border:1px solid #30263f;border-radius:12px;padding:9px 6px;text-align:center;font-size:9px;color:#aaa1b4}.oracly-mini b{display:block;color:#f2d58e;font-size:12px;margin-top:4px}.oracly-reading-row{margin-top:10px;padding:11px;border:1px solid #30263f;background:#100d17;border-radius:12px}.oracly-reading-row b{display:block;color:#f2d58e;font:500 14px Georgia,serif;margin-bottom:5px}.oracly-reading-row span{display:block;color:#d5ccd9;font-size:12px;line-height:1.5}.oracly-other-error{color:#efb2b2;font-size:11px;margin:8px 0 0}';document.head.appendChild(s);
}
function panelHTML(){
 var id=isID(),yr=new Date().getFullYear();
 return '<section id="oracly-other-panel" class="panel goldline">'+
 '<div class="oracly-other-head"><div class="oracly-other-icon">◉</div><div><h3 class="section-title" style="margin:0">'+(id?'Baca Orang Lain':'Read Someone Else')+'</h3><div class="subtitle">'+(id?'Cukup masukkan tanggal lahirnya. Profil kamu tidak akan berubah.':'Just enter their birth date. Your own profile will not change.')+'</div></div></div>'+
 '<input id="oracly-other-name" class="oracly-other-name" placeholder="'+(id?'Nama (opsional)':'Name (optional)')+'" maxlength="40">'+
 '<div class="oracly-other-grid"><label>'+(id?'Tanggal':'Day')+'<input id="oracly-other-day" type="number" inputmode="numeric" min="1" max="31" placeholder="DD"></label><label>'+(id?'Bulan':'Month')+'<input id="oracly-other-month" type="number" inputmode="numeric" min="1" max="12" placeholder="MM"></label><label>'+(id?'Tahun':'Year')+'<input id="oracly-other-year" type="number" inputmode="numeric" min="1900" max="'+yr+'" placeholder="YYYY"></label></div>'+
 '<div id="oracly-other-error" class="oracly-other-error"></div><button id="oracly-other-read" class="btn goldbtn" style="margin-top:12px">'+(id?'Baca Sekarang':'Read Now')+'</button><div id="oracly-other-result"></div></section>';
}
function injectPanel(){
 injectStyle();
 if(app.querySelector('#oracly-other-panel'))return;
 var home=app.querySelector('.home-head'),menu=app.querySelector('.menu-grid');
 if(!home||!menu)return;
 var wrap=document.createElement('div');wrap.innerHTML=panelHTML();var p=wrap.firstElementChild;menu.insertAdjacentElement('afterend',p);
 var btn=p.querySelector('#oracly-other-read');if(btn)btn.addEventListener('click',runOtherReading);
}
function runOtherReading(){
 var d=parseInt((document.getElementById('oracly-other-day')||{}).value,10),m=parseInt((document.getElementById('oracly-other-month')||{}).value,10),y=parseInt((document.getElementById('oracly-other-year')||{}).value,10);
 var name=((document.getElementById('oracly-other-name')||{}).value||'').trim();var err=document.getElementById('oracly-other-error'),out=document.getElementById('oracly-other-result');if(!out)return;
 if(!validDate(d,m,y)){if(err)err.textContent=isID()?'Tanggal lahir tidak valid.':'Please enter a valid birth date.';out.innerHTML='';return}
 if(err)err.textContent='';
 var idx=signIndex(d,m),z=zodiac[idx],lp=lifePath(d,m,y),display=name||(isID()?'Orang ini':'This person'),person=isID()?signPersonalityID[idx]:signPersonalityEN[idx];
 out.className='oracly-other-result';out.innerHTML='<div class="oracly-result-hero"><div class="oracly-result-sym">'+z.sym+'</div><div><div class="subtitle">'+esc(display)+'</div><div class="title" style="text-align:left;font-size:22px">'+z.name+'</div></div></div>'+
 '<div class="oracly-mini-grid"><div class="oracly-mini">'+(isID()?'Elemen':'Element')+'<b>'+elementLabel(z.el)+'</b></div><div class="oracly-mini">'+(isID()?'Planet':'Planet')+'<b>'+planetLabel(z.planet)+'</b></div><div class="oracly-mini">'+(isID()?'Jalan Hidup':'Life Path')+'<b>'+lp+'</b></div></div>'+
 '<div class="oracly-reading-row"><b>'+(isID()?'Karakter Dasar':'Core Personality')+'</b><span>'+person+'</span></div>'+
 '<div class="oracly-reading-row"><b>'+(isID()?'Energi Hari Ini':'Today\'s Energy')+'</b><span>'+readingFor(d,m,y,'overall')+'</span></div>'+
 '<div class="oracly-reading-row"><b>'+(isID()?'Cinta':'Love')+'</b><span>'+readingFor(d,m,y,'love')+'</span></div>'+
 '<div class="oracly-reading-row"><b>'+(isID()?'Karier':'Career')+'</b><span>'+readingFor(d,m,y,'career')+'</span></div>'+
 '<div class="oracly-reading-row"><b>'+(isID()?'Keuangan':'Money')+'</b><span>'+readingFor(d,m,y,'money')+'</span></div>'+
 '<div class="notice">'+(isID()?'Untuk hiburan dan refleksi pribadi, bukan prediksi yang pasti.':'For entertainment and personal reflection, not a fixed prediction.')+'</div>';
 try{out.scrollIntoView({behavior:'smooth',block:'nearest'})}catch(e){}
}

var busy=false;function apply(){if(busy)return;busy=true;try{injectPanel();fixTarotMeaningNodes()}finally{busy=false}}
new MutationObserver(function(){setTimeout(apply,0)}).observe(app,{childList:true,subtree:true});
setTimeout(apply,50);setTimeout(apply,400);
window.ORACLY_ENHANCEMENTS={apply:apply,translateMeaning:translateMeaning};
})();
