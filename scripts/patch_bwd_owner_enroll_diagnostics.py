#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-admin')
cloud = root / 'app/src/main/java/com/baliweddingdj/app/BwdCloud.java'
text = cloud.read_text()

old = '''                    int code=post("/v1/admin/devices",body.toString(),adminCode);\n                    a.runOnUiThread(()->toastDialog(a,"Cloud Notifications",code>=200&&code<300?"This device is enrolled for new booking alerts.":"Enrollment failed. Check the admin code or cloud setup."));'''
new = '''                    HttpResult result=postDetailed("/v1/admin/devices",body.toString(),adminCode);\n                    a.runOnUiThread(()->toastDialog(a,"Cloud Notifications",result.code>=200&&result.code<300?"This device is enrolled for new booking alerts.":("Enrollment failed · HTTP "+result.code+" · "+result.body)));'''
if old not in text:
    raise SystemExit('enrollment response anchor not found')
text = text.replace(old, new, 1)

anchor = '''    private static int post(String path,String json,String adminCode)throws Exception{'''
helper = '''    private static final class HttpResult{\n        final int code; final String body;\n        HttpResult(int code,String body){this.code=code;this.body=body==null?"":body;}\n    }\n\n    private static HttpResult postDetailed(String path,String json,String adminCode)throws Exception{\n        String base=BuildConfig.BWD_CLOUD_BASE_URL;\n        while(base.endsWith("/"))base=base.substring(0,base.length()-1);\n        HttpURLConnection con=(HttpURLConnection)new URL(base+path).openConnection();\n        con.setConnectTimeout(12000);con.setReadTimeout(15000);con.setRequestMethod("POST");con.setDoOutput(true);\n        con.setRequestProperty("Content-Type","application/json; charset=utf-8");\n        if(adminCode!=null)con.setRequestProperty("X-BWD-Admin-Enroll",adminCode);\n        try(OutputStream out=con.getOutputStream()){out.write(json.getBytes(StandardCharsets.UTF_8));}\n        int code=con.getResponseCode();\n        InputStream in=code>=400?con.getErrorStream():con.getInputStream();\n        StringBuilder body=new StringBuilder();\n        if(in!=null){try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)body.append(line);}}\n        con.disconnect();\n        String safe=body.toString(); if(safe.length()>220)safe=safe.substring(0,220);\n        return new HttpResult(code,safe);\n    }\n\n'''
if anchor not in text:
    raise SystemExit('post method anchor not found')
text = text.replace(anchor, helper + anchor, 1)
cloud.write_text(text)
print('BWD Owner enrollment diagnostics patch applied')
