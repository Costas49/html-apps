package com.costas.novalitelauncher;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
  static final String P="nova_lite", F="favorites";
  final ArrayList<AppItem> apps=new ArrayList<>(), shown=new ArrayList<>(), favs=new ArrayList<>();
  SharedPreferences prefs; PackageManager pm; LinearLayout home,drawer; GridView grid,favGrid; EditText search; TextView clock,date;
  final Handler h=new Handler(Looper.getMainLooper());

  @Override public void onCreate(Bundle b){
    super.onCreate(b); pm=getPackageManager(); prefs=getSharedPreferences(P,MODE_PRIVATE);
    getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.rgb(8,11,18));
    build(); load(); tick();
  }

  void build(){
    FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Color.rgb(8,11,18)); setContentView(root);
    home=new LinearLayout(this); home.setOrientation(LinearLayout.VERTICAL); home.setPadding(dp(22),dp(26),dp(22),dp(18)); root.addView(home,new FrameLayout.LayoutParams(-1,-1));
    drawer=new LinearLayout(this); drawer.setOrientation(LinearLayout.VERTICAL); drawer.setPadding(dp(16),dp(20),dp(16),dp(10)); drawer.setBackgroundColor(Color.rgb(8,11,18)); drawer.setVisibility(View.GONE); root.addView(drawer,new FrameLayout.LayoutParams(-1,-1));

    LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); home.addView(top,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout tb=new LinearLayout(this); tb.setOrientation(LinearLayout.VERTICAL); top.addView(tb,new LinearLayout.LayoutParams(0,-2,1));
    clock=txt("",48,Color.WHITE,true); date=txt("",15,0xFFB9C3D6,false); tb.addView(clock); tb.addView(date);
    Button role=btn("⌂  Ορισμός Home"); top.addView(role,new LinearLayout.LayoutParams(dp(145),dp(48))); role.setOnClickListener(v->requestHome());

    TextView hero=txt("Nova Lite",28,Color.WHITE,true); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2); hp.topMargin=dp(28); home.addView(hero,hp);
    home.addView(txt("Γρήγορος • καθαρός • χωρίς διαφημίσεις",14,0xFF95A4BF,false));
    Button sb=btn("⌕  Αναζήτηση εφαρμογών"); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(56)); sp.topMargin=dp(22); home.addView(sb,sp); sb.setOnClickListener(v->showDrawer(true));
    TextView ft=txt("Αγαπημένα",16,0xFFD5DCEE,true); LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2); fp.topMargin=dp(22); fp.bottomMargin=dp(8); home.addView(ft,fp);
    favGrid=makeGrid(4); home.addView(favGrid,new LinearLayout.LayoutParams(-1,0,1));
    Button all=btn("▦  Όλες οι εφαρμογές"); home.addView(all,new LinearLayout.LayoutParams(-1,dp(56))); all.setOnClickListener(v->showDrawer(false));

    LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); drawer.addView(bar,new LinearLayout.LayoutParams(-1,dp(54)));
    Button back=btn("‹"); back.setTextSize(28); bar.addView(back,new LinearLayout.LayoutParams(dp(56),dp(48))); back.setOnClickListener(v->showHome());
    TextView title=txt("Εφαρμογές",23,Color.WHITE,true); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1); tp.leftMargin=dp(12); bar.addView(title,tp);

    search=new EditText(this); search.setSingleLine(true); search.setTextColor(Color.WHITE); search.setHintTextColor(0xFF8390A8); search.setHint("Αναζήτηση…"); search.setTextSize(17); search.setPadding(dp(18),0,dp(18),0); search.setBackground(round(0xFF171D2A,18,0xFF2B3750));
    LinearLayout.LayoutParams sr=new LinearLayout.LayoutParams(-1,dp(54)); sr.topMargin=dp(10); sr.bottomMargin=dp(12); drawer.addView(search,sr);
    search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){filter(s.toString());} public void afterTextChanged(Editable e){}});

    int cols=Math.max(4,Math.min(6,getResources().getConfiguration().screenWidthDp/95)); grid=makeGrid(cols); drawer.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
    TextView hint=txt("Παρατεταμένο πάτημα = αγαπημένο",12,0xFF7F8AA0,false); hint.setGravity(Gravity.CENTER); drawer.addView(hint);
  }

  void load(){
    Intent q=new Intent(Intent.ACTION_MAIN,null); q.addCategory(Intent.CATEGORY_LAUNCHER); apps.clear();
    for(ResolveInfo r:pm.queryIntentActivities(q,0)){
      if(r.activityInfo==null||getPackageName().equals(r.activityInfo.packageName))continue;
      apps.add(new AppItem(String.valueOf(r.loadLabel(pm)),new ComponentName(r.activityInfo.packageName,r.activityInfo.name),r.loadIcon(pm)));
    }
    final Collator c=Collator.getInstance(new Locale("el","GR")); Collections.sort(apps,(a,b)->c.compare(a.name,b.name));
    Set<String> s=new LinkedHashSet<>(prefs.getStringSet(F,Collections.emptySet()));
    if(s.isEmpty()){for(int i=0;i<Math.min(8,apps.size());i++)s.add(apps.get(i).key()); prefs.edit().putStringSet(F,s).apply();}
    refreshFavs(); filter("");
  }

  void refreshFavs(){
    favs.clear(); Set<String>s=prefs.getStringSet(F,Collections.emptySet()); for(AppItem a:apps)if(s.contains(a.key()))favs.add(a);
    favGrid.setAdapter(new AppAdapter(favs)); favGrid.setOnItemClickListener((p,v,pos,id)->open(favs.get(pos))); favGrid.setOnItemLongClickListener((p,v,pos,id)->{toggle(favs.get(pos));return true;});
  }
  void filter(String q){
    if(grid==null)return; String n=q.trim().toLowerCase(Locale.getDefault()); shown.clear();
    for(AppItem a:apps)if(n.isEmpty()||a.name.toLowerCase(Locale.getDefault()).contains(n))shown.add(a);
    grid.setAdapter(new AppAdapter(shown)); grid.setOnItemClickListener((p,v,pos,id)->open(shown.get(pos))); grid.setOnItemLongClickListener((p,v,pos,id)->{toggle(shown.get(pos));return true;});
  }
  void toggle(AppItem a){
    Set<String>s=new LinkedHashSet<>(prefs.getStringSet(F,Collections.emptySet())); boolean add;
    if(s.contains(a.key())){s.remove(a.key());add=false;}else{s.add(a.key());add=true;} prefs.edit().putStringSet(F,s).apply(); refreshFavs();
    Toast.makeText(this,add?"Προστέθηκε στα αγαπημένα":"Αφαιρέθηκε",Toast.LENGTH_SHORT).show();
  }
  void open(AppItem a){
    try{Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LAUNCHER);i.setComponent(a.c);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);startActivity(i);}
    catch(Exception e){Toast.makeText(this,"Δεν μπόρεσε να ανοίξει",Toast.LENGTH_SHORT).show();}
  }
  void requestHome(){
    RoleManager r=(RoleManager)getSystemService(Context.ROLE_SERVICE);
    if(r!=null&&r.isRoleAvailable(RoleManager.ROLE_HOME)){
      if(r.isRoleHeld(RoleManager.ROLE_HOME))Toast.makeText(this,"Ήδη προεπιλεγμένος launcher",Toast.LENGTH_SHORT).show();
      else startActivityForResult(r.createRequestRoleIntent(RoleManager.ROLE_HOME),7);
    }else Toast.makeText(this,"Ρυθμίσεις → Εφαρμογές → Προεπιλεγμένες → Home",Toast.LENGTH_LONG).show();
  }
  void showDrawer(boolean focus){home.setVisibility(View.GONE);drawer.setVisibility(View.VISIBLE);if(focus){search.requestFocus();search.postDelayed(()->{InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(im!=null)im.showSoftInput(search,InputMethodManager.SHOW_IMPLICIT);},120);}}
  void showHome(){search.setText("");search.clearFocus();InputMethodManager im=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(im!=null)im.hideSoftInputFromWindow(search.getWindowToken(),0);drawer.setVisibility(View.GONE);home.setVisibility(View.VISIBLE);}
  @Override public void onBackPressed(){if(drawer.getVisibility()==View.VISIBLE)showHome();}
  void tick(){h.post(new Runnable(){public void run(){Date d=new Date();clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(d));date.setText(new SimpleDateFormat("EEEE, d MMMM",new Locale("el","GR")).format(d));h.postDelayed(this,30000);}});}

  GridView makeGrid(int n){GridView g=new GridView(this);g.setNumColumns(n);g.setVerticalSpacing(dp(8));g.setHorizontalSpacing(dp(4));g.setSelector(android.R.color.transparent);return g;}
  Button btn(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setBackground(round(0xFF171D2A,18,0xFF2B3750));return b;}
  TextView txt(String s,float z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
  Drawable round(int fill,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
  int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}

  class AppAdapter extends BaseAdapter{
    final List<AppItem>d;AppAdapter(List<AppItem>x){d=x;}public int getCount(){return d.size();}public Object getItem(int p){return d.get(p);}public long getItemId(int p){return p;}
    public View getView(int p,View cv,ViewGroup parent){Holder x;if(cv==null){LinearLayout c=new LinearLayout(MainActivity.this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(4),dp(7),dp(4),dp(6));ImageView i=new ImageView(MainActivity.this);c.addView(i,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView t=txt("",12,0xFFF0F3F8,false);t.setGravity(Gravity.CENTER);t.setMaxLines(2);t.setPadding(2,dp(5),2,0);c.addView(t,new LinearLayout.LayoutParams(-1,dp(38)));x=new Holder(i,t);c.setTag(x);cv=c;}else x=(Holder)cv.getTag();AppItem a=d.get(p);x.i.setImageDrawable(a.icon);x.t.setText(a.name);return cv;}
  }
  static class Holder{final ImageView i;final TextView t;Holder(ImageView a,TextView b){i=a;t=b;}}
  static class AppItem{final String name;final ComponentName c;final Drawable icon;AppItem(String n,ComponentName x,Drawable d){name=n;c=x;icon=d;}String key(){return c.flattenToString();}}
}
