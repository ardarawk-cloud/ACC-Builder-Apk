#!/usr/bin/env python3
import pathlib, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-native')
main = root / 'app/src/main/java/com/baliweddingdj/app/MainActivity.java'
s = main.read_text()

admin_line = '        p.addView(Ui.section(this,"ADMIN"));p.addView(fullButton(adminMode?"OPEN ADMIN PANEL":"ADMIN ACCESS",false,adminMode?this::showAdmin:this::showAdminAuth));\n'
s = s.replace(admin_line, '')

s = s.replace(
    'pm.optInt("verified")==1?"Verified":"Uploaded · Waiting for admin verification"',
    'pm.optInt("verified")==1?"Verified":"Saved on this device · secure admin sync pending"'
)
s = s.replace(
    'toast("Receipt uploaded. Payment is waiting for admin verification.");',
    'toast("Receipt saved on this device. Secure admin sync is not active in this build yet.");'
)

# Customer APK must not expose a locally self-provisioned admin vault.
s = s.replace(
    'new AlertDialog.Builder(this).setTitle("Set Device Admin Vault")',
    'new AlertDialog.Builder(this).setTitle("Admin tools unavailable in customer app")'
)

main.write_text(s)
print('BWD public client safety patch applied')
print('admin_profile_entry_removed=', 'ADMIN ACCESS' not in s)
print('receipt_copy_is_truthful=', 'secure admin sync pending' in s)
