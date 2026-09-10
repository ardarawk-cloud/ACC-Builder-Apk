#!/usr/bin/env python3
import pathlib, sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'bwd-native')
main = root / 'app/src/main/java/com/baliweddingdj/app/MainActivity.java'
s = main.read_text()

s = s.replace('addNav("BOOK MY WEDDING",()->{bookingStep=1;showBooking();});', 'addNav("MY BOOKING",this::showProfile);')
anchor = 'import android.graphics.Canvas;\n'
extra = ('import android.graphics.Bitmap;\n'
         'import android.graphics.BitmapFactory;\n'
         'import android.graphics.Color;\n'
         'import android.graphics.drawable.GradientDrawable;\n'
         'import android.widget.Space;\n')
if 'import android.graphics.Bitmap;' not in s:
    s = s.replace(anchor, anchor + extra, 1)

s = s.replace('    private LinearLayout bottom;\n', '    private LinearLayout bottom;\n    private TextView brandBar;\n', 1)
s = s.replace('        TextView brand=Ui.text(this,"BALI WEDDING DJ",18,Ui.WARM,true);brand.setLetterSpacing(.12f);brand.setGravity(Gravity.CENTER_VERTICAL);brand.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),0);\n        root.addView(brand,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));',
'''        brandBar=Ui.text(this,"BALI WEDDING DJ",18,Ui.WARM,true);brandBar.setLetterSpacing(.12f);brandBar.setGravity(Gravity.CENTER_VERTICAL);brandBar.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),0);\n        root.addView(brandBar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,54)));''', 1)

old_page = '        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout col=Ui.column(this);col.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,30));sc.addView(col);content.removeAllViews();content.addView(sc,new FrameLayout.LayoutParams(-1,-1));\n        col.addView(Ui.title(this,title)); if(subtitle!=null&&!subtitle.isEmpty()){TextView s=Ui.text(this,subtitle,14,Ui.MUTED,false);s.setPadding(0,0,0,Ui.dp(this,12));col.addView(s);}return col;'
new_page = '        showChrome(true); ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout col=Ui.column(this);col.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,18),Ui.dp(this,30));sc.addView(col);content.removeAllViews();content.addView(sc,new FrameLayout.LayoutParams(-1,-1));\n        if(title!=null&&!title.isEmpty())col.addView(Ui.title(this,title)); if(subtitle!=null&&!subtitle.isEmpty()){TextView s=Ui.text(this,subtitle,14,Ui.MUTED,false);s.setPadding(0,0,0,Ui.dp(this,12));col.addView(s);}return col;'
if old_page in s:
    s = s.replace(old_page, new_page, 1)
elif 'showChrome(true); ScrollView sc=' not in s:
    s = s.replace('        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout col=Ui.column(this);', '        showChrome(true); ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout col=Ui.column(this);', 1)

insert_at = s.find('    private void toast(String s){')
if insert_at < 0:
    raise SystemExit('toast anchor not found')
helpers = r'''    private void showChrome(boolean show){
        if(brandBar!=null)brandBar.setVisibility(show?View.VISIBLE:View.GONE);
        if(bottom!=null)bottom.setVisibility(show?View.VISIBLE:View.GONE);
    }
    private TextView homeText(String text,float size,int color,boolean bold){
        TextView t=Ui.text(this,text,size,color,bold);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private TextView homeNav(String icon,String label,boolean active,Runnable action){
        TextView t=Ui.text(this,icon+"\n"+label,11,active?Ui.GOLD:Ui.WARM,active);
        t.setGravity(Gravity.CENTER);t.setLineSpacing(0,1.05f);t.setOnClickListener(v->action.run());
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.argb(active?205:185,16,17,20));bg.setCornerRadius(Ui.dp(this,20));
        if(active)bg.setStroke(Ui.dp(this,1),Color.argb(120,215,181,109));t.setBackground(bg);return t;
    }
    private Button luxuryButton(String text,boolean primary,Runnable action){
        Button b=Ui.button(this,text,primary);b.setAllCaps(false);b.setTextSize(15);b.setLetterSpacing(.04f);b.setOnClickListener(v->action.run());
        GradientDrawable g=new GradientDrawable();g.setColor(primary?Color.rgb(224,188,109):Color.argb(185,12,13,15));g.setCornerRadius(Ui.dp(this,28));g.setStroke(Ui.dp(this,1),primary?Color.rgb(224,188,109):Color.rgb(145,116,62));b.setBackground(g);
        return b;
    }
    private void loadHomePhoto(ImageView image){
        final String url="https://images.unsplash.com/photo-1693576588167-2e7148490dc5?auto=format&fit=crop&w=1200&q=82";
        new Thread(()->{try{java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();c.setConnectTimeout(9000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(true);c.connect();Bitmap bm=BitmapFactory.decodeStream(c.getInputStream());c.disconnect();if(bm!=null)runOnUiThread(()->image.setImageBitmap(bm));}catch(Exception ignored){}}).start();
    }

'''
if 'private void showChrome(boolean show)' not in s:
    s = s[:insert_at] + helpers + s[insert_at:]

