from pathlib import Path

p = Path('web/index.html')
s = p.read_text(encoding='utf-8')

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

p.write_text(s, encoding='utf-8')
print('ACC AI Builder UI mode labels patched')
