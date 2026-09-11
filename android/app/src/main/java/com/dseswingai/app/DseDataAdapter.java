package com.dseswingai.app;

import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Mobile-only real DSE adapter. No fabricated prices. Primary source is an
 * unofficial DSE crawler API; direct DSE endpoints are fallback probes.
 */
public final class DseDataAdapter {
    public static final String SOURCE_PROXY = "UNOFFICIAL_DSE_PROXY";
    public static final String SOURCE_DIRECT = "DSE_DIRECT";
    public static final String OK = "OK";
    public static final String INSUFFICIENT = "INSUFFICIENT_DATA";
    public static final String ERROR = "ERROR";
    private static final int TIMEOUT_MS = 12000;
    private static final String LATEST_PROXY = "https://bdstock.org/v1/dse/latest";
    private static final String HIST_PROXY = "https://bdstock.org/v1/dse/historical";
    private static final String DSE_ALL = "https://dsebd.org/latest_share_price_all.php";
    private static final String DSE_SCROLL = "https://dsebd.org/latest_share_price_scroll_by_ajax.php";
    private DseDataAdapter() {}

    public static JSONObject fetchLatest() { return fetchLatestInternal(); }

    public static JSONObject fetchHistory(String symbol, String start, String end) {
        long t = System.currentTimeMillis();
        List<JSONObject> rows = new ArrayList<>();
        String err = null;
        String url = HIST_PROXY + "?start=" + enc(start) + "&end=" + enc(end) + "&code=" + enc(symbol);
        try {
            String body = httpGet(url);
            Object root = new org.json.JSONTokener(body).nextValue();
            JSONArray arr = root instanceof JSONArray ? (JSONArray)root : findArray((JSONObject)root);
            if (arr != null) {
                for (int i=0;i<arr.length();i++) {
                    JSONObject raw=arr.optJSONObject(i); if(raw==null) continue;
                    JSONObject n=normalizeHist(raw); if(n!=null) rows.add(n);
                }
            }
        } catch(Exception e){ err=e.getClass().getSimpleName()+": "+e.getMessage(); }
        JSONObject out=new JSONObject();
        try {
            out.put("status", rows.size()>=30?OK:INSUFFICIENT); out.put("source", SOURCE_PROXY);
            out.put("symbol",symbol); out.put("requested_at",t); out.put("received_at",System.currentTimeMillis());
            out.put("rows", new JSONArray(rows)); out.put("row_count",rows.size());
            if(err!=null) out.put("error",err);
        } catch(Exception ignored){}
        return out;
    }

    private static JSONObject fetchLatestInternal(){
        long t=System.currentTimeMillis();
        String[] urls={LATEST_PROXY,DSE_ALL,DSE_SCROLL};
        String[] sources={SOURCE_PROXY,SOURCE_DIRECT,SOURCE_DIRECT};
        Exception last=null;
        for(int i=0;i<urls.length;i++){
            try{
                String body=httpGet(urls[i]); JSONArray rows=parseRows(body);
                if(rows.length()>0){
                    JSONObject r=new JSONObject(); r.put("status",OK); r.put("source",sources[i]); r.put("requested_at",t); r.put("received_at",System.currentTimeMillis());
                    r.put("freshness_seconds",0); r.put("row_count",rows.length()); r.put("rows",rows);
                    if(i==0) r.put("warning","Unofficial proxy source; verify freshness before trading.");
                    return r;
                }
            }catch(Exception e){ last=e; }
        }
        JSONObject r=new JSONObject(); try{
            r.put("status",ERROR); r.put("source","NONE"); r.put("requested_at",t); r.put("received_at",System.currentTimeMillis());
            r.put("freshness_seconds",-1); r.put("row_count",0); r.put("rows",new JSONArray());
            r.put("error",last==null?"No usable DSE source responded":String.valueOf(last));
        }catch(Exception ignored){} return r;
    }

