package com.example.rideriot.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.example.rideriot.R

class CameraFragment : Fragment() {

    private lateinit var webView: WebView
    private val STREAM_URL = "http://172.20.0.154:5000/stream"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.cameraWebView)

        // Configura a WebView para esticar a imagem e ignorar a cache
        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.webViewClient = WebViewClient()

        // HTML embebido para centrar a imagem no ecrã preto
        val html = """
            <html>
            <body style="margin:0;padding:0;background-color:black;display:flex;justify-content:center;align-items:center;">
                <img src="$STREAM_URL" style="width:100%; height:auto;" />
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Pára de consumir dados quando sais do separador
        webView.loadUrl("about:blank")
    }
}