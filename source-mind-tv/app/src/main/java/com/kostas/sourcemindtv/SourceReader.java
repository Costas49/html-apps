package com.kostas.sourcemindtv;

import android.content.Context;
import android.net.Uri;
import android.text.Html;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SourceReader {
    private final Context context;
    private static final int LIMIT = 180000;

    public SourceReader(Context context) { this.context=context; }

    public String read(Uri uri,String name,String mime) throws Exception {
        String lower=name==null?"":name.toLowerCase(Locale.ROOT);
        if ("application/pdf".equals(mime)||lower.endsWith(".pdf")) return crop(readPdf(uri));
        if (lower.endsWith(".epub")||"application/epub+zip".equals(mime)) return crop(readEpub(uri));
        if (lower.endsWith(".docx")||"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)) return crop(readDocx(uri));
        if (lower.endsWith(".fb2")) return crop(stripMarkup(readText(uri).replace("</p>","</p>\n")));
        if (lower.endsWith(".html")||lower.endsWith(".htm")||(mime!=null&&mime.contains("html"))) return crop(stripMarkup(readText(uri)));
        if (lower.endsWith(".rtf")||(mime!=null&&mime.contains("rtf"))) return crop(stripRtf(readText(uri)));
        return crop(readText(uri));
    }

    private String readPdf(Uri uri) throws Exception {
        InputStream in=context.getContentResolver().openInputStream(uri);
        if(in==null) throw new Exception("No stream");
        PDDocument doc=null;
        try{
            doc=PDDocument.load(in);
            return new PDFTextStripper().getText(doc);
        } finally {
            try{if(doc!=null)doc.close();}catch(Exception ignored){}
            try{in.close();}catch(Exception ignored){}
        }
    }

    private String readEpub(Uri uri) throws Exception {
        InputStream in=context.getContentResolver().openInputStream(uri);
        if(in==null) throw new Exception("No stream");
        ZipInputStream zip=new ZipInputStream(in);
        StringBuilder out=new StringBuilder();
        ZipEntry e;
        while((e=zip.getNextEntry())!=null && out.length()<LIMIT*2){
            String n=e.getName().toLowerCase(Locale.ROOT);
            if(!e.isDirectory()&&(n.endsWith(".xhtml")||n.endsWith(".html")||n.endsWith(".htm"))){
                String raw=readEntry(zip,600000);
                String text=stripMarkup(raw);
                if(text.length()>30) out.append(text).append("\n\n");
            }
            zip.closeEntry();
        }
        zip.close();
        return out.toString();
    }

    private String readDocx(Uri uri) throws Exception {
        InputStream in=context.getContentResolver().openInputStream(uri);
        if(in==null) throw new Exception("No stream");
        ZipInputStream zip=new ZipInputStream(in);
        String out="";
        ZipEntry e;
        while((e=zip.getNextEntry())!=null){
            if("word/document.xml".equals(e.getName())){
                String raw=readEntry(zip,1500000)
                        .replace("</w:p>","</w:p>\n")
                        .replace("<w:tab/>"," ");
                out=stripMarkup(raw);
                break;
            }
            zip.closeEntry();
        }
        zip.close();
        return out;
    }

    private String readText(Uri uri) throws Exception {
        InputStream in=context.getContentResolver().openInputStream(uri);
        if(in==null) throw new Exception("No stream");
        BufferedReader br=new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder(); String line;
        while((line=br.readLine())!=null && sb.length()<LIMIT*2) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private String readEntry(ZipInputStream zip,int max) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[8192]; int n,total=0;
        while((n=zip.read(buf))>0 && total<max){
            int take=Math.min(n,max-total);
            out.write(buf,0,take); total+=take;
        }
        return out.toString("UTF-8");
    }

    private String stripMarkup(String raw){
        if(raw==null)return "";
        String prepared=raw
                .replaceAll("(?is)<script.*?>.*?</script>"," ")
                .replaceAll("(?is)<style.*?>.*?</style>"," ")
                .replaceAll("(?i)</(p|div|h1|h2|h3|h4|li|tr|section|article)>","$0\n")
                .replaceAll("(?i)<br\\s*/?>","\n");
        return Html.fromHtml(prepared,Html.FROM_HTML_MODE_LEGACY).toString();
    }

    private String stripRtf(String rtf){
        if(rtf==null)return "";
        return rtf.replaceAll("\\\\'[0-9a-fA-F]{2}"," ")
                .replaceAll("\\\\[a-zA-Z]+-?\\d* ?"," ")
                .replace("{"," ").replace("}"," ");
    }

    private String crop(String s){
        if(s==null)return "";
        s=s.replace("\r"," ")
                .replaceAll("[\\t ]+"," ")
                .replaceAll("\\n[ \\t]+","\n")
                .replaceAll("\\n{3,}","\n\n")
                .trim();
        return s.length()>LIMIT?s.substring(0,LIMIT):s;
    }
}
