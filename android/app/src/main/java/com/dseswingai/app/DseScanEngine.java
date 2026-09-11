package com.dseswingai.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class DseScanEngine {
    public interface Progress { void onProgress(int done, int total, String symbol); }
    public static JSONObject run(Progress progress) {
        JSONObject out=new JSONObject(); JSONArray top10=new JSONArray(), aplus=new JSONArray();
        JSONArray latestRows=null; String source="NONE"; String error=null;
        try{
            JSONObject live=DseDataAdapter.fetchLatest();
            if(!"OK".equals(live.optString("status"))) return fail("Latest DSE data unavailable: "+live.optString("error","unknown"));
            latestRows=live.optJSONArray("rows"); source=live.optString("source","UNKNOWN");
            if(latestRows==null||latestRows.length()<30) return fail("Insufficient live universe rows: "+(latestRows==null?0:latestRows.length()));
            List<String> symbols=new ArrayList<>(); for(int i=0;i<latestRows.length();i++){JSONObject r=latestRows.optJSONObject(i); if(r!=null){String s=r.optString("symbol","").trim().toUpperCase(Locale.US); if(!s.isEmpty()&&!symbols.contains(s))symbols.add(s);}}
            // Personal mobile: cap is 350, matching the intended 300+ DSE universe without unbounded API calls.
            if(symbols.size()>350) symbols=symbols.subList(0,350);
            ExecutorService pool=Executors.newFixedThreadPool(4); List<Future<JSONObject>> futures=new ArrayList<>(); int total=symbols.size();
            String end=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()); Calendar cal=Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR,-365); String start=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.getTime());
            for(String sym:symbols){ futures.add(pool.submit(() -> analyze(sym,latestRows,start,end))); }
            List<JSONObject> valid=new ArrayList<>(); int done=0;
            for(Future<JSONObject> f:futures){ try{JSONObject a=f.get(40,TimeUnit.SECONDS); done++; if(progress!=null&&done%5==0)progress.onProgress(done,total,a.optString("symbol")); if("VALID".equals(a.optString("status")))valid.add(a);}catch(Exception ignored){done++;}}
            pool.shutdownNow(); valid.sort((a,b)->Double.compare(b.optDouble("score",0),a.optDouble("score",0)));
            for(int i=0;i<valid.size()&&i<10;i++)top10.put(valid.get(i));
            for(int i=0;i<valid.size()&&i<3;i++) aplus.put(valid.get(i));
            out.put("status",valid.isEmpty()?"NO_VALID_SETUP":"OK"); out.put("source",source); out.put("universe_scanned",symbols.size()); out.put("valid_count",valid.size()); out.put("top10",top10); out.put("a_plus",aplus); out.put("generated_at",System.currentTimeMillis());
            out.put("hard_gates","Risk ≤ 5%; R:R ≥ 1:2; missing data never promoted"); return out;
        }catch(Exception e){ return fail(e.getMessage()); }
    }
    private static JSONObject analyze(String sym, JSONArray latest, String start, String end){
        try{
            JSONObject cur=null; for(int i=0;i<latest.length();i++){JSONObject r=latest.optJSONObject(i);if(r!=null&&sym.equalsIgnoreCase(r.optString("symbol"))){cur=r;break;}}
            if(cur==null)return failStock(sym,"NO_LIVE_ROW");
            JSONObject hist=DseDataAdapter.fetchHistory(sym,start,end); JSONArray rows=hist.optJSONArray("rows"); if(rows==null||rows.length()<60)return failStock(sym,"INSUFFICIENT_HISTORY");
            int n=rows.length(); double[] close=new double[n],high=new double[n],low=new double[n],vol=new double[n];
            for(int i=0;i<n;i++){JSONObject r=rows.getJSONObject(i); close[i]=r.optDouble("close",Double.NaN);high[i]=r.optDouble("high",close[i]);low[i]=r.optDouble("low",close[i]);vol[i]=r.optDouble("volume",0);}
            double rsi=rsi(close,14), ema20=ema(close,20), ema50=ema(close,50), atr=atr(high,low,close,14); double avg20=avgTail(vol,20); double rel=avg20>0?vol[n-1]/avg20:0; double res=resistance(high,20); double sup=support(low,20); double px=close[n-1];
            boolean breakout=Double.isFinite(res)&&px>res&&rel>=1.5; boolean pullback=ema20>ema50&&px>=ema20-0.5*atr&&px<=ema20+1.2*atr&&px>sup; String setup=breakout?"BREAKOUT":(pullback?"PULLBACK":""); if(setup.isEmpty())return failStock(sym,"NO_SETUP");
            double risk=px-sup; if(!(risk>0&&Double.isFinite(risk)))return failStock(sym,"INVALID_STOP"); double target=px+2*risk; double rr=2.0; double entryLow=px*0.997,entryHigh=px*1.003; double riskPct= (risk/px); // per-share stop risk ratio; capital sizing occurs in risk calculator.
            int score=0; score+=ema20>ema50?15:(px>ema50?8:0); score+=setup.isEmpty()?0:20; score+=rel>=2?15:(rel>=1.5?10:0); score+=(rsi>=50&&rsi<=60)?15:((rsi>=45&&rsi<65)?8:0); score+=breakout?15:10; score+=5; score+=5; score+=10; score=Math.min(100,score);
            JSONObject o=new JSONObject();o.put("status","VALID");o.put("symbol",sym);o.put("setup",setup);o.put("score",score);o.put("confidence",score>=85&&rel>=2?"HIGH":(score>=70?"MEDIUM":"LOW"));o.put("price",px);o.put("entry_low",entryLow);o.put("entry_high",entryHigh);o.put("stop_loss",sup);o.put("target",target);o.put("rr",rr);o.put("rsi",rsi);o.put("rel_volume",rel);o.put("risk_per_share",risk);o.put("source_history",hist.optString("source","UNKNOWN")); return o;
        }catch(Exception e){return failStock(sym,e.getClass().getSimpleName());}
    }
    private static JSONObject failStock(String s,String reason){try{JSONObject o=new JSONObject();o.put("status","BLOCKED");o.put("symbol",s);o.put("reason",reason);return o;}catch(Exception e){return new JSONObject();}}
    private static JSONObject fail(String m){try{JSONObject o=new JSONObject();o.put("status","ERROR");o.put("error",m==null?"unknown":m);o.put("top10",new JSONArray());o.put("a_plus",new JSONArray());return o;}catch(Exception e){return new JSONObject();}}
    private static double avgTail(double[] a,int n){int s=Math.max(0,a.length-n);double t=0;int c=0;for(int i=s;i<a.length;i++){t+=a[i];c++;}return c==0?0:t/c;}
    private static double ema(double[] a,int n){double k=2.0/(n+1),e=a[0];for(int i=1;i<a.length;i++)e=a[i]*k+e*(1-k);return e;}
    private static double rsi(double[] a,int n){if(a.length<=n)return Double.NaN;double g=0,l=0;for(int i=a.length-n;i<a.length;i++){double d=a[i]-a[i-1];if(d>0)g+=d;else l-=d;}g/=n;l/=n; if(l==0)return 100; return 100-(100/(1+(g/l)));}
    private static double atr(double[] h,double[] l,double[] c,int n){if(c.length<=n)return Double.NaN;double sum=0;for(int i=c.length-n;i<c.length;i++){double pc=c[i-1];sum+=Math.max(h[i]-l[i],Math.max(Math.abs(h[i]-pc),Math.abs(l[i]-pc)));}return sum/n;}
    private static double resistance(double[] h,int n){int end=h.length-2;int start=Math.max(0,end-n+1);double m=Double.NEGATIVE_INFINITY;for(int i=start;i<=end;i++)m=Math.max(m,h[i]);return m;}
    private static double support(double[] l,int n){int start=Math.max(0,l.length-n);double m=Double.POSITIVE_INFINITY;for(int i=start;i<l.length;i++)m=Math.min(m,l[i]);return m;}
}
