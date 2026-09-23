package com.kostas.sourcemindtv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WebResearch {
    public static String searchGreekWikipedia(String query) throws Exception {
        String q=URLEncoder.encode(query,"UTF-8");
        String searchUrl="https://el.wikipedia.org/w/api.php?action=query&list=search&srsearch="+q+"&srlimit=3&format=json&utf8=1";
        JSONObject root=new JSONObject(get(searchUrl));
        JSONArray arr=root.optJSONObject("query")==null?null:root.optJSONObject("query").optJSONArray("search");
        if(arr==null||arr.length()==0) return "Δεν βρέθηκαν αποτελέσματα στη Wikipedia.";

        List<String> titles=new ArrayList<>();
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.optJSONObject(i);
            if(o!=null&&!o.optString("title","").isEmpty()) titles.add(o.optString("title"));
        }
        if(titles.isEmpty()) return "Δεν βρέθηκαν αποτελέσματα στη Wikipedia.";

        String joined="";
        for(int i=0;i<titles.size();i++){
            if(i>0) joined+="|";
            joined+=titles.get(i);
        }
        String exUrl="https://el.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exintro=1&redirects=1&titles="
                +URLEncoder.encode(joined,"UTF-8")+"&format=json&utf8=1";
        JSONObject exRoot=new JSONObject(get(exUrl));
        JSONObject pages=exRoot.optJSONObject("query")==null?null:exRoot.optJSONObject("query").optJSONObject("pages");
        StringBuilder out=new StringBuilder("ΔΗΜΟΣΙΟΣ ΙΣΤΟΣ — Ελληνική Wikipedia\n");
        if(pages!=null){
            Iterator<String> keys=pages.keys();
            while(keys.hasNext()){
                JSONObject p=pages.optJSONObject(keys.next());
                if(p==null)continue;
                String title=p.optString("title","");
                String extract=p.optString("extract","");
                if(extract.length()>1800)extract=extract.substring(0,1800);
                if(!extract.isEmpty()) out.append("\n### ").append(title).append("\n").append(extract).append("\n");
            }
        }
        return out.toString().trim();
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(address).openConnection();
            c.setConnectTimeout(12000); c.setReadTimeout(16000);
            c.setRequestProperty("User-Agent","SourceMindTV/2.0 (Android TV)");
            c.setRequestProperty("Accept","application/json");
            int code=c.getResponseCode();
            if(code<200||code>=300)throw new Exception("HTTP "+code);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb=new StringBuilder(); String line;
            while((line=br.readLine())!=null)sb.append(line);
            br.close(); return sb.toString();
        } finally { if(c!=null)c.disconnect(); }
    }
}