    private static JSONArray parseRows(String body) throws Exception {
        Object root=new org.json.JSONTokener(body).nextValue();
        JSONArray a;
        if(root instanceof JSONArray) a=(JSONArray)root; else a=findArray((JSONObject)root);
        if(a==null) return new JSONArray(); JSONArray out=new JSONArray();
        for(int i=0;i<a.length();i++){ JSONObject raw=a.optJSONObject(i); if(raw==null) continue; JSONObject n=normalizeCurrent(raw); if(n!=null) out.put(n); }
        return out;
    }

    private static JSONArray findArray(JSONObject o){ if(o==null)return null; String[] keys={"data","results","stocks","rows","records"}; for(String k:keys){JSONArray a=o.optJSONArray(k); if(a!=null)return a;} return null; }
    private static JSONObject normalizeCurrent(JSONObject raw){
        String s=firstString(raw,"TRADING CODE","trading_code","symbol","code","name"); Double l=firstNumber(raw,"LTP*","ltp","LTP","current_price","price");
        if(s==null||l==null)return null; JSONObject n=new JSONObject(); try{n.put("symbol",s.trim().toUpperCase(Locale.US)); n.put("ltp",l);
            putNum(n,"high",firstNumber(raw,"HIGH","high")); putNum(n,"low",firstNumber(raw,"LOW","low")); putNum(n,"close",firstNumber(raw,"CLOSEP*","close","closep")); putNum(n,"ycp",firstNumber(raw,"YCP*","ycp")); putNum(n,"change",firstNumber(raw,"CHANGE","change")); putNum(n,"volume",firstNumber(raw,"VOLUME","volume")); putNum(n,"trade",firstNumber(raw,"TRADE","trade"));
            return n;}catch(Exception e){return null;}
    }
    private static JSONObject normalizeHist(JSONObject raw){
        String d=firstString(raw,"date","DATE","TRADING DATE","timestamp"); Double o=firstNumber(raw,"open","OPENP*","OPEN","openp"); Double h=firstNumber(raw,"high","HIGH"); Double l=firstNumber(raw,"low","LOW"); Double c=firstNumber(raw,"close","CLOSEP*","CLOSE"); Double v=firstNumber(raw,"volume","VOLUME"); if(d==null||c==null)return null; JSONObject n=new JSONObject(); try{n.put("date",d); if(o!=null)n.put("open",o); if(h!=null)n.put("high",h); if(l!=null)n.put("low",l); n.put("close",c); if(v!=null)n.put("volume",v); return n;}catch(Exception e){return null;}
    }
    private static void putNum(JSONObject o,String k,Double v)throws Exception{if(v!=null)o.put(k,v);}
    private static String firstString(JSONObject o,String...keys){for(String k:keys){if(o.has(k)&&!o.isNull(k)){String s=String.valueOf(o.opt(k)).trim(); if(!s.isEmpty()&&!s.equalsIgnoreCase("null"))return s;}}return null;}
    private static Double firstNumber(JSONObject o,String...keys){for(String k:keys){if(!o.has(k)||o.isNull(k))continue; Object v=o.opt(k); if(v instanceof Number)return ((Number)v).doubleValue(); try{String s=String.valueOf(v).replace(",","").trim(); if(!s.isEmpty())return Double.parseDouble(s);}catch(Exception ignored){}}return null;}
    private static String enc(String s)throws UnsupportedEncodingException{return java.net.URLEncoder.encode(s,StandardCharsets.UTF_8.name());}
    private static String httpGet(String urlString)throws IOException{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(urlString).openConnection();c.setRequestMethod("GET");c.setConnectTimeout(TIMEOUT_MS);c.setReadTimeout(TIMEOUT_MS);c.setUseCaches(false);c.setRequestProperty("Accept","application/json,text/html,*/*");c.setRequestProperty("User-Agent","DSE-Swing-AI-Personal/2.0");int code=c.getResponseCode();InputStream s=code>=200&&code<300?c.getInputStream():c.getErrorStream();if(s==null)throw new IOException("HTTP "+code);String body=readFully(s);if(code<200||code>=300)throw new IOException("HTTP "+code+": "+body);return body;}finally{if(c!=null)c.disconnect();}}
    private static String readFully(InputStream in)throws IOException{StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String x;while((x=r.readLine())!=null)b.append(x);}return b.toString();}
}
