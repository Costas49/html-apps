package com.kostas.sourcemindtv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GeminiClient {
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/";

    public static class TestResult {
        public final boolean ok;
        public final String model;
        public final String message;
        TestResult(boolean ok, String model, String message) {
            this.ok = ok; this.model = model; this.message = message;
        }
    }

    public static TestResult testAndChooseModel(String key) {
        if (key == null || key.trim().isEmpty()) return new TestResult(false, "", "Δεν υπάρχει API key.");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BASE + "models").openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("x-goog-api-key", key.trim());
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            String body = readBody(c, code);
            if (code < 200 || code >= 300) return new TestResult(false, "", explainHttp(code, body));

            JSONObject root = new JSONObject(body);
            JSONArray models = root.optJSONArray("models");
            List<String> candidates = new ArrayList<>();
            if (models != null) {
                for (int i=0;i<models.length();i++) {
                    JSONObject m=models.optJSONObject(i);
                    if (m==null) continue;
                    JSONArray methods=m.optJSONArray("supportedGenerationMethods");
                    boolean canGenerate=false;
                    if (methods!=null) {
                        for (int j=0;j<methods.length();j++) {
                            if ("generateContent".equals(methods.optString(j))) { canGenerate=true; break; }
                        }
                    }
                    String name=m.optString("name","");
                    if (canGenerate && name.startsWith("models/")) candidates.add(name.substring(7));
                }
            }
            if (candidates.isEmpty()) return new TestResult(false, "", "Το κλειδί συνδέθηκε, αλλά δεν βρέθηκε μοντέλο generateContent.");

            String selected = chooseBest(candidates);
            String response = generate(key, selected, "Απάντησε μόνο με τη λέξη OK.");
            if (response == null || response.trim().isEmpty()) return new TestResult(false, selected, "Το μοντέλο βρέθηκε αλλά δεν επέστρεψε κείμενο.");
            return new TestResult(true, selected, "API ενεργό • μοντέλο: " + selected);
        } catch (Exception e) {
            return new TestResult(false, "", "Σφάλμα σύνδεσης: " + shortMessage(e));
        } finally {
            if (c!=null) c.disconnect();
        }
    }

    private static String chooseBest(List<String> models) {
        String[] prefs = {"flash-lite","flash","pro"};
        for (String pref:prefs) {
            for (String m:models) {
                String x=m.toLowerCase();
                if (x.contains(pref) && !x.contains("image") && !x.contains("tts") && !x.contains("audio")) return m;
            }
        }
        return models.get(0);
    }

    public static String generate(String key, String model, String prompt) throws Exception {
        if (model == null || model.trim().isEmpty()) throw new Exception("Δεν έχει επιλεγεί μοντέλο.");
        HttpURLConnection c=null;
        try {
            URL url=new URL(BASE+"models/"+model+":generateContent");
            c=(HttpURLConnection)url.openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("x-goog-api-key", key.trim());
            c.setRequestProperty("Content-Type","application/json; charset=UTF-8");

            JSONObject part=new JSONObject().put("text",prompt);
            JSONArray parts=new JSONArray().put(part);
            JSONObject content=new JSONObject().put("role","user").put("parts",parts);
            JSONObject cfg=new JSONObject().put("temperature",0.25).put("maxOutputTokens",1800);
            JSONObject payload=new JSONObject()
                    .put("contents",new JSONArray().put(content))
                    .put("generationConfig",cfg);

            byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream os=c.getOutputStream()){ os.write(bytes); }

            int code=c.getResponseCode();
            String body=readBody(c,code);
            if(code<200||code>=300) throw new Exception(explainHttp(code,body));

            JSONObject root=new JSONObject(body);
            JSONArray candidates=root.optJSONArray("candidates");
            if(candidates==null||candidates.length()==0) throw new Exception("Δεν επέστρεψε απάντηση το μοντέλο.");
            JSONObject first=candidates.optJSONObject(0);
            JSONObject cont=first==null?null:first.optJSONObject("content");
            JSONArray ps=cont==null?null:cont.optJSONArray("parts");
            if(ps==null||ps.length()==0) throw new Exception("Κενή απάντηση μοντέλου.");
            StringBuilder out=new StringBuilder();
            for(int i=0;i<ps.length();i++){
                JSONObject p=ps.optJSONObject(i);
                if(p!=null && p.has("text")) out.append(p.optString("text")).append("\n");
            }
            return out.toString().trim();
        } finally {
            if(c!=null) c.disconnect();
        }
    }

    private static String readBody(HttpURLConnection c,int code) throws Exception {
        BufferedReader br=new BufferedReader(new InputStreamReader(
                code>=200&&code<400?c.getInputStream():c.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder(); String line;
        while((line=br.readLine())!=null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static String explainHttp(int code,String body) {
        String detail="";
        try {
            JSONObject r=new JSONObject(body);
            JSONObject e=r.optJSONObject("error");
            if(e!=null) detail=e.optString("message","");
        } catch(Exception ignored){}
        if(code==400) return "400: μη έγκυρο αίτημα/μοντέλο. "+detail;
        if(code==401||code==403) return code+": το API key δεν έγινε δεκτό ή δεν έχει άδεια. "+detail;
        if(code==429) return "429: εξαντλήθηκε προσωρινά το quota/όριο του API. "+detail;
        return "HTTP "+code+(detail.isEmpty()?"":": "+detail);
    }

    private static String shortMessage(Exception e){
        String m=e.getMessage();
        return m==null?e.getClass().getSimpleName():(m.length()>180?m.substring(0,180):m);
    }
}
