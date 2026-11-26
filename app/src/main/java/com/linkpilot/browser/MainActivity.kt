package com.linkpilot.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID

private data class BrowserTab(
    var url: String,
    var title: String = "تب جدید",
    var webView: WebView? = null,
    var loaded: Boolean = false
)

private data class BrowserEntry(val title: String, val url: String, val time: Long)

class MainActivity : Activity() {
    private val bg = Color.rgb(17, 17, 19)
    private val panel = Color.rgb(31, 31, 35)
    private val red = Color.rgb(220, 30, 42)
    private val white = Color.WHITE
    private val muted = Color.rgb(185, 185, 190)

    private lateinit var address: EditText
    private lateinit var webHost: FrameLayout
    private lateinit var tabLabel: TextView
    private lateinit var pageProgress: ProgressBar
    private val tabs = mutableListOf<BrowserTab>()
    private var activeIndex = 0
    private val prefs by lazy { getSharedPreferences("mili_chrome", Context.MODE_PRIVATE) }
    private val commandStore by lazy { CommandStore(this) }
    private var adBlockEnabled = true
    private var botAddress = "@V2TGbot"
    private var homePage = "https://www.google.com"
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 4812

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adBlockEnabled = prefs.getBoolean("adblock", true)
        botAddress = prefs.getString("bot", "@V2TGbot") ?: "@V2TGbot"
        homePage = prefs.getString("home", "https://www.google.com") ?: "https://www.google.com"
        buildBrowserUi()
        tabs.add(BrowserTab(homePage))
        activateTab(0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun currentTab() = tabs.getOrNull(activeIndex)
    private fun currentWeb() = currentTab()?.webView

    private fun label(text: String, size: Float = 14f, color: Int = white) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun buildBrowserUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(6))
        }
        val brand = TextView(this).apply {
            text = "M"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            gravity = Gravity.CENTER
            setBackgroundColor(red)
        }
        top.addView(brand, LinearLayout.LayoutParams(dp(38), dp(40)))

        address = EditText(this).apply {
            hint = "جستجو یا نشانی وب"
            setSingleLine(true)
            textSize = 14f
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(12), 0, dp(8), 0)
            setBackgroundColor(panel)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) {
                    navigate(text.toString())
                    true
                } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(8), 0, dp(6), 0) })
        top.addView(topButton("↻") { currentWeb()?.reload() }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(top)

        pageProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(red)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(panel)
        }
        root.addView(pageProgress, LinearLayout.LayoutParams(-1, dp(2)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), dp(5))
        }
        actions.addView(actionButton("‹") { if (currentWeb()?.canGoBack() == true) currentWeb()?.goBack() })
        actions.addView(actionButton("›") { if (currentWeb()?.canGoForward() == true) currentWeb()?.goForward() })
        actions.addView(actionButton("⌂") { navigate(homePage) })
        actions.addView(actionButton("＋") { createBlankTab(true) })
        tabLabel = TextView(this).apply {
            text = "1"
            textSize = 14f
            setTextColor(white)
            gravity = Gravity.CENTER
            setOnClickListener { showTabs() }
        }
        actions.addView(tabLabel, LinearLayout.LayoutParams(0, dp(42), 1f))
        actions.addView(actionButton("⋮") { showMainMenu() })
        root.addView(actions)

        webHost = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        root.addView(webHost, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        window.statusBarColor = bg
        window.navigationBarColor = bg
    }

    private fun topButton(text: String, click: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 23f
        gravity = Gravity.CENTER
        setTextColor(white)
        setOnClickListener { click() }
    }

    private fun actionButton(text: String, click: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 24f
        gravity = Gravity.CENTER
        setTextColor(white)
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        setOnClickListener { click() }
    }

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return homePage
        if (value.startsWith("https://", true) || value.startsWith("http://", true)) return value
        if (value.startsWith("about:")) return value
        return if (value.contains('.') && !value.contains(' ')) "https://$value"
        else "https://www.google.com/search?q=${Uri.encode(value)}"
    }

    private fun navigate(raw: String) {
        val url = normalizeUrl(raw)
        val tab = currentTab() ?: return
        tab.url = url
        tab.loaded = true
        address.setText(url)
        address.clearFocus()
        currentWeb()?.loadUrl(url) ?: ensureWebView(tab).loadUrl(url)
    }

    private fun createBlankTab(activate: Boolean) {
        tabs.add(BrowserTab("about:blank"))
        if (activate) activateTab(tabs.lastIndex) else updateTabCount()
    }

    // Background tabs are URL-only records. A WebView and network request are created only on activation.
    private fun openLinkInBackground(url: String) {
        tabs.add(BrowserTab(url))
        updateTabCount()
        Toast.makeText(this, "تب در پس‌زمینه ایجاد شد؛ هنوز بارگیری نشده است", Toast.LENGTH_SHORT).show()
    }

    private fun activateTab(index: Int) {
        if (index !in tabs.indices) return
        currentWeb()?.onPause()
        activeIndex = index
        val tab = tabs[index]
        val wv = ensureWebView(tab)
        webHost.removeAllViews()
        (wv.parent as? ViewGroup)?.removeView(wv)
        webHost.addView(wv, FrameLayout.LayoutParams(-1, -1))
        wv.onResume()
        if (!tab.loaded && tab.url != "about:blank") {
            tab.loaded = true
            wv.loadUrl(tab.url)
        }
        address.setText(if (tab.url == "about:blank") "" else tab.url)
        updateTabCount()
    }

    private fun ensureWebView(tab: BrowserTab): WebView {
        tab.webView?.let { return it }
        val wv = MiliWebView(this)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                this@MainActivity.fileChooserCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                    startActivityForResult(intent, fileChooserRequestCode)
                    true
                } catch (_: Exception) {
                    this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                    this@MainActivity.fileChooserCallback = null
                    false
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    tab.title = title
                    updateTabCount()
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (tabs.getOrNull(activeIndex) === tab) {
                    pageProgress.progress = newProgress
                    pageProgress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                if (scheme == "http" || scheme == "https") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    false
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (adBlockEnabled && !isAllowedBySite(url) && isAdUrl(url)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url.isNullOrBlank()) return
                tab.url = url
                tab.title = view?.title?.takeIf { it.isNotBlank() } ?: url
                if (tabs.getOrNull(activeIndex) === tab) address.setText(url)
                if (url.startsWith("http")) addHistory(tab.title, url)
            }
        }
        wv.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadFile(url, userAgent, contentDisposition, mimeType)
        })

        // Link long-press actions. Text selection keeps the platform context menu and adds Translate.
        wv.setOnLongClickListener {
            val hit = wv.hitTestResult
            if (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                val link = hit.extra.orEmpty()
                if (link.isNotBlank()) showLinkMenu(wv, link)
                true
            } else false
        }
        tab.webView = wv
        return wv
    }


    // Intercept WebView's native text-selection ActionMode and add a real Translate action.
    private inner class MiliWebView(context: Context) : WebView(context) {
        private fun wrapSelectionCallback(original: ActionMode.Callback): ActionMode.Callback =
            object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    val accepted = original.onCreateActionMode(mode, menu)
                    if (accepted) menu.add(Menu.NONE, 7101, 90, "ترجمه")
                    return accepted
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
                    original.onPrepareActionMode(mode, menu)

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (item.itemId == 7101) {
                        evaluateJavascript("window.getSelection().toString()") { raw ->
                            val selected = try {
                                org.json.JSONTokener(raw).nextValue() as? String ?: ""
                            } catch (_: Exception) { "" }
                            if (selected.isNotBlank()) showTranslation(selected)
                            else Toast.makeText(this@MainActivity, "متن انتخاب‌شده پیدا نشد", Toast.LENGTH_SHORT).show()
                        }
                        mode.finish()
                        return true
                    }
                    return original.onActionItemClicked(mode, item)
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    original.onDestroyActionMode(mode)
                }
            }

        override fun startActionMode(callback: ActionMode.Callback): ActionMode? =
            super.startActionMode(wrapSelectionCallback(callback))

        override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
            super.startActionMode(wrapSelectionCallback(callback), type)
    }

    private fun isAllowedBySite(raw: String): Boolean {
        val host = Uri.parse(raw).host ?: return false
        val allowed = prefs.getStringSet("allow_sites", emptySet()) ?: emptySet()
        return allowed.any { host == it || host.endsWith(".$it") }
    }

    private fun isAdUrl(raw: String): Boolean {
        val host = try { Uri.parse(raw).host?.lowercase() ?: return false } catch (_: Exception) { return false }
        val domains = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "adnxs.com", "taboola.com", "outbrain.com",
            "popads.net", "popcash.net", "adsterra.com", "imasdk.googleapis.com",
            "scorecardresearch.com", "criteo.com", "quantserve.com", "zedo.com"
        )
        return domains.any { host == it || host.endsWith(".$it") } || host.startsWith("ads.") || host.startsWith("ad.")
    }

    private fun showLinkMenu(wv: WebView, link: String) {
        val items = arrayOf(
            "باز کردن در تب جدید (بدون بارگیری)",
            "باز کردن در همین تب",
            "کپی لینک",
            "ترجمه عنوان واقعی لینک",
            "ارسال با یکی از دستورهای تلگرام"
        )
        AlertDialog.Builder(this).setTitle("عملیات لینک").setItems(items) { _, which ->
            when (which) {
                0 -> openLinkInBackground(link)
                1 -> navigate(link)
                2 -> copy(link)
                3 -> {
                    val js = "(function(){const a=[...document.querySelectorAll('a')].find(x=>x.href===${JSONObject.quote(link)});return a?(a.innerText||a.title||a.getAttribute('aria-label')||''):'';})()"
                    wv.evaluateJavascript(js) { raw ->
                        val title = try { org.json.JSONTokener(raw).nextValue() as? String ?: "" } catch (_: Exception) { "" }
                        showTranslation(title.ifBlank { link })
                    }
                }
                4 -> chooseCommandForLink(link)
            }
        }.show()
    }

    private fun showTranslation(text: String) {
        val url = "https://translate.google.com/?sl=auto&tl=fa&text=${Uri.encode(text)}&op=translate"
        val dialog = AlertDialog.Builder(this).create()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundColor(bg) }
        layout.addView(label("ترجمه Google", 16f).apply { setPadding(dp(4), dp(4), dp(4), dp(8)) })
        val translateView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl(url)
        }
        layout.addView(translateView, LinearLayout.LayoutParams(-1, dp(440)))
        dialog.setView(layout)
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "بستن") { _, _ -> translateView.stopLoading(); translateView.destroy(); dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .94).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun copy(value: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Mili Chrome", value))
        Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
    }

    private fun chooseCommandForLink(link: String) {
        val enabled = commandStore.all().filter { it.enabled }
        if (enabled.isEmpty()) {
            Toast.makeText(this, "هیچ دستور فعالی وجود ندارد؛ از مدیریت دستورها اضافه کن", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("انتخاب دستور")
            .setItems(enabled.map { it.title }.toTypedArray()) { _, which ->
                val command = enabled[which]
                val message = command.render(link)
                openTelegramDraft(command.bot.ifBlank { botAddress }, message)
            }.setNegativeButton("انصراف", null).show()
    }

    // Telegram deep link opens the selected bot chat with a prepared draft. The user confirms Send in Telegram.
    // Telegram does not expose a safe public API for silently sending a personal-account message from another app.
    private fun openTelegramDraft(bot: String, message: String) {
        val username = bot.removePrefix("@").trim()
        if (username.isNotBlank()) {
            try {
                val uri = Uri.parse("tg://resolve?domain=${Uri.encode(username)}&text=${Uri.encode(message)}")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                Toast.makeText(this, "متن در گفت‌وگوی ربات آماده شد؛ برای ارسال، دکمه ارسال تلگرام را بزن", Toast.LENGTH_LONG).show()
                return
            } catch (_: Exception) { }
        }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(share, "ارسال متن با تلگرام"))
    }

    private fun showTabs() {
        val rows = tabs.mapIndexed { i, t ->
            val state = if (t.loaded || t.url == "about:blank") "آماده / بارگیری‌شده" else "پس‌زمینه؛ هنوز بارگیری نشده"
            "${if (i == activeIndex) "● " else ""}${i + 1}. ${t.title}\n$state"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("تب‌ها (${tabs.size})")
            .setItems(rows) { _, which -> activateTab(which) }
            .setPositiveButton("تب خالی جدید") { _, _ -> createBlankTab(true) }
            .setNeutralButton("بستن تب فعلی") { _, _ -> closeCurrentTab() }
            .setNegativeButton("بستن", null).show()
    }

    private fun closeCurrentTab() {
        if (tabs.size <= 1) {
            currentWeb()?.let { it.stopLoading(); it.destroy() }
            tabs[0] = BrowserTab("about:blank")
            activateTab(0)
            return
        }
        val removed = tabs.removeAt(activeIndex)
        removed.webView?.let { it.stopLoading(); it.destroy() }
        activeIndex = (activeIndex - 1).coerceAtLeast(0)
        activateTab(activeIndex)
    }

    private fun updateTabCount() {
        if (::tabLabel.isInitialized) tabLabel.text = tabs.size.toString()
    }

    private fun addHistory(title: String, url: String) {
        val list = readEntries("history")
        if (list.lastOrNull()?.url == url) return
        list.add(BrowserEntry(title, url, System.currentTimeMillis()))
        writeEntries("history", list.takeLast(500))
    }

    private fun readEntries(key: String): MutableList<BrowserEntry> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { i ->
                val o = array.getJSONObject(i)
                BrowserEntry(o.optString("title"), o.optString("url"), o.optLong("time"))
            }
        } catch (_: Exception) { mutableListOf() }
    }

    private fun writeEntries(key: String, entries: List<BrowserEntry>) {
        val array = JSONArray()
        entries.forEach { e -> array.put(JSONObject().put("title", e.title).put("url", e.url).put("time", e.time)) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun showBookmarks() {
        val entries = readEntries("bookmarks")
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setMessage("هنوز نشانکی ذخیره نشده است.")
                .setPositiveButton("افزودن صفحه فعلی") { _, _ -> bookmarkCurrent() }
                .setNegativeButton("بستن", null).show()
            return
        }
        val names = entries.map { it.title.ifBlank { it.url } }.toTypedArray()
        AlertDialog.Builder(this).setTitle("نشانک‌ها")
            .setItems(names) { _, which -> navigate(entries[which].url) }
            .setPositiveButton("افزودن صفحه فعلی") { _, _ -> bookmarkCurrent() }
            .setNeutralButton("مدیریت / حذف") { _, _ -> manageBookmarks(entries) }
            .setNegativeButton("بستن", null).show()
    }

    private fun bookmarkCurrent() {
        val tab = currentTab() ?: return
        if (!tab.url.startsWith("http")) return
        val entries = readEntries("bookmarks")
        if (entries.none { it.url == tab.url }) {
            entries.add(BrowserEntry(tab.title, tab.url, System.currentTimeMillis()))
            writeEntries("bookmarks", entries)
        }
        Toast.makeText(this, "به نشانک‌ها اضافه شد", Toast.LENGTH_SHORT).show()
    }

    private fun manageBookmarks(entries: List<BrowserEntry>) {
        if (entries.isEmpty()) return
        AlertDialog.Builder(this).setTitle("برای حذف، نشانک را انتخاب کن")
            .setItems(entries.map { it.title.ifBlank { it.url } }.toTypedArray()) { _, which ->
                val current = readEntries("bookmarks")
                current.removeAll { it.url == entries[which].url }
                writeEntries("bookmarks", current)
                showBookmarks()
            }.setNegativeButton("بستن", null).show()
    }

    private fun showHistory() {
        val entries = readEntries("history").asReversed()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setMessage("تاریخچه خالی است.").setPositiveButton("باشه", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("تاریخچه")
            .setItems(entries.map { it.title.ifBlank { it.url } }.toTypedArray()) { _, which -> navigate(entries[which].url) }
            .setNeutralButton("پاک کردن تاریخچه") { _, _ -> writeEntries("history", emptyList()); Toast.makeText(this, "تاریخچه پاک شد", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("بستن", null).show()
    }

    private fun downloadFile(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        try {
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent ?: "")
                CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Cookie", it) }
                setTitle(filename)
                setDescription("دانلود توسط Mili Chrome")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "دانلود شروع شد: $filename", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "شروع دانلود ناموفق بود: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDownloads() {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
        ))
        val rows = mutableListOf<String>()
        if (cursor != null) {
            cursor.use {
                while (it.moveToNext()) {
                    val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val state = when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> "کامل شد"
                        DownloadManager.STATUS_FAILED -> "ناموفق"
                        DownloadManager.STATUS_RUNNING -> "در حال دانلود"
                        DownloadManager.STATUS_PAUSED -> "متوقف"
                        else -> "در صف"
                    }
                    rows.add("$title — $state")
                }
            }
        }
        AlertDialog.Builder(this).setTitle("دانلودها")
            .setItems(rows.toTypedArray()) { _, _ -> }
            .setPositiveButton("باز کردن پوشه دانلودها") { _, _ ->
                try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (_: Exception) { }
            }.setNegativeButton("بستن", null).show()
    }

    private fun showFindInPage() {
        val input = EditText(this).apply { hint = "متن مورد جستجو"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("یافتن در صفحه").setView(input)
            .setPositiveButton("جستجو") { _, _ -> currentWeb()?.findAllAsync(input.text.toString()) }
            .setNegativeButton("انصراف", null).show()
    }

    private fun showMainMenu() {
        val items = arrayOf(
            "تب‌ها", "نشانک‌ها", "ذخیره صفحه فعلی در نشانک‌ها", "تاریخچه", "دانلودها", "یافتن در صفحه",
            "مدیریت دستورهای تلگرام", "ربات مقصد تلگرام", "مسدودکننده تبلیغات: ${if (adBlockEnabled) "روشن" else "خاموش"}",
            "استثنای مسدودکننده برای سایت فعلی", "تنظیم صفحه اصلی", "درباره Mili Chrome"
        )
        AlertDialog.Builder(this).setTitle("Mili Chrome").setItems(items) { _, which ->
            when (which) {
                0 -> showTabs()
                1 -> showBookmarks()
                2 -> bookmarkCurrent()
                3 -> showHistory()
                4 -> showDownloads()
                5 -> showFindInPage()
                6 -> manageCommands()
                7 -> editBotAddress()
                8 -> {
                    adBlockEnabled = !adBlockEnabled
                    prefs.edit().putBoolean("adblock", adBlockEnabled).apply()
                    Toast.makeText(this, "مسدودکننده ${if (adBlockEnabled) "روشن" else "خاموش"} شد", Toast.LENGTH_SHORT).show()
                }
                9 -> toggleCurrentSiteException()
                10 -> editHomePage()
                11 -> showAbout()
            }
        }.show()
    }

    private fun toggleCurrentSiteException() {
        val host = try { Uri.parse(currentTab()?.url ?: "").host ?: "" } catch (_: Exception) { "" }
        if (host.isBlank()) return
        val sites = (prefs.getStringSet("allow_sites", emptySet()) ?: emptySet()).toMutableSet()
        val added = if (sites.contains(host)) { sites.remove(host); false } else { sites.add(host); true }
        prefs.edit().putStringSet("allow_sites", sites).apply()
        Toast.makeText(this, if (added) "$host از مسدودسازی مستثنا شد" else "استثنای $host حذف شد", Toast.LENGTH_LONG).show()
    }

    private fun editBotAddress() {
        val input = EditText(this).apply { setSingleLine(true); setText(botAddress); hint = "@YourBot" }
        AlertDialog.Builder(this).setTitle("ربات مقصد تلگرام").setMessage("نام کاربری ربات را وارد کن.")
            .setView(input).setPositiveButton("ذخیره") { _, _ ->
                botAddress = input.text.toString()
                prefs.edit().putString("bot", botAddress).apply()
            }.setNegativeButton("انصراف", null).show()
    }

    private fun editHomePage() {
        val input = EditText(this).apply { setSingleLine(true); setText(homePage); hint = "https://www.google.com" }
        AlertDialog.Builder(this).setTitle("صفحه اصلی").setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                homePage = normalizeUrl(input.text.toString())
                prefs.edit().putString("home", homePage).apply()
            }.setNegativeButton("انصراف", null).show()
    }

    private fun manageCommands() {
        val commands = commandStore.all()
        val rows = commands.map { "${if (it.enabled) "●" else "○"} ${it.title}  (${it.prefix}{LINK}${it.suffix})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("دستورهای تلگرام")
            .setItems(rows) { _, which -> editCommand(commands[which]) }
            .setPositiveButton("＋ افزودن دستور") { _, _ -> editCommand(null) }
            .setNeutralButton("بازگردانی پیش‌فرض‌ها") { _, _ ->
                commandStore.save(listOf(
                    LinkCommand("ql", "ارسال با /ql", "/ql ", "", bot = botAddress),
                    LinkCommand("jl", "ارسال با /jl", "/jl ", "", bot = botAddress),
                    LinkCommand("l", "ارسال با /l", "/l ", "", bot = botAddress),
                    LinkCommand("yl", "ارسال با /yl", "/yl ", "", bot = botAddress)
                ))
                manageCommands()
            }.setNegativeButton("بستن", null).show()
    }

    private fun editCommand(existing: LinkCommand?) {
        val title = EditText(this).apply { hint = "نام دستور"; setText(existing?.title ?: "دستور جدید") }
        val prefix = EditText(this).apply { hint = "متن قبل از لینک (فاصله‌ها حفظ می‌شوند)"; setText(existing?.prefix ?: ""); minLines = 2; gravity = Gravity.TOP }
        val suffix = EditText(this).apply { hint = "متن بعد از لینک (فاصله‌ها حفظ می‌شوند)"; setText(existing?.suffix ?: ""); minLines = 2; gravity = Gravity.TOP }
        val template = EditText(this).apply { hint = "{PREFIX}{LINK}{SUFFIX}"; setText(existing?.template ?: "{PREFIX}{LINK}{SUFFIX}"); minLines = 2; gravity = Gravity.TOP }
        val bot = EditText(this).apply { hint = "@V2TGbot"; setText(existing?.bot ?: botAddress); setSingleLine(true) }
        val enabled = CheckBox(this).apply { text = "فعال باشد"; setTextColor(white); isChecked = existing?.enabled ?: true }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        fun addField(caption: String, field: EditText) {
            box.addView(label(caption, 12f, muted))
            field.setTextColor(white); field.setHintTextColor(muted); field.setPadding(dp(8), dp(8), dp(8), dp(8)); field.setBackgroundColor(panel)
            box.addView(field, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(3), 0, dp(9)) })
        }
        addField("نام نمایشی", title)
        addField("پیشوند دقیق", prefix)
        addField("پسوند دقیق", suffix)
        addField("الگوی خروجی؛ از {PREFIX}، {LINK} و {SUFFIX} استفاده کن", template)
        addField("ربات مقصد این دستور", bot)
        box.addView(enabled)
        val scroll = ScrollView(this).apply { addView(box) }
        val builder = AlertDialog.Builder(this).setTitle(if (existing == null) "افزودن دستور" else "ویرایش دستور")
            .setView(scroll)
            .setPositiveButton("ذخیره", null)
            .setNegativeButton("انصراف", null)
        if (existing != null) builder.setNeutralButton("حذف دستور", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (title.text.isEmpty()) {
                    title.error = "نام دستور را وارد کن"
                    return@setOnClickListener
                }
                val updated = LinkCommand(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title.text.toString(),
                    prefix = prefix.text.toString(),
                    suffix = suffix.text.toString(),
                    template = template.text.toString(),
                    bot = bot.text.toString(),
                    enabled = enabled.isChecked
                )
                val list = commandStore.all().toMutableList()
                if (existing == null) list.add(updated) else {
                    val index = list.indexOfFirst { it.id == existing.id }
                    if (index >= 0) list[index] = updated
                }
                commandStore.save(list)
                dialog.dismiss()
                manageCommands()
            }
            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    val list = commandStore.all().filterNot { it.id == existing.id }
                    commandStore.save(list)
                    dialog.dismiss()
                    manageCommands()
                }
            }
        }
        dialog.show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this).setTitle("درباره Mili Chrome")
            .setMessage("Mili Chrome یک مرورگر اندرویدی مستقل با رابط مشکی و قرمز و موتور WebView/Chromium نصب‌شده روی دستگاه است. این برنامه خودِ Google Chrome نیست و از موتور داخلی Chrome جداگانه استفاده نمی‌کند.")
            .setPositiveButton("باشه", null).show()
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequestCode) {
            val result = if (resultCode == RESULT_OK && data != null) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else null
            fileChooserCallback?.onReceiveValue(result)
            fileChooserCallback = null
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val wv = currentWeb()
        if (wv?.canGoBack() == true) wv.goBack()
        else if (tabs.size > 1) closeCurrentTab()
        else super.onBackPressed()
    }

    override fun onPause() { currentWeb()?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); currentWeb()?.onResume() }

    override fun onDestroy() {
        tabs.forEach { it.webView?.let { w -> w.stopLoading(); w.destroy() } }
        super.onDestroy()
    }
}
