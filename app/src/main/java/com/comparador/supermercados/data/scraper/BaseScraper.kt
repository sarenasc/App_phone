package com.comparador.supermercados.data.scraper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.comparador.supermercados.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

abstract class BaseScraper(protected val context: Context) {

    // Carga la URL en WebView (resuelve Cloudflare) y luego llama al API path via fetch()
    protected suspend fun fetchJsonViaWebView(pageUrl: String, apiPath: String): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val wv = WebView(context)
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = CHROME_UA
                }

                val handler = Handler(Looper.getMainLooper())
                val timeout = Runnable {
                    if (cont.isActive) cont.resumeWithException(Exception("Timeout: $pageUrl"))
                    wv.destroy()
                }
                handler.postDelayed(timeout, 25_000)

                class Bridge {
                    @JavascriptInterface fun onResult(json: String) {
                        handler.removeCallbacks(timeout)
                        if (cont.isActive) cont.resume(json)
                        handler.post { wv.destroy() }
                    }
                    @JavascriptInterface fun onError(msg: String) {
                        handler.removeCallbacks(timeout)
                        if (cont.isActive) cont.resumeWithException(Exception(msg))
                        handler.post { wv.destroy() }
                    }
                }
                wv.addJavascriptInterface(Bridge(), "WVBridge")

                wv.webViewClient = object : WebViewClient() {
                    private var done = false
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (done) return
                        val js = """
                            (function tryFetch(retries) {
                                if (document.title === 'Just a moment...' && retries > 0) {
                                    setTimeout(function(){ tryFetch(retries-1); }, 1500);
                                    return;
                                }
                                fetch('$apiPath', {
                                    credentials: 'include',
                                    headers: {
                                        'Accept': 'application/json, text/plain, */*',
                                        'x-requested-with': 'XMLHttpRequest'
                                    }
                                })
                                .then(function(r){ return r.text(); })
                                .then(function(t){ WVBridge.onResult(t); })
                                .catch(function(e){ WVBridge.onError(String(e)); });
                            })(10);
                        """
                        view.evaluateJavascript(js, null)
                        done = true
                    }
                    override fun onReceivedError(view: WebView, code: Int, desc: String, url: String) {
                        handler.removeCallbacks(timeout)
                        if (cont.isActive) cont.resumeWithException(Exception("Error $code"))
                        wv.destroy()
                    }
                }

                wv.loadUrl(pageUrl)
                cont.invokeOnCancellation {
                    handler.removeCallbacks(timeout)
                    handler.post { wv.destroy() }
                }
            }
        }

    // Extrae el estado interno de VTEX IO (__STATE__ / __STORE_STATE__) desde la página renderizada
    protected suspend fun fetchPageStateViaWebView(pageUrl: String, query: String, domain: String): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val wv = WebView(context)
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = CHROME_UA
                }
                val handler = Handler(Looper.getMainLooper())
                val timeout = Runnable {
                    if (cont.isActive) cont.resumeWithException(Exception("Timeout: $pageUrl"))
                    wv.destroy()
                }
                handler.postDelayed(timeout, 30_000)

                class Bridge {
                    @JavascriptInterface fun onResult(json: String) {
                        handler.removeCallbacks(timeout)
                        if (cont.isActive) cont.resume(json)
                        handler.post { wv.destroy() }
                    }
                    @JavascriptInterface fun onError(msg: String) {
                        handler.removeCallbacks(timeout)
                        if (cont.isActive) cont.resumeWithException(Exception(msg))
                        handler.post { wv.destroy() }
                    }
                }
                wv.addJavascriptInterface(Bridge(), "WVBridge")

                wv.webViewClient = object : WebViewClient() {
                    private var done = false
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (done) return
                        val js = """
                            (function tryExtract(retries) {
                                if (document.title === 'Just a moment...' && retries > 0) {
                                    setTimeout(function(){ tryExtract(retries-1); }, 1500);
                                    return;
                                }
                                try {
                                    // VTEX IO: estado global de la tienda
                                    var state = window.__STATE__ || window.__STORE_STATE__ || window.__RUNTIME__;
                                    if (state) { WVBridge.onResult(JSON.stringify(state)); return; }

                                    // VTEX IO: buscar en scripts inline
                                    var scripts = document.querySelectorAll('script[type="application/json"]');
                                    for (var i = 0; i < scripts.length; i++) {
                                        var txt = scripts[i].textContent;
                                        if (txt && (txt.indexOf('productName') > -1 || txt.indexOf('"products"') > -1)) {
                                            WVBridge.onResult(txt); return;
                                        }
                                    }

                                    // JSON-LD estructurado
                                    var ldScripts = document.querySelectorAll('script[type="application/ld+json"]');
                                    for (var j = 0; j < ldScripts.length; j++) {
                                        var ld = ldScripts[j].textContent;
                                        if (ld && ld.indexOf('ItemList') > -1) {
                                            WVBridge.onResult(ld); return;
                                        }
                                    }

                                    WVBridge.onError('Estado VTEX no encontrado en ' + document.title);
                                } catch(e) {
                                    WVBridge.onError(String(e));
                                }
                            })(10);
                        """
                        view.evaluateJavascript(js, null)
                        done = true
                    }
                    override fun onReceivedError(view: WebView, code: Int, desc: String, url: String) {
                        handler.removeCallbacks(timeout)
                        if (cont.isActive) cont.resumeWithException(Exception("Error web: $desc"))
                        wv.destroy()
                    }
                }
                wv.loadUrl(pageUrl)
                cont.invokeOnCancellation {
                    handler.removeCallbacks(timeout)
                    handler.post { wv.destroy() }
                }
            }
        }

    // Carga la URL en WebView y extrae el contenido de script#__NEXT_DATA__
    protected suspend fun fetchNextDataViaWebView(url: String): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val wv = WebView(context)
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = CHROME_UA
                }

                val handler = Handler(Looper.getMainLooper())
                val timeout = Runnable {
                    if (cont.isActive) cont.resumeWithException(Exception("Timeout: $url"))
                    wv.destroy()
                }
                handler.postDelayed(timeout, 25_000)

                class Bridge {
                    @JavascriptInterface fun onResult(json: String) {
                        handler.removeCallbacks(timeout)
                        if (cont.isActive) cont.resume(json)
                        handler.post { wv.destroy() }
                    }
                    @JavascriptInterface fun onError(msg: String) {
                        handler.removeCallbacks(timeout)
                        if (cont.isActive) cont.resumeWithException(Exception(msg))
                        handler.post { wv.destroy() }
                    }
                }
                wv.addJavascriptInterface(Bridge(), "WVBridge")

                wv.webViewClient = object : WebViewClient() {
                    private var done = false
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (done) return
                        val js = """
                            (function tryExtract(retries) {
                                if (document.title === 'Just a moment...' && retries > 0) {
                                    setTimeout(function(){ tryExtract(retries-1); }, 1500);
                                    return;
                                }
                                var el = document.getElementById('__NEXT_DATA__');
                                if (el && el.textContent) {
                                    WVBridge.onResult(el.textContent);
                                } else {
                                    WVBridge.onError('__NEXT_DATA__ no encontrado en ' + document.title);
                                }
                            })(8);
                        """
                        view.evaluateJavascript(js, null)
                        done = true
                    }
                    override fun onReceivedError(view: WebView, code: Int, desc: String, url: String) {
                        handler.removeCallbacks(timeout)
                        if (cont.isActive) cont.resumeWithException(Exception("Error web: $desc"))
                        wv.destroy()
                    }
                }

                wv.loadUrl(url)
                cont.invokeOnCancellation {
                    handler.removeCallbacks(timeout)
                    handler.post { wv.destroy() }
                }
            }
        }

    abstract suspend fun search(query: String): List<Product>

    companion object {
        const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
    }
}
