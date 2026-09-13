#!/usr/bin/env python3
import pathlib, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-native')
cloud = root / 'app/src/main/java/com/baliweddingdj/app/BwdCloud.java'
if not cloud.exists():
    raise SystemExit('BwdCloud.java not found')

s = cloud.read_text()

old_field = '    private static volatile boolean initialized=false;\n'
new_field = '    private static volatile boolean initialized=false;\n    private static volatile String lastPostBody="";\n'
if 'lastPostBody' not in s:
    if old_field not in s:
        raise SystemExit('BwdCloud initialized field anchor not found')
    s = s.replace(old_field, new_field, 1)

old_result = '                    int code=post("/v1/admin/devices",body.toString(),adminCode);\n                    a.runOnUiThread(()->toastDialog(a,"Cloud Notifications",code>=200&&code<300?"This device is enrolled for new booking alerts.":"Enrollment failed. Check the admin code or cloud setup."));'
new_result = '                    int code=post("/v1/admin/devices",body.toString(),adminCode);\n                    String responseBody=lastPostBody;\n                    a.runOnUiThread(()->toastDialog(a,"Cloud Notifications",code>=200&&code<300?"This device is enrolled for new booking alerts.":enrollmentFailureMessage(code,responseBody)));'
if old_result in s:
    s = s.replace(old_result, new_result, 1)
elif 'enrollmentFailureMessage(code,responseBody)' not in s:
    raise SystemExit('BwdCloud enrollment result anchor not found')

old_post = '        int code=con.getResponseCode();InputStream in=code>=400?con.getErrorStream():con.getInputStream();if(in!=null){try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){while(r.readLine()!=null){}}}con.disconnect();return code;'
new_post = '        int code=con.getResponseCode();InputStream in=code>=400?con.getErrorStream():con.getInputStream();StringBuilder response=new StringBuilder();if(in!=null){try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)response.append(line);}}lastPostBody=response.toString();con.disconnect();return code;'
if old_post in s:
    s = s.replace(old_post, new_post, 1)
elif 'lastPostBody=response.toString()' not in s:
    raise SystemExit('BwdCloud post response anchor not found')

helper_anchor = '    private static boolean firebaseConfigured(){'
helper = '''    private static String enrollmentFailureMessage(int code,String body){\n        try{\n            String err=new JSONObject(body==null?"":body).optString("error","").trim();\n            if(!err.isEmpty())return "Enrollment failed ("+code+"): "+err;\n        }catch(Exception ignored){}\n        return "Enrollment failed ("+code+").";\n    }\n\n'''
if 'private static String enrollmentFailureMessage' not in s:
    if helper_anchor not in s:
        raise SystemExit('BwdCloud helper anchor not found')
    s = s.replace(helper_anchor, helper + helper_anchor, 1)

cloud.write_text(s)
print('BWD enrollment diagnostics patch applied')
