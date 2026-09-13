#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-admin')
main = root / 'app/src/main/java/com/baliweddingdj/app/MainActivity.java'
s = main.read_text()

old = '        EditText due=Ui.input(this,"Payment due date YYYY-MM-DD");\n'
new = r'''        EditText due=Ui.input(this,"Tap to choose payment due date");
        due.setFocusable(false);due.setClickable(true);
        due.setOnClickListener(v->{
            java.util.Calendar cal=java.util.Calendar.getInstance();
            String current=due.getText().toString().trim();
            try{
                String[] parts=current.split("-");
                if(parts.length==3){cal.set(java.util.Calendar.YEAR,Integer.parseInt(parts[0]));cal.set(java.util.Calendar.MONTH,Integer.parseInt(parts[1])-1);cal.set(java.util.Calendar.DAY_OF_MONTH,Integer.parseInt(parts[2]));}
            }catch(Exception ignored){}
            new android.app.DatePickerDialog(this,(view,year,month,day)->due.setText(String.format(java.util.Locale.US,"%04d-%02d-%02d",year,month+1,day)),cal.get(java.util.Calendar.YEAR),cal.get(java.util.Calendar.MONTH),cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
'''
if old not in s:
    if 'DatePickerDialog(this' in s:
        print('BWD Owner due-date picker already applied')
        raise SystemExit(0)
    raise SystemExit('due date field anchor not found')
s = s.replace(old, new, 1)

for token in ['Tap to choose payment due date','DatePickerDialog(this','%04d-%02d-%02d']:
    if token not in s:
        raise SystemExit('owner due-date picker verification failed: '+token)
main.write_text(s)
print('BWD Owner: payment due date now uses calendar picker')
