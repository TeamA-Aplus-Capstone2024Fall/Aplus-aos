package com.example.aplus_aos

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aplus_aos.ui.theme.Aplus_AOSTheme

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            Aplus_AOSTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
//                    WebViewScreen("http://192.168.1.113:3000")
                    WebViewScreen("https://skku-aplus-web.vercel.app/")
                }
            }
        }
    }

    @Composable
    fun WebViewScreen(url: String) {
        AndroidView(
            factory = { context ->
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                        }
                    }
                    loadUrl(url)
                }
                webView
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun WebViewPreview() {
        Aplus_AOSTheme {
//            WebViewScreen("http://192.168.1.113:3000")
            WebViewScreen("https://skku-aplus-web.vercel.app/")
        }
    }
}
