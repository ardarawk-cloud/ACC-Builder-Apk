#!/usr/bin/env python3
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "bwd-native")
java_dir = root / "app/src/main/java/com/baliweddingdj/app"
main = java_dir / "MainActivity.java"
db = java_dir / "WeddingDb.java"

BUSINESS_WA = "6282247972288"
BUSINESS_DISPLAY = "+62 822-4797-2288"

# Replace all currently shipped default/contact destinations.
for path in java_dir.glob("*.java"):
    text = path.read_text()
    text = text.replace("628113813737", BUSINESS_WA)
    text = text.replace("+62 811-3813-737", BUSINESS_DISPLAY)
    path.write_text(text)

# Make the admin UI explicit that this is the business contact.
s = main.read_text()
s = s.replace('Ui.input(this,"WhatsApp number")', 'Ui.input(this,"WhatsApp Business number")')
main.write_text(s)

# Force existing installations onto the business contact through a DB migration.
d = db.read_text()
d = re.sub(r'private static final int DB_VERSION = \d+;', 'private static final int DB_VERSION = 2;', d)
old_upgrade = '@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}'
new_upgrade = '''@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion < 2){
            ContentValues v = new ContentValues();
            v.put("value", "6282247972288");
            db.update("settings", v, "key=?", new String[]{"whatsapp"});
        }
    }'''
if old_upgrade in d:
    d = d.replace(old_upgrade, new_upgrade)
elif 'oldVersion < 2' not in d:
    raise SystemExit('Unexpected WeddingDb.onUpgrade shape; refusing unsafe contact patch')
db.write_text(d)

# Fail closed if any old contact remains in restored source.
joined = "\n".join(p.read_text() for p in java_dir.glob("*.java"))
if "628113813737" in joined or "+62 811-3813-737" in joined:
    raise SystemExit("Old personal WhatsApp number still present after patch")
if BUSINESS_WA not in joined or BUSINESS_DISPLAY not in joined:
    raise SystemExit("Business WhatsApp contact was not applied completely")

print("BWD business WhatsApp contact applied:", BUSINESS_DISPLAY)
