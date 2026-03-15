package com.github.nyxiereal.viewquests

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.utils.DimenUtils

object CaptchaDialog {

    interface CaptchaCallback {
        fun onSolved(captchaKey: String)
        fun onCancel()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun show(ctx: Context, siteKey: String, rqdata: String?, callback: CaptchaCallback) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1F26"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val titleView = TextView(ctx).apply {
            text = "Complete the verification"
            setTextColor(Color.parseColor("#F2F3F5"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, DimenUtils.dpToPx(16), 0, DimenUtils.dpToPx(8))
        }

        val subText = TextView(ctx).apply {
            text = "Discord requires verification to claim this reward"
            setTextColor(Color.parseColor("#72767D"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(DimenUtils.dpToPx(16), 0, DimenUtils.dpToPx(16), DimenUtils.dpToPx(12))
        }

        val webView = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(340)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#17181C"))
                cornerRadius = DimenUtils.dpToPx(8).toFloat()
            }
        }

        var dialog: AlertDialog? = null

        val rqdataScript = if (!rqdata.isNullOrEmpty()) "window._hcaptcha_rqdata = '${rqdata}';" else ""

        val html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  body { background: #17181C; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }
  .h-captcha { margin: auto; }
</style>
<script src="https://js.hcaptcha.com/1/api.js" async defer></script>
</head>
<body>
<div class="h-captcha" data-sitekey="${siteKey}" data-callback="onCaptchaSolved" data-theme="dark"></div>
<script>
  $rqdataScript
  function onCaptchaSolved(token) {
    CaptchaBridge.onSolved(token);
  }
</script>
</body>
</html>
        """.trimIndent()

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onSolved(token: String) {
                com.aliucord.Utils.mainThread.post {
                    dialog?.dismiss()
                    callback.onSolved(token)
                }
            }
        }, "CaptchaBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!rqdata.isNullOrEmpty()) {
                    view?.evaluateJavascript("if(window.hcaptcha){hcaptcha.setData('#captcha-widget', {rqdata: '$rqdata'});}", null)
                }
            }
        }

        webView.loadDataWithBaseURL("https://discord.com", html, "text/html", "UTF-8", null)

        container.addView(titleView)
        container.addView(subText)
        container.addView(webView)

        dialog = AlertDialog.Builder(ctx)
            .setView(container)
            .setCancelable(true)
            .setOnCancelListener { callback.onCancel() }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss(); callback.onCancel() }
            .create()

        dialog!!.show()
        dialog!!.window?.setLayout(
            (ctx.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
