from pathlib import Path

p = Path('web/index.html')
s = p.read_text(encoding='utf-8')

# Clear mode labels: selected project = edit, New Project = generate.
s = s.replace(
    "$('#projectName').value=meta.name||'';lastRelease=null;",
    "$('#projectName').value=meta.name||'';$('#runBtn').textContent='Apply Change + Build APK';lastRelease=null;"
)
s = s.replace(
    "$('#newBtn').onclick=()=>{stopPoll();current=null;activeRequest=null;",
    "$('#newBtn').onclick=()=>{stopPoll();current=null;$('#runBtn').textContent='Generate + Build APK';activeRequest=null;"
)
s = s.replace(
    "current={id:projectId,name,last_request_id:id};activeRequest={id,projectId};",
    "current={id:projectId,name,last_request_id:id};$('#runBtn').textContent='Apply Change + Build APK';activeRequest={id,projectId};"
)

# This repo is public. Never attach the fine-grained PAT to read-only calls.
# Keep the PAT only for the PUT that creates a new request file.
s = s.replace(
    "const x=await api(`/contents/${path}?ref=${encodeURIComponent(cfg.branch)}`);",
    "const x=await api(`/contents/${path}?ref=${encodeURIComponent(cfg.branch)}`,{},false);"
)
s = s.replace(
    "const rel=await api(`/releases/tags/build-${encodeURIComponent(requestId)}`);",
    "const rel=await api(`/releases/tags/build-${encodeURIComponent(requestId)}`,{},false);"
)
s = s.replace(
    "const dirs=await api(`/contents/apps?ref=${encodeURIComponent(cfg.branch)}`).catch(()=>[]);",
    "const dirs=await api(`/contents/apps?ref=${encodeURIComponent(cfg.branch)}`,{},false).catch(()=>[]);"
)
s = s.replace(
    "const r=await api('');$('#repoState').textContent=",
    "const r=await api('',{},false);$('#repoState').textContent="
)

p.write_text(s, encoding='utf-8')
print('ACC AI Builder public-read and UI mode patches applied')
