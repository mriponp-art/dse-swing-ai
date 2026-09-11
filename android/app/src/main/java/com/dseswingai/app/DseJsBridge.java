package com.dseswingai.app;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.util.concurrent.*;

public final class DseJsBridge {
    private final WebView webView; private final ExecutorService executor=Executors.newFixedThreadPool(2); private final Handler main=new Handler(Looper.getMainLooper());
    public DseJsBridge(WebView w){webView=w;}
    @JavascriptInterface public void fetchLatest(String callback){runJson(() -> DseDataAdapter.fetchLatest(),callback);}
    @JavascriptInterface public void runDailyScan(String callback){runJson(() -> DseScanEngine.run((done,total,sym)->postCallback("window.onDseProgress",progress(done,total,sym))),callback);}
    private JSONObject progress(int d,int t,String s){try{JSONObject o=new JSONObject();o.put("done",d);o.put("total",t);o.put("symbol",s);return o;}catch(Exception e){return new JSONObject();}}
    private void postCallback(String cb, JSONObject o){main.post(()->webView.evaluateJavascript(cb+"("+JSONObject.quote(o.toString())+");",null));}
    private interface Task{JSONObject go();}
    private void runJson(Task t,String cb){String safe=(cb==null||cb.isEmpty())?"window.onDseData":cb;executor.execute(()->{JSONObject r=t.go();main.post(()->webView.evaluateJavascript(safe+"("+JSONObject.quote(r.toString())+");",null));});}
    public void shutdown(){executor.shutdownNow();}
}
