package com.kostas.sourcemindtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int PICK_SOURCE=91;
    private static final String PREFS="source_mind_tv_v1";
    private static final String KEY_SOURCES="sources_json";
    private static final String KEY_API="gemini_api_key";
    private static final String KEY_MODEL="gemini_model";
    private static final String KEY_WEB="web_enabled";
    private static final int MAX_SOURCES=16;

    private final List<SourceDoc> sources=new ArrayList<>();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Set<String> stopWords=new HashSet<>();

    private LinearLayout sourcesBox;
    private TextView status,output,modelLabel;
    private EditText questionInput,apiKeyInput;
    private Button webButton;
    private TextToSpeech tts;
    private SourceReader sourceReader;
    private boolean webEnabled=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        PDFBoxResourceLoader.init(getApplicationContext());
        setupStopWords();
        sourceReader=new SourceReader(this);
        tts=new TextToSpeech(this,this);
        loadSources();
        webEnabled=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(KEY_WEB,false);
        buildUi();
        renderSources();
    }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8,12,19));

        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(32),dp(22),dp(32),dp(44));

        TextView title=text("SOURCE MIND TV 2",32,Color.WHITE);
        title.setTypeface(null,1); page.addView(title);

        TextView sub=text("AI • PDF / EPUB / DOCX / TXT / HTML / FB2 / RTF • Android TV 12",16,Color.rgb(177,190,207));
        sub.setPadding(0,dp(4),0,dp(16)); page.addView(sub);

        LinearLayout add=row();
        Button addFile=button("+ ΑΡΧΕΙΟ / EBOOK");
        Button addText=button("+ ΚΕΙΜΕΝΟ");
        Button addUrl=button("+ URL");
        add.addView(addFile,weight()); add.addView(spacer(dp(10)));
        add.addView(addText,weight()); add.addView(spacer(dp(10)));
        add.addView(addUrl,weight()); page.addView(add,full(dp(62)));

        TextView apiTitle=text("GEMINI AI — ΔΙΚΟ ΣΟΥ API KEY",20,Color.WHITE);
        apiTitle.setTypeface(null,1); apiTitle.setPadding(0,dp(22),0,dp(7)); page.addView(apiTitle);

        apiKeyInput=edit("Επικόλλησε Gemini API key");
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyInput.setText(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_API,""));
        page.addView(apiKeyInput,full(dp(60)));

        LinearLayout apiRow=row();
        Button saveKey=button("ΑΠΟΘΗΚΕΥΣΗ");
        Button testKey=button("ΔΟΚΙΜΗ API");
        webButton=button(webEnabled?"ΙΣΤΟΣ: ON":"ΙΣΤΟΣ: OFF");
        apiRow.addView(saveKey,weight()); apiRow.addView(spacer(dp(10)));
        apiRow.addView(testKey,weight()); apiRow.addView(spacer(dp(10)));
        apiRow.addView(webButton,weight()); page.addView(apiRow,top(dp(10),dp(62)));

        String savedModel=getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_MODEL,"");
        modelLabel=text(savedModel.isEmpty()?"Μοντέλο: δεν έχει ελεγχθεί":"Μοντέλο: "+savedModel,15,Color.rgb(151,167,189));
        modelLabel.setPadding(0,dp(8),0,0); page.addView(modelLabel);

        LinearLayout actions=row();
        Button summary=button("AI ΠΕΡΙΛΗΨΗ");
        Button study=button("ΟΔΗΓΟΣ ΜΕΛΕΤΗΣ");
        Button speak=button("🔊 ΑΚΡΟΑΣΗ");
        actions.addView(summary,weight()); actions.addView(spacer(dp(10)));
        actions.addView(study,weight()); actions.addView(spacer(dp(10)));
        actions.addView(speak,weight()); page.addView(actions,top(dp(16),dp(62)));

        status=text("Έτοιμο.",17,Color.rgb(92,224,173));
        status.setPadding(0,dp(14),0,dp(10)); page.addView(status);

        TextView sTitle=text("ΠΗΓΕΣ",22,Color.WHITE); sTitle.setTypeface(null,1); page.addView(sTitle);
        sourcesBox=new LinearLayout(this); sourcesBox.setOrientation(LinearLayout.VERTICAL); page.addView(sourcesBox);

        TextView askTitle=text("ΡΩΤΑ ΤΟ AI",22,Color.WHITE);
        askTitle.setTypeface(null,1); askTitle.setPadding(0,dp(22),0,dp(8)); page.addView(askTitle);

        questionInput=edit("Ρώτα για τις πηγές σου ή, με ΙΣΤΟΣ ON, για γενική πληροφορία");
        page.addView(questionInput,full(dp(62)));

        Button ask=button("ΡΩΤΑ AI");
        page.addView(ask,top(dp(10),dp(62)));

        TextView outTitle=text("ΑΠΑΝΤΗΣΗ / ΣΗΜΕΙΩΣΕΙΣ",22,Color.WHITE);
        outTitle.setTypeface(null,1); outTitle.setPadding(0,dp(24),0,dp(8)); page.addView(outTitle);

        output=text("Πρόσθεσε πηγή ή ενεργοποίησε τον δημόσιο ιστό. Χωρίς κλειδί λειτουργεί η τοπική ανάλυση.",18,Color.rgb(227,233,241));
        output.setPadding(dp(18),dp(16),dp(18),dp(16));
        output.setBackground(panelDrawable());
        output.setMovementMethod(new ScrollingMovementMethod());
        output.setMinHeight(dp(240)); page.addView(output);

        TextView note=text("Το API key αποθηκεύεται ιδιωτικά στη συσκευή. Ο «ΙΣΤΟΣ» χωρίς κλειδί χρησιμοποιεί δημόσια Ελληνική Wikipedia· για οποιαδήποτε άλλη σελίδα χρησιμοποίησε +URL.",14,Color.rgb(136,151,171));
        note.setPadding(0,dp(12),0,0); page.addView(note);

        scroll.addView(page); setContentView(scroll);

        addFile.setOnClickListener(v->pickSource());
        addText.setOnClickListener(v->showAddTextDialog());
        addUrl.setOnClickListener(v->showAddUrlDialog());
        saveKey.setOnClickListener(v->saveApiKey());
        testKey.setOnClickListener(v->testApiKey());
        webButton.setOnClickListener(v->toggleWeb());
        summary.setOnClickListener(v->makeSummary());
        study.setOnClickListener(v->makeStudyGuide());
        speak.setOnClickListener(v->speakOutput());
        ask.setOnClickListener(v->answerQuestion());

        addFile.requestFocus();
    }

    private void saveApiKey(){
        String key=apiKeyInput.getText().toString().trim();
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_API,key).apply();
        status.setText(key.isEmpty()?"Το API key διαγράφηκε.":"✓ Το API key αποθηκεύτηκε τοπικά.");
    }

    private void testApiKey(){
        final String key=apiKeyInput.getText().toString().trim();
        if(key.isEmpty()){toast("Βάλε πρώτα το API key.");return;}
        saveApiKey();
        status.setText("Ελέγχω κλειδί, quota και διαθέσιμα μοντέλα…");
        executor.execute(()->{
            GeminiClient.TestResult r=GeminiClient.testAndChooseModel(key);
            runOnUiThread(()->{
                if(r.ok){
                    getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_MODEL,r.model).apply();
                    modelLabel.setText("Μοντέλο: "+r.model);
                    status.setText("✓ "+r.message);
                    output.setText("Το API λειτουργεί. Μπορείς τώρα να ζητήσεις AI περίληψη ή να ρωτήσεις οτιδήποτε.");
                }else{
                    status.setText("✕ "+r.message);
                    output.setText("Ο έλεγχος API απέτυχε. Αν βλέπεις 401/403, έλεγξε το κλειδί. Αν βλέπεις 429, είναι όριο/quota.");
                }
            });
        });
    }

    private void toggleWeb(){
        webEnabled=!webEnabled;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(KEY_WEB,webEnabled).apply();
        webButton.setText(webEnabled?"ΙΣΤΟΣ: ON":"ΙΣΤΟΣ: OFF");
        status.setText(webEnabled?"Δημόσιος ιστός ενεργός: Ελληνική Wikipedia.":"Δημόσιος ιστός ανενεργός.");
    }

    private void pickSource(){
        if(sources.size()>=MAX_SOURCES){toast("Έφτασες το όριο πηγών.");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
                "application/pdf","application/epub+zip",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain","text/html","text/markdown","text/csv","application/json","application/xml",
                "application/rtf","text/rtf","application/octet-stream"
        });
        startActivityForResult(i,PICK_SOURCE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_SOURCE||resultCode!=RESULT_OK||data==null)return;
        Uri uri=data.getData(); if(uri==null)return;
        try{
            int flags=data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri,flags);
        }catch(Exception ignored){}
        final String name=getDisplayName(uri);
        final String mime=getContentResolver().getType(uri);
        status.setText("Διαβάζω: "+name+"…");
        executor.execute(()->{
            try{
                String content=cleanText(sourceReader.read(uri,name,mime));
                runOnUiThread(()->addSource(name,content,fileType(name)));
            }catch(Exception e){
                runOnUiThread(()->{
                    status.setText("Δεν μπόρεσα να διαβάσω αυτό το αρχείο.");
                    output.setText("Υποστηρίζονται PDF, EPUB, DOCX, TXT, HTML, MD, CSV, JSON, XML, FB2 και RTF. Κλειδωμένα/DRM ebooks δεν διαβάζονται.");
                });
            }
        });
    }

    private String fileType(String name){
        String n=name.toLowerCase(Locale.ROOT);
        if(n.endsWith(".pdf"))return "PDF"; if(n.endsWith(".epub"))return "EPUB";
        if(n.endsWith(".docx"))return "DOCX"; if(n.endsWith(".fb2"))return "FB2";
        if(n.endsWith(".rtf"))return "RTF"; if(n.endsWith(".html")||n.endsWith(".htm"))return "HTML";
        return "Κείμενο";
    }

    private void showAddTextDialog(){
        if(sources.size()>=MAX_SOURCES){toast("Έφτασες το όριο πηγών.");return;}
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),0,dp(18),0);
        EditText title=new EditText(this); title.setHint("Τίτλος πηγής"); title.setSingleLine(true); box.addView(title);
        EditText body=new EditText(this); body.setHint("Επικόλλησε κείμενο"); body.setMinLines(8); body.setGravity(Gravity.TOP);
        body.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE); box.addView(body);
        new AlertDialog.Builder(this).setTitle("Νέα πηγή").setView(box)
                .setPositiveButton("ΠΡΟΣΘΗΚΗ",(d,w)->{
                    String t=title.getText().toString().trim(); String c=cleanText(body.getText().toString());
                    if(t.isEmpty())t="Κείμενο "+(sources.size()+1);
                    if(c.length()<20)toast("Το κείμενο είναι πολύ μικρό."); else addSource(t,c,"Κείμενο");
                }).setNegativeButton("ΑΚΥΡΟ",null).show();
    }

    private void showAddUrlDialog(){
        if(sources.size()>=MAX_SOURCES){toast("Έφτασες το όριο πηγών.");return;}
        EditText input=new EditText(this); input.setHint("https://…"); input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Εισαγωγή ιστοσελίδας").setView(input)
                .setPositiveButton("ΦΟΡΤΩΣΗ",(d,w)->{
                    String url=input.getText().toString().trim();
                    if(!url.startsWith("https://"))toast("Χρειάζεται σύνδεσμος https://"); else importUrl(url);
                }).setNegativeButton("ΑΚΥΡΟ",null).show();
    }

    private void importUrl(String address){
        status.setText("Φορτώνω ιστοσελίδα…");
        executor.execute(()->{
            HttpURLConnection c=null;
            try{
                URL url=new URL(address); c=(HttpURLConnection)url.openConnection();
                c.setConnectTimeout(12000); c.setReadTimeout(17000);
                c.setRequestProperty("User-Agent","Mozilla/5.0 SourceMindTV/2.0");
                int code=c.getResponseCode(); if(code<200||code>=300)throw new Exception("HTTP "+code);
                BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
                StringBuilder sb=new StringBuilder(); String line;
                while((line=br.readLine())!=null&&sb.length()<360000)sb.append(line).append('\n');
                br.close();
                String raw=sb.toString().replaceAll("(?is)<script.*?>.*?</script>"," ")
                        .replaceAll("(?is)<style.*?>.*?</style>"," ");
                String txt=Html.fromHtml(raw,Html.FROM_HTML_MODE_LEGACY).toString();
                String cleaned=cleanText(txt);
                if(cleaned.length()>180000)cleaned=cleaned.substring(0,180000);
                final String finalText=cleaned; final String host=url.getHost();
                runOnUiThread(()->addSource(host.isEmpty()?address:host,finalText,"URL"));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Η σελίδα δεν επέτρεψε ανάγνωση. Δοκίμασε άλλη URL."));
            }finally{if(c!=null)c.disconnect();}
        });
    }

    private void addSource(String title,String content,String type){
        if(content==null||content.trim().length()<20){status.setText("Δεν βρέθηκε αρκετό αναγνώσιμο κείμενο.");return;}
        if(content.length()>180000)content=content.substring(0,180000);
        sources.add(new SourceDoc(title,content.trim(),type)); persistSources(); renderSources();
        status.setText("✓ Προστέθηκε: "+title);
        output.setText("Η πηγή «"+title+"» είναι έτοιμη για περίληψη και ερωτήσεις.");
    }

    private void renderSources(){
        if(sourcesBox==null)return; sourcesBox.removeAllViews();
        if(sources.isEmpty()){
            TextView e=text("Καμία πηγή ακόμη.",16,Color.rgb(151,165,184)); e.setPadding(0,dp(8),0,dp(8)); sourcesBox.addView(e); return;
        }
        for(int i=0;i<sources.size();i++){
            final int index=i; SourceDoc s=sources.get(i);
            LinearLayout card=row(); card.setPadding(dp(14),dp(8),dp(10),dp(8)); card.setBackground(cardDrawable());
            LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
            TextView n=text((i+1)+". "+s.title,18,Color.WHITE); n.setTypeface(null,1); info.addView(n);
            info.addView(text(s.type+" • "+s.content.length()+" χαρακτήρες",14,Color.rgb(151,167,188)));
            card.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            Button del=button("ΔΙΑΓΡΑΦΗ"); del.setTextSize(14); card.addView(del,new LinearLayout.LayoutParams(dp(160),dp(54)));
            del.setOnClickListener(v->{String nme=sources.remove(index).title;persistSources();renderSources();status.setText("Διαγράφηκε: "+nme);});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin=dp(8); sourcesBox.addView(card,p);
        }
    }

    private void makeSummary(){
        if(sources.isEmpty()){toast("Πρόσθεσε πρώτα πηγή.");return;}
        String key=savedKey(),model=savedModel();
        if(key.isEmpty()||model.isEmpty()){localSummary();return;}
        status.setText("Το AI δημιουργεί περίληψη…");
        final String ctx=contextForSummary();
        executor.execute(()->{
            try{
                String prompt="Απάντησε στα ελληνικά. Κάνε ακριβή, κατανοητή περίληψη ΜΟΝΟ του υλικού παρακάτω. "+
                        "Δώσε: 1) σύντομη ουσία, 2) βασικά σημεία, 3) κρίσιμες λεπτομέρειες, 4) τι δεν προκύπτει από το κείμενο.\n\nΥΛΙΚΟ:\n"+ctx;
                String ans=GeminiClient.generate(key,model,prompt);
                runOnUiThread(()->{output.setText(ans);status.setText("✓ AI περίληψη έτοιμη.");});
            }catch(Exception e){runOnUiThread(()->{status.setText("AI σφάλμα: "+e.getMessage());localSummary();});}
        });
    }

    private void localSummary(){
        String merged=mergedText(); List<String> best=topSentences(merged,"",7);
        StringBuilder sb=new StringBuilder("ΤΟΠΙΚΗ ΠΕΡΙΛΗΨΗ — ΧΩΡΙΣ AI\n\n");
        for(String s:best)sb.append("• ").append(s).append("\n\n");
        List<String> keys=topKeywords(merged,8);
        if(!keys.isEmpty())sb.append("Κύριες έννοιες: ").append(join(keys," • "));
        output.setText(sb.toString().trim()); status.setText("✓ Τοπική περίληψη. Για πλήρες AI πάτησε ΔΟΚΙΜΗ API.");
    }

    private void makeStudyGuide(){
        if(sources.isEmpty()){toast("Πρόσθεσε πρώτα πηγή.");return;}
        String merged=mergedText(); List<String> pts=topSentences(merged,"",8); List<String> keys=topKeywords(merged,8);
        StringBuilder sb=new StringBuilder("ΟΔΗΓΟΣ ΜΕΛΕΤΗΣ\n\nΒΑΣΙΚΑ ΣΗΜΕΙΑ\n");
        for(String s:pts)sb.append("• ").append(s).append("\n");
        sb.append("\nΕΡΩΤΗΣΕΙΣ ΕΠΑΝΑΛΗΨΗΣ\n");
        for(int i=0;i<Math.min(6,keys.size());i++)sb.append(i+1).append(". Τι αναφέρει το υλικό για «").append(keys.get(i)).append("»;\n");
        output.setText(sb.toString().trim()); status.setText("✓ Ο οδηγός μελέτης είναι έτοιμος.");
    }

    private void answerQuestion(){
        final String q=questionInput.getText().toString().trim();
        if(q.isEmpty()){toast("Γράψε ερώτηση.");return;}
        if(sources.isEmpty()&&!webEnabled){toast("Πρόσθεσε πηγή ή άνοιξε ΙΣΤΟΣ.");return;}
        final String key=savedKey(),model=savedModel();
        if(key.isEmpty()||model.isEmpty()){answerWithoutAi(q);return;}

        status.setText(webEnabled?"AI + δημόσιος ιστός: αναζήτηση…":"AI: αναζήτηση στις πηγές…");
        executor.execute(()->{
            try{
                String evidence=buildEvidence(q,14);
                String web=webEnabled?WebResearch.searchGreekWikipedia(q):"Ιστός απενεργοποιημένος.";
                String prompt="Απάντησε στα ελληνικά, καθαρά και συγκεκριμένα στην ερώτηση. "+
                        "Χρησιμοποίησε πρώτα τις ΠΗΓΕΣ ΧΡΗΣΤΗ. Αν υπάρχει τμήμα ΔΗΜΟΣΙΟΣ ΙΣΤΟΣ, μπορείς να το χρησιμοποιήσεις συμπληρωματικά. "+
                        "Μην εφευρίσκεις γεγονότα. Ξεχώρισε τι προέρχεται από τις πηγές και τι από τον ιστό. Αν δεν υπάρχει επαρκής στήριξη, πες το.\n\n"+
                        "ΕΡΩΤΗΣΗ: "+q+"\n\nΠΗΓΕΣ ΧΡΗΣΤΗ:\n"+evidence+"\n\n"+web;
                String ans=GeminiClient.generate(key,model,prompt);
                runOnUiThread(()->{output.setText(ans);status.setText("✓ Απάντηση AI έτοιμη.");});
            }catch(Exception e){runOnUiThread(()->{status.setText("AI σφάλμα: "+e.getMessage());answerWithoutAi(q);});}
        });
    }

    private void answerWithoutAi(final String q){
        status.setText(webEnabled?"Τοπική ανάλυση + δημόσιος ιστός…":"Τοπική ανάλυση…");
        executor.execute(()->{
            String local=buildEvidence(q,6);
            String web="";
            if(webEnabled){
                try{web=WebResearch.searchGreekWikipedia(q);}catch(Exception e){web="Ο δημόσιος ιστός δεν απάντησε.";}
            }
            final String result=(local.isEmpty()?"Δεν βρέθηκε σχετικό απόσπασμα στις πηγές.":local)
                    +(web.isEmpty()?"":"\n\n"+web)
                    +"\n\nΧωρίς ενεργό AI εμφανίζονται σχετικά αποσπάσματα, όχι συνθετική απάντηση.";
            runOnUiThread(()->{output.setText(result);status.setText("✓ Τοπικά αποτελέσματα έτοιμα.");});
        });
    }

    private String buildEvidence(String q,int limit){
        List<String> qWords=tokens(q); List<Hit> hits=new ArrayList<>();
        for(int si=0;si<sources.size();si++){
            SourceDoc s=sources.get(si);
            for(String sentence:splitSentences(s.content)){
                if(sentence.length()<25||sentence.length()>800)continue;
                List<String> words=tokens(sentence); int overlap=0;
                for(String w:qWords)if(words.contains(w))overlap++;
                if(overlap>0)hits.add(new Hit(overlap*10.0+Math.min(sentence.length(),300)/300.0,sentence,si));
            }
        }
        Collections.sort(hits,(a,b)->Double.compare(b.score,a.score));
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<Math.min(limit,hits.size());i++){
            Hit h=hits.get(i); SourceDoc s=sources.get(h.sourceIndex);
            sb.append("[").append(h.sourceIndex+1).append("] ").append(s.title).append(": ").append(h.sentence).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String contextForSummary(){
        StringBuilder sb=new StringBuilder(); int total=0;
        for(int i=0;i<sources.size();i++){
            SourceDoc s=sources.get(i); int remaining=70000-total; if(remaining<=0)break;
            int take=Math.min(s.content.length(),Math.min(14000,remaining));
            sb.append("\n### Πηγή ").append(i+1).append(": ").append(s.title).append("\n")
                    .append(s.content,0,take).append("\n");
            total+=take;
        }
        return sb.toString();
    }

    private List<String> topSentences(String text,String query,int limit){
        List<String> sentences=splitSentences(text); Map<String,Integer> freq=frequency(text); List<String> q=tokens(query);
        List<SentenceScore> scored=new ArrayList<>();
        for(int i=0;i<sentences.size();i++){
            String s=sentences.get(i).trim(); if(s.length()<35||s.length()>560)continue;
            List<String> words=tokens(s); if(words.size()<5)continue;
            double score=0; for(String w:words)score+=Math.min(freq.getOrDefault(w,0),8);
            score/=Math.sqrt(words.size()); for(String x:q)if(words.contains(x))score+=12; if(i<8)score+=1.2;
            scored.add(new SentenceScore(score,i,s));
        }
        Collections.sort(scored,(a,b)->Double.compare(b.score,a.score));
        List<SentenceScore> selected=new ArrayList<>();
        for(SentenceScore ss:scored){
            boolean dup=false; for(SentenceScore p:selected)if(similar(ss.sentence,p.sentence)>.72){dup=true;break;}
            if(!dup)selected.add(ss); if(selected.size()>=limit)break;
        }
        Collections.sort(selected, Comparator.comparingInt(a->a.position));
        List<String> out=new ArrayList<>(); for(SentenceScore ss:selected)out.add(ss.sentence); return out;
    }

    private List<String> topKeywords(String text,int limit){
        Map<String,Integer> f=frequency(text); List<Map.Entry<String,Integer>> e=new ArrayList<>(f.entrySet());
        Collections.sort(e,(a,b)->Integer.compare(b.getValue(),a.getValue()));
        List<String> out=new ArrayList<>(); for(Map.Entry<String,Integer>x:e){if(x.getKey().length()>=4&&x.getValue()>=2)out.add(x.getKey());if(out.size()>=limit)break;} return out;
    }
    private Map<String,Integer> frequency(String text){Map<String,Integer>m=new HashMap<>();for(String w:tokens(text))m.put(w,m.getOrDefault(w,0)+1);return m;}
    private List<String> tokens(String text){String n=normalize(text);String[]raw=n.split("[^\\p{L}\\p{Nd}]+");List<String>out=new ArrayList<>();for(String w:raw)if(w.length()>=3&&!stopWords.contains(w))out.add(w);return out;}
    private String normalize(String text){String x=text==null?"":text.toLowerCase(new Locale("el","GR"));return Normalizer.normalize(x,Normalizer.Form.NFD).replaceAll("\\p{M}+","");}
    private List<String> splitSentences(String text){String[]a=cleanText(text).split("(?<=[.!?;])\\s+|\\n+");List<String>o=new ArrayList<>();for(String s:a){s=s.trim();if(!s.isEmpty())o.add(s);}return o;}
    private double similar(String a,String b){Set<String>aa=new HashSet<>(tokens(a)),bb=new HashSet<>(tokens(b));if(aa.isEmpty()||bb.isEmpty())return 0;Set<String>i=new HashSet<>(aa);i.retainAll(bb);Set<String>u=new HashSet<>(aa);u.addAll(bb);return i.size()/(double)u.size();}
    private String mergedText(){StringBuilder sb=new StringBuilder();for(SourceDoc s:sources)sb.append(s.content).append("\n");return sb.toString();}
    private String join(List<String>v,String sep){StringBuilder sb=new StringBuilder();for(int i=0;i<v.size();i++){if(i>0)sb.append(sep);sb.append(v.get(i));}return sb.toString();}

    private void speakOutput(){
        String v=output.getText().toString().trim(); if(v.isEmpty()){toast("Δεν υπάρχει κείμενο.");return;}
        if(tts==null){toast("Το TTS δεν είναι έτοιμο.");return;}
        tts.speak(v,TextToSpeech.QUEUE_FLUSH,null,"source_mind_v2"); status.setText("🔊 Ανάγνωση απάντησης.");
    }

    @Override public void onInit(int s){
        if(s==TextToSpeech.SUCCESS&&tts!=null){
            int r=tts.setLanguage(new Locale("el","GR"));
            if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED)tts.setLanguage(Locale.getDefault());
            tts.setSpeechRate(.92f);
        }
    }

    private String savedKey(){return getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_API,"").trim();}
    private String savedModel(){return getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_MODEL,"").trim();}

    private String getDisplayName(Uri uri){
        String name="Πηγή"; Cursor c=null;
        try{c=getContentResolver().query(uri,null,null,null,null);if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)name=c.getString(i);}}
        catch(Exception ignored){}finally{if(c!=null)c.close();} return name==null?"Πηγή":name;
    }

    private String cleanText(String s){
        if(s==null)return ""; return s.replace("\r"," ").replaceAll("[\\t ]+"," ").replaceAll("\\n[ \\t]+","\n").replaceAll("\\n{3,}","\n\n").trim();
    }

    private void loadSources(){
        sources.clear(); String raw=getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_SOURCES,"[]");
        try{JSONArray arr=new JSONArray(raw);for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o==null)continue;String t=o.optString("title","Πηγή"),c=o.optString("content",""),ty=o.optString("type","Κείμενο");if(!c.isEmpty())sources.add(new SourceDoc(t,c,ty));}}
        catch(Exception ignored){}
    }

    private void persistSources(){
        JSONArray arr=new JSONArray();
        try{for(SourceDoc s:sources)arr.put(new JSONObject().put("title",s.title).put("content",s.content).put("type",s.type));}catch(Exception ignored){}
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_SOURCES,arr.toString()).apply();
    }

    private void setupStopWords(){
        String c="και να το η ο οι τα των της τον την του σε για με από που πως ως ένα μια είναι ήταν θα δεν ναι ή αλλά αν όταν όσο αυτό αυτή αυτά εκεί εδώ πολύ πιο προς μετά πριν μέσα έξω χωρίς κάθε επίσης όμως επειδή ώστε γιατί τι ποιος ποια ποιο μου σου μας σας τους τις στο στη στα στον στην στους στις κι ούτε ότι ενώ ήδη μπορεί έχουν έχει είχε έχω είμαι είσαι είμαστε then the and are was were this that with from into your you our their they them for not have has had can could should would";
        for(String w:c.split(" "))stopWords.add(normalize(w));
    }

    private Button button(String label){
        Button b=new Button(this); b.setText(label); b.setTextSize(16); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setFocusable(true);b.setFocusableInTouchMode(true);b.setPadding(dp(12),dp(7),dp(12),dp(7));b.setBackground(buttonDrawable(false));
        b.setOnFocusChangeListener((v,f)->{b.setBackground(buttonDrawable(f));b.setTextColor(f?Color.BLACK:Color.WHITE);b.setScaleX(f?1.03f:1f);b.setScaleY(f?1.03f:1f);});return b;
    }
    private EditText edit(String hint){
        EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(126,141,160));e.setTextColor(Color.WHITE);e.setTextSize(18);e.setSingleLine(true);
        e.setPadding(dp(16),dp(10),dp(16),dp(10));e.setFocusable(true);e.setFocusableInTouchMode(true);e.setBackground(editDrawable(false));e.setOnFocusChangeListener((v,f)->e.setBackground(editDrawable(f)));return e;
    }
    private TextView text(String v,int sp,int color){TextView t=new TextView(this);t.setText(v);t.setTextSize(sp);t.setTextColor(color);return t;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private View spacer(int w){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(w,1));return v;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,dp(62),1f);}
    private LinearLayout.LayoutParams full(int h){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,h);}
    private LinearLayout.LayoutParams top(int m,int h){LinearLayout.LayoutParams p=full(h);p.topMargin=m;return p;}
    private GradientDrawable buttonDrawable(boolean f){GradientDrawable g=new GradientDrawable();g.setColor(f?Color.rgb(239,208,90):Color.rgb(34,46,64));g.setCornerRadius(dp(10));g.setStroke(dp(f?3:2),f?Color.WHITE:Color.rgb(70,90,116));return g;}
    private GradientDrawable editDrawable(boolean f){GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(18,26,37));g.setCornerRadius(dp(9));g.setStroke(dp(f?3:1),f?Color.rgb(239,208,90):Color.rgb(65,81,104));return g;}
    private GradientDrawable panelDrawable(){GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(17,24,34));g.setCornerRadius(dp(11));g.setStroke(dp(1),Color.rgb(52,69,91));return g;}
    private GradientDrawable cardDrawable(){GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(15,22,31));g.setCornerRadius(dp(9));g.setStroke(dp(1),Color.rgb(46,62,82));return g;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String v){Toast.makeText(this,v,Toast.LENGTH_SHORT).show();}

    @Override protected void onDestroy(){executor.shutdownNow();if(tts!=null)try{tts.stop();tts.shutdown();}catch(Exception ignored){}super.onDestroy();}

    private static class SourceDoc{final String title,content,type;SourceDoc(String t,String c,String y){title=t;content=c;type=y;}}
    private static class SentenceScore{final double score;final int position;final String sentence;SentenceScore(double s,int p,String x){score=s;position=p;sentence=x;}}
    private static class Hit{final double score;final String sentence;final int sourceIndex;Hit(double s,String x,int i){score=s;sentence=x;sourceIndex=i;}}
}