start = s.find('    private void showHome(){')
end = s.find('    private void showPackages(){', start)
if start < 0 or end < 0:
    raise SystemExit('showHome block not found')
new_home = r'''    private void showHome(){
        showChrome(false);content.removeAllViews();
        FrameLayout home=new FrameLayout(this);home.setBackgroundColor(Ui.BG);content.addView(home,new FrameLayout.LayoutParams(-1,-1));
        ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackgroundColor(Color.rgb(21,17,15));
        home.addView(photo,new FrameLayout.LayoutParams(-1,-1));loadHomePhoto(photo);
        View shade=new View(this);GradientDrawable fade=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.argb(100,4,5,7),Color.argb(55,4,5,7),Color.argb(150,4,5,7),Color.argb(238,4,5,7)});shade.setBackground(fade);home.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        View sideShade=new View(this);GradientDrawable sideFade=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.argb(205,4,5,7),Color.argb(120,4,5,7),Color.argb(30,4,5,7)});sideShade.setBackground(sideFade);home.addView(sideShade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout layer=Ui.column(this);layer.setPadding(Ui.dp(this,24),Ui.dp(this,18),Ui.dp(this,24),Ui.dp(this,16));home.addView(layer,new FrameLayout.LayoutParams(-1,-1));
        TextView brand=homeText("BALI WEDDING DJ",19,Ui.WARM,true);brand.setLetterSpacing(.14f);brand.setShadowLayer(8,0,2,Color.BLACK);layer.addView(brand,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));
        Space push=new Space(this);layer.addView(push,new LinearLayout.LayoutParams(1,0,1));
        TextView title=homeText("Your\nWedding.\nOur Music.",42,Ui.WARM,false);title.setLineSpacing(0,.94f);title.setShadowLayer(12,0,3,Color.BLACK);layer.addView(title);
        View gold=new View(this);gold.setBackgroundColor(Ui.GOLD);LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,2));glp.setMargins(0,Ui.dp(this,14),0,Ui.dp(this,13));layer.addView(gold,glp);
        TextView sub=homeText("Premium wedding\nentertainment in Bali.",16,Color.rgb(220,216,208),false);sub.setLineSpacing(0,1.18f);sub.setShadowLayer(8,0,2,Color.BLACK);layer.addView(sub);
        layer.addView(Ui.space(this,18));
        Button book=luxuryButton("BOOK YOUR DATE   →",true,()->{bookingStep=1;showBooking();});layer.addView(book,new LinearLayout.LayoutParams(-1,Ui.dp(this,58)));
        layer.addView(Ui.space(this,20));
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,2),Ui.dp(this,3),Ui.dp(this,2),Ui.dp(this,3));
        TextView n1=homeNav("⌂","HOME",true,this::showHome);TextView n2=homeNav("▦","PACKAGES",false,this::showPackages);TextView n3=homeNav("□","MY BOOKING",false,this::showProfile);TextView n4=homeNav("○","PROFILE",false,this::showProfile);
        nav.addView(n1,new LinearLayout.LayoutParams(0,Ui.dp(this,72),1));nav.addView(n2,new LinearLayout.LayoutParams(0,Ui.dp(this,72),1));nav.addView(n3,new LinearLayout.LayoutParams(0,Ui.dp(this,72),1));nav.addView(n4,new LinearLayout.LayoutParams(0,Ui.dp(this,72),1));layer.addView(nav);
    }

'''
s = s[:start] + new_home + s[end:]
for needle in ['loadHomePhoto(photo)','Your\\nWedding.\\nOur Music.','BOOK YOUR DATE   →','MY BOOKING','sideShade']:
    if needle not in s:
        raise SystemExit('missing home v3 token: '+needle)
for forbidden in ['VIEW PACKAGES   →','homeText("☰"']:
    if forbidden in s:
        raise SystemExit('forbidden home v3 token still present: '+forbidden)
main.write_text(s)
print('BWD client home QC v3 applied: no hamburger, stronger scrim, single packages entry')

admin_root = root.parent / 'bwd-admin'
owner_patch = pathlib.Path(__file__).with_name('patch_bwd_owner_dashboard.py')
if admin_root.exists() and owner_patch.exists():
    import subprocess
    subprocess.run([sys.executable, str(owner_patch), str(admin_root)], check=True)
    owner_cloud_patch = pathlib.Path(__file__).with_name('patch_bwd_owner_cloud_inbox.py')
    if owner_cloud_patch.exists():
        subprocess.run([sys.executable, str(owner_cloud_patch), str(admin_root)], check=True)
    owner_status_patch = pathlib.Path(__file__).with_name('patch_bwd_owner_status_cloud.py')
    if owner_status_patch.exists():
        subprocess.run([sys.executable, str(owner_status_patch), str(admin_root)], check=True)
