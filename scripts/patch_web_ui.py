from pathlib import Path
import re

p = Path('web/index.html')
s = p.read_text(encoding='utf-8')

# Hardcode the builder repository. No browser PAT is required anymore.
s = re.sub(
    r"let cfg=JSON\.parse\(localStorage\.getItem\('accGithub'\)\|\|'\{\}'\),current=null,lastRelease=null,pollTimer=null,activeRequest=null;",
    "let cfg={owner:'ardarawk-cloud',repo:'ACC-Builder-Apk',branch:'main',token:''},current=null,lastRelease=null,pollTimer=null,activeRequest=null;",
    s,
)
s = s.replace(
    "function connected(){return !!(cfg.owner&&cfg.repo&&cfg.branch&&cfg.token)}",
    "function connected(){return !!(cfg.owner&&cfg.repo&&cfg.branch)}",
)

# All repository reads are public and must never send the browser token.
s = s.replace(
    "const x=await api(`/contents/${path}?ref=${encodeURIComponent(cfg.branch)}`);",
    "const x=await api(`/contents/${path}?ref=${encodeURIComponent(cfg.branch)}`,{},false);",
)
s = s.replace(
    "const rel=await api(`/releases/tags/build-${encodeURIComponent(requestId)}`);",
    "const rel=await api(`/releases/tags/build-${encodeURIComponent(requestId)}`,{},false);",
)
s = s.replace(
    "const dirs=await api(`/contents/apps?ref=${encodeURIComponent(cfg.branch)}`).catch(()=>[]);",
    "const dirs=await api(`/contents/apps?ref=${encodeURIComponent(cfg.branch)}`,{},false).catch(()=>[]);",
)
s = s.replace(
    "const r=await api('');",
    "const r=await api('',{},false);",
)

# Clear visual distinction between editing an old project and creating a new one.
s = s.replace(
    "$('#projectName').value=meta.name||'';lastRelease=null;",
    "$('#projectName').value=meta.name||'';$('#runBtn').textContent='Apply Change + Build APK';lastRelease=null;",
)
s = s.replace(
    "$('#newBtn').onclick=()=>{stopPoll();current=null;activeRequest=null;",
    "$('#newBtn').onclick=()=>{stopPoll();current=null;$('#runBtn').textContent='Generate + Build APK';activeRequest=null;",
)
s = s.replace(
    "current={id:projectId,name,last_request_id:id};activeRequest={id,projectId};",
    "current={id:projectId,name,last_request_id:id};$('#runBtn').textContent='Apply Change + Build APK';activeRequest={id,projectId};",
)

# Replace direct GitHub Contents write with a pre-filled GitHub Issue.
# The user only taps Submit on GitHub; Actions owns all repo writes using GITHUB_TOKEN.
new_create = r'''async function createRequest(){
  const prompt=$('#prompt').value.trim();
  if(!prompt)return alert('Tulis prompt dulu.');
  const id=reqId();
  const wasEditing=!!current;
  const name=($('#projectName').value.trim()||current?.name||prompt.split(/[.!?]/)[0]||'ACC App').slice(0,50);
  const projectId=current?.id||`${slug(name)}-${id.slice(-6)}`;
  const mode=wasEditing?'modify':'generate';
  const body=[
    `ACC_REQUEST_ID=${id}`,
    `ACC_PROJECT_ID=${projectId}`,
    `ACC_PROJECT_NAME=${name}`,
    `ACC_MODE=${mode}`,
    '',
    'PROMPT:',
    prompt
  ].join('\n');
  const title=`[ACC BUILD] ${name}`;
  const url=`https://github.com/${cfg.owner}/${cfg.repo}/issues/new?title=${encodeURIComponent(title)}&body=${encodeURIComponent(body)}`;
  current={id:projectId,name,last_request_id:id};
  $('#runBtn').textContent='Apply Change + Build APK';
  activeRequest={id,projectId};
  lastRelease=null;
  apkBtn.disabled=true;
  setBuild('waiting',`Request ${id}\nGitHub akan membuka form build.\nTap Submit new issue, lalu kembali ke halaman ini.\nBuilder akan menunggu APK otomatis.`);
  showTab('build');
  startPoll();
  setStatus('Tap Submit di GitHub');
  window.open(url,'_blank');
}
'''
s = re.sub(r"async function createRequest\(\)\{[\s\S]*?\}\nasync function pollRelease", new_create + "async function pollRelease", s, count=1)

# Settings are no longer needed; this button simply opens the repository.
s = s.replace(
    "$('#settingsBtn').onclick=openSettings;",
    "$('#settingsBtn').textContent='GitHub';$('#settingsBtn').onclick=()=>window.open(`https://github.com/${cfg.owner}/${cfg.repo}`,'_blank');",
)

# Rewrite a few user-facing strings so the new flow is obvious.
s = s.replace('GitHub Settings', 'GitHub')
s = s.replace('Token cukup dibatasi ke repository ini dengan <b>Contents: Read and write</b>. Izin Actions tidak diperlukan lagi.', 'Browser tidak memakai Personal Access Token. Build dikirim melalui GitHub Issue dan diproses otomatis oleh GitHub Actions.')
s = s.replace('Prompt → GitHub → APK', 'Prompt → Submit GitHub → APK')

p.write_text(s, encoding='utf-8')
print('ACC AI Builder switched to no-PAT issue trigger')
