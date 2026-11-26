
package com.linkpilot.browser

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ContextMenu
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import android.net.Uri
import org.json.JSONTokener
import org.json.JSONObject
import java.io.ByteArrayInputStream

private data class BrowserTab(
    var url: String,
    var title: String = "تب جدید",
    var webView: WebView? = null,
    var loaded: Boolean = false
)

class MainActivity : Activity() {
    private val bg = Color.rgb(17, 17, 19)
    private val panel = Color.rgb(31, 31, 35)
    private val red = Color.rgb(220, 30, 42)
    private val white = Color.WHITE

    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var webHost: FrameLayout
    private lateinit var tabLabel: TextView

    private val tabs = mutableListOf<BrowserTab>()
    private var activeIndex = 0

    private val prefs by lazy {
        getSharedPreferences("mili_chrome", Context.MODE_PRIVATE)
    }

    private var adBlockEnabled = true
    private var botAddress = "@V2TGbot"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adBlockEnabled = prefs.getBoolean("adblock", true)
        botAddress = prefs.getString("bot", "@V2TGbot") ?: "@V2TGbot"

        buildBrowserUi()

        tabs.add(BrowserTab("https://www.google.com"))
        activateTab(0)
    }

    private fun buildBrowserUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 6)
        }

        val brand = TextView(this).apply {
            text = "M"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            gravity = Gravity.CENTER
            setBackgroundColor(red)
        }

        top.addView(brand, LinearLayout.LayoutParams(dp(38), dp(38)))

        address = EditText(this).apply {
            hint = "جستجو یا نشانی وب"
            setSingleLine(true)
            textSize = 14f
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), 0, dp(8), 0)
            setBackgroundColor(panel)
            imeOptions = EditorInfo.IME_ACTION_GO

            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI

            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) {
                    navigate(address.text.toString())
                    true
                } else {
                    false
                }
            }
        }

        top.addView(
            address,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(8, 0, 8, 0)
            }
        )

        top.addView(
            makeTopButton("↻") {
                currentWeb()?.reload()
            },
            LinearLayout.LayoutParams(dp(40), dp(42))
        )

        root.addView(top)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(4, 0, 4, 6)
        }

        actions.addView(makeAction("‹") {
            currentWeb()?.goBack()
        })

        actions.addView(makeAction("›") {
            currentWeb()?.goForward()
        })

        actions.addView(makeAction("＋") {
            createBlankTab(activate = true)
        })

        tabLabel = TextView(this).apply {
            text = "1"
            setTextColor(white)
            textSize = 14f
            gravity = Gravity.CENTER
            setOnClickListener {
                showTabs()
            }
        }

        actions.addView(
            tabLabel,
            LinearLayout.LayoutParams(0, dp(40), 1f)
        )

        actions.addView(makeAction("⋮") {
            showMainMenu()
        })

        root.addView(actions)

        webHost = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        root.addView(
            webHost,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)
    }

    private fun makeTopButton(
        text: String,
        click: () -> Unit
    ) = TextView(this).apply {
        this.text = text
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(white)
        setOnClickListener { click() }
    }

    private fun makeAction(
        text: String,
        click: () -> Unit
    ) = TextView(this).apply {
        this.text = text
        textSize = 25f
        gravity = Gravity.CENTER
        setTextColor(white)
        layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
        setOnClickListener { click() }
    }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun currentTab() = tabs.getOrNull(activeIndex)

    private fun currentWeb() = currentTab()?.webView

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim()

        if (value.startsWith("https://") ||
            value.startsWith("http://")
        ) {
            return value
        }

        return if (value.contains('.') && !value.contains(' ')) {
            "https://$value"
        } else {
            "https://www.google.com/search?q=${Uri.encode(value)}"
        }
    }

    private fun navigate(raw: String) {
        val url = normalizeUrl(raw)
        val tab = currentTab() ?: return

        tab.url = url
        tab.loaded = true

        val wv = ensureWebView(tab)

        address.setText(url)
        wv.loadUrl(url)
    }

    private fun createBlankTab(activate: Boolean) {
        tabs.add(BrowserTab("about:blank"))

        if (activate) {
            activateTab(tabs.lastIndex)
        } else {
            updateTabCount()
        }
    }

    // تب پس‌زمینه فقط نشانی را ذخیره می‌کند.
    // تا زمان فعال شدن تب هیچ WebView یا بارگیری ساخته نمی‌شود.
    private fun openLinkInBackground(url: String) {
        tabs.add(BrowserTab(url))
        updateTabCount()

        Toast.makeText(
            this,
            "تب جدید در پس‌زمینه ذخیره شد؛ هنوز بارگیری نشده است",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun activateTab(index: Int) {
        if (index !in tabs.indices) return

        currentWeb()?.onPause()

        activeIndex = index

        val tab = tabs[index]
        val wv = ensureWebView(tab)

        webHost.removeAllViews()

        if (wv.parent is ViewGroup) {
            (wv.parent as ViewGroup).removeView(wv)
        }

        webHost.addView(
            wv,
            FrameLayout.LayoutParams(-1, -1)
        )

        wv.onResume()

        if (!tab.loaded && tab.url != "about:blank") {
            tab.loaded = true
            wv.loadUrl(tab.url)
        } else if (tab.url == "about:blank") {
            address.setText("")
        }

        if (tab.url != "about:blank") {
            address.setText(tab.url)
        }

        updateTabCount()
    }

    private fun ensureWebView(tab: BrowserTab): WebView {
        tab.webView?.let { return it }

        val wv = WebView(this)

        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.javaScriptCanOpenWindowsAutomatically = false
        wv.settings.loadsImagesAutomatically = true
        wv.settings.setSupportMultipleWindows(false)

        wv.webChromeClient = WebChromeClient()

        wv.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {

                if (
                    adBlockEnabled &&
                    request != null &&
                    isAdUrl(request.url.toString())
                ) {
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                if (url != null) {
                    tab.url = url

                    tab.title =
                        view?.title?.takeIf { it.isNotBlank() } ?: url

                    if (tabs.getOrNull(activeIndex) === tab) {
                        address.setText(url)
                    }
                }
            }
        }

        // منوی لمس طولانی لینک
        wv.setOnLongClickListener {
            val hit = wv.hitTestResult

            if (
                hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            ) {
                val link = hit.extra.orEmpty()

                if (link.isNotBlank()) {
                    showLinkMenu(wv, link)
                }

                true
            } else {
                false
            }
        }

        // منوی ترجمه متن انتخاب‌شده
        // این روش به جای customSelectionActionModeCallback استفاده می‌شود.
        wv.setOnCreateContextMenuListener { menu, _, _ ->

            menu.add(0, 7101, 100, "ترجمه").setOnMenuItemClickListener {

                wv.evaluateJavascript(
                    "window.getSelection().toString()"
                ) { value ->

                    val selected = try {
                        JSONTokener(value).nextValue() as? String ?: ""
                    } catch (_: Exception) {
                        ""
                    }

                    if (selected.isNotBlank()) {
                        showTranslation(selected)
                    }
                }

                true
            }
        }

        tab.webView = wv
        return wv
    }

    private fun isAdUrl(raw: String): Boolean {
        val host = try {
            Uri.parse(raw).host?.lowercase() ?: return false
        } catch (_: Exception) {
            return false
        }

        val domains = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "adnxs.com",
            "taboola.com",
            "outbrain.com",
            "popads.net",
            "popcash.net",
            "adsterra.com"
        )

        return domains.any {
            host == it || host.endsWith(".$it")
        } || host.startsWith("ads.") || host.startsWith("ad.")
    }

    private fun showLinkMenu(wv: WebView, link: String) {
        val items = arrayOf(
            "باز کردن در تب جدید (بدون بارگیری)",
            "کپی لینک",
            "ترجمه عنوان لینک",
            "ارسال دستور تلگرام"
        )

        AlertDialog.Builder(this)
            .setTitle("عملیات لینک")
            .setItems(items) { _, which ->

                when (which) {

                    0 -> openLinkInBackground(link)

                    1 -> copy(link)

                    2 -> {
                        val js =
                            "(function(){const a=[...document.querySelectorAll('a')].find(x=>x.href===${JSONObject.quote(link)});return a?(a.innerText||a.title||a.getAttribute('aria-label')||a.getAttribute('alt')||''):'';})()"

                        wv.evaluateJavascript(js) { raw ->

                            val title = try {
                                JSONTokener(raw).nextValue() as? String ?: ""
                            } catch (_: Exception) {
                                ""
                            }

                            showTranslation(title.ifBlank { link })
                        }
                    }

                    3 -> {
                        Toast.makeText(
                            this,
                            "ارسال مستقیم با حساب شخصی تلگرام هنوز به اتصال امن TDLib نیاز دارد و در این نسخه فعال نیست.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun showTranslation(text: String) {
        val url =
            "https://translate.google.com/?sl=auto&tl=fa&text=${Uri.encode(text)}&op=translate"

        val dialog = AlertDialog.Builder(this).create()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        layout.addView(
            TextView(this).apply {
                this.text = "ترجمه Google · متن انتخاب‌شده"
                setTextColor(white)
                textSize = 16f
                setPadding(4, 4, 4, 8)
            }
        )

        val translateView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl(url)
        }

        layout.addView(
            translateView,
            LinearLayout.LayoutParams(-1, dp(440))
        )

        dialog.setView(layout)

        dialog.setButton(
            AlertDialog.BUTTON_POSITIVE,
            "بستن"
        ) { _, _ ->
            translateView.destroy()
            dialog.dismiss()
        }

        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * .94).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun copy(value: String) {
        val cm =
            getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager

        cm.setPrimaryClip(
            android.content.ClipData.newPlainText(
                "Mili Chrome",
                value
            )
        )

        Toast.makeText(
            this,
            "کپی شد",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showTabs() {
        if (tabs.isEmpty()) return

        val rows = tabs.mapIndexed { i, t ->

            val status =
                if (t.loaded || t.url == "about:blank") {
                    "بارگذاری‌شده"
                } else {
                    "در انتظار بارگذاری"
                }

            "${if (i == activeIndex) "● " else ""}${i + 1}. ${t.title}\n$status"

        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("تب‌ها (${tabs.size})")
            .setItems(rows) { _, which ->
                activateTab(which)
            }
            .setPositiveButton("تب خالی جدید") { _, _ ->
                createBlankTab(true)
            }
            .setNeutralButton("بستن تب فعلی") { _, _ ->
                closeCurrentTab()
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun closeCurrentTab() {
        if (tabs.size <= 1) {
            currentWeb()?.loadUrl("about:blank")
            tabs[0] = BrowserTab("about:blank")
            activateTab(0)
            return
        }

        val removed = tabs.removeAt(activeIndex)

        removed.webView?.let {
            it.stopLoading()
            it.destroy()
        }

        activeIndex = (activeIndex - 1).coerceAtLeast(0)

        activateTab(activeIndex)
    }

    private fun updateTabCount() {
        if (::tabLabel.isInitialized) {
            tabLabel.text = tabs.size.toString()
        }
    }

    private fun showMainMenu() {
        val items = arrayOf(
            "تب‌ها",
            "مدیریت دستورها",
            "ربات تلگرام مقصد",
            "مسدودکننده تبلیغات: ${if (adBlockEnabled) "روشن" else "خاموش"}",
            "درباره Mili Chrome"
        )

        AlertDialog.Builder(this)
            .setTitle("Mili Chrome")
            .setItems(items) { _, which ->

                when (which) {

                    0 -> showTabs()

                    1 -> showCommandsInfo()

                    2 -> editBotAddress()

                    3 -> {
                        adBlockEnabled = !adBlockEnabled

                        prefs.edit()
                            .putBoolean("adblock", adBlockEnabled)
                            .apply()

                        Toast.makeText(
                            this,
                            "مسدودکننده ${if (adBlockEnabled) "روشن" else "خاموش"} شد",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    4 -> {
                        AlertDialog.Builder(this)
                            .setMessage(
                                "Mili Chrome · مرورگر آزمایشی مبتنی بر Android WebView/Chromium"
                            )
                            .setPositiveButton("باشه", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun editBotAddress() {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(botAddress)
            hint = "@YourBot"
        }

        AlertDialog.Builder(this)
            .setTitle("ربات مقصد تلگرام")
            .setMessage("آدرس ربات قابل ویرایش است.")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->

                botAddress = input.text.toString()

                prefs.edit()
                    .putString("bot", botAddress)
                    .apply()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showCommandsInfo() {
        AlertDialog.Builder(this)
            .setTitle("دستورهای تلگرام")
            .setMessage(
                "در این نسخه منوی مدیریت اولیه است. ارسال خودکار از حساب شخصی تلگرام هنوز پیاده‌سازی نشده؛ برای آن اتصال TDLib و ورود امن حساب لازم است."
            )
            .setPositiveButton("متوجه شدم", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val wv = currentWeb()

        if (wv?.canGoBack() == true) {
            wv.goBack()
        } else if (tabs.size > 1) {
            closeCurrentTab()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        currentWeb()?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        currentWeb()?.onResume()
    }

    override fun onDestroy() {
        tabs.forEach {
            it.webView?.let { w ->
                w.stopLoading()
                w.destroy()
            }
        }

        super.onDestroy()
    }
}
