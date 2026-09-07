#!/usr/bin/env python3
import pathlib, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-native')
main = root / 'app/src/main/java/com/baliweddingdj/app/MainActivity.java'
s = main.read_text()

old_nav = '        addNav("HOME",this::showHome);addNav("PACKAGES",this::showPackages);addNav("BOOK MY WEDDING",()->{bookingStep=1;showBooking();});addNav("PROFILE",this::showProfile);'
new_nav = '        addNav("HOME",this::showHome);addNav("PACKAGES",this::showPackages);addNav("MY BOOKING",this::showProfile);addNav("PROFILE",this::showProfile);'
if old_nav not in s and new_nav not in s:
    raise SystemExit('bottom navigation anchor not found')
s = s.replace(old_nav, new_nav, 1)

old_page = '        col.addView(Ui.title(this,title)); if(subtitle!=null&&!subtitle.isEmpty()){TextView s=Ui.text(this,subtitle,14,Ui.MUTED,false);s.setPadding(0,0,0,Ui.dp(this,12));col.addView(s);}return col;'
new_page = '        if(title!=null&&!title.isEmpty())col.addView(Ui.title(this,title)); if(subtitle!=null&&!subtitle.isEmpty()){TextView s=Ui.text(this,subtitle,14,Ui.MUTED,false);s.setPadding(0,0,0,Ui.dp(this,12));col.addView(s);}return col;'
if old_page not in s and new_page not in s:
    raise SystemExit('page title anchor not found')
s = s.replace(old_page, new_page, 1)

start = s.find('    private void showHome(){')
end = s.find('    private void showPackages(){', start)
if start < 0 or end < 0:
    raise SystemExit('showHome block not found')

new_home = '''    private void showHome(){
        LinearLayout p=page("","");
        LinearLayout hero=Ui.card(this);
        hero.setPadding(Ui.dp(this,22),Ui.dp(this,28),Ui.dp(this,22),Ui.dp(this,24));

        TextView h=Ui.text(this,"Your Wedding.\\nOur Music.",36,Ui.WARM,false);
        h.setLineSpacing(0,1.04f);
        h.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,18));
        hero.addView(h);

        View line=new View(this);
        line.setBackgroundColor(Ui.GOLD);
        LinearLayout.LayoutParams lineLp=new LinearLayout.LayoutParams(Ui.dp(this,54),Ui.dp(this,2));
        lineLp.setMargins(0,0,0,Ui.dp(this,16));
        hero.addView(line,lineLp);

        TextView sub=Ui.text(this,"Premium wedding entertainment in Bali.",15,Ui.MUTED,false);
        sub.setLetterSpacing(.02f);
        sub.setPadding(0,0,0,Ui.dp(this,22));
        hero.addView(sub);

        hero.addView(fullButton("BOOK YOUR DATE",true,()->{bookingStep=1;showBooking();}));
        hero.addView(Ui.space(this,10));
        hero.addView(fullButton("VIEW PACKAGES",false,this::showPackages));
        p.addView(hero);
    }

'''
s = s[:start] + new_home + s[end:]

# Guard the client-only design lock.
required = [
    'Your Wedding.\\nOur Music.',
    'Premium wedding entertainment in Bali.',
    'addNav("MY BOOKING",this::showProfile)',
    'fullButton("BOOK YOUR DATE"',
    'fullButton("VIEW PACKAGES"',
]
for needle in required:
    if needle not in s:
        raise SystemExit(f'missing required client home token: {needle}')
home_after = s[s.find('    private void showHome(){'):s.find('    private void showPackages(){')]
for forbidden in [
    'We Can Make Your Wedding Like A Rose',
    'Your Music.\\nYour Moment.',
    'p.addView(Ui.section(this,"WEDDING ENTERTAINMENT"))',
    'p.addView(Ui.section(this,"CHECK YOUR DATE"))',
    'p.addView(Ui.section(this,"PLAN WITH US"))',
    'p.addView(Ui.section(this,"MORE"))',
]:
    if forbidden in home_after:
        raise SystemExit(f'legacy home content still present: {forbidden}')

main.write_text(s)
print('BWD premium client home patch applied')
print('single_brand_header=', s.count('Ui.text(this,"BALI WEDDING DJ"') == 1)
print('premium_home_copy=', 'Your Wedding.\\nOur Music.' in s)
print('compact_home=', 'We Can Make Your Wedding Like A Rose' not in home_after)
print('my_booking_nav=', 'addNav("MY BOOKING",this::showProfile)' in s)
