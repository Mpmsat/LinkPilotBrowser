package com.linkpilot.browser

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.text.InputType
import android.net.Uri

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var store: CommandStore
    private val tabs = mutableListOf<String>()
    private var activeTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CommandStore(this)
        buildUi()
        openUrl("https://www.google.com")
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 8)
        }
        address = EditText(this).apply {
            hint = "آدرس سایت یا جستجو"
            singleLine = true
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText("https://www.google.com")
        }
        val go = Button(this).apply {
            text = "برو"
            setOnClickListener { openUrl(address.text.toString()) }
        }
        val tabButton = Button(this).apply {
            text = "تب‌ها"
            setOnClickListener { showTabs() }
        }
        val settings = Button(this).apply {
            text = "تنظیمات"
            setOnClickListener { showCommandSettings() }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(go); bar.addView(tabButton); bar.addView(settings)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url != null) address.setText(url)
                }
            }
            setOnLongClickListener {
                val hit = hitTestResult
                if (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    showLinkMenu(hit.extra ?: "")
                    true
                } else false
            }
        }
        page.addView(bar)
        page.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun openUrl(raw: String) {
        var u = raw.trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = if (u.contains(".") && !u.contains(" ")) "https://$u"
                else "https://www.google.com/search?q=" + Uri.encode(u)
        }
        if (tabs.isEmpty()) tabs.add(u) else tabs[activeTab] = u
        web.loadUrl(u)
    }

    private fun showLinkMenu(link: String) {
        if (link.isBlank()) return
        val commands = store.all().filter { it.enabled }
        val labels = mutableListOf("کپی لینک", "باز کردن لینک در تب جدید", "ترجمه عنوان لینک")
        labels.addAll(commands.map { it.title })
        AlertDialog.Builder(this)
            .setTitle("عملیات روی لینک")
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> copy(link)
                    1 -> newTab(link)
                    2 -> showTranslatePopup(link)
                    else -> {
                        val cmd = commands[which - 3]
                        // Current prototype prepares exact text and copies it.
                        // Direct MTProto send is intentionally not faked; it requires TDLib auth integration.
                        copy(cmd.render(link))
                        Toast.makeText(this, "متن دستور آماده شد؛ ارسال مستقیم تلگرام در مرحله اتصال MTProto اضافه می‌شود.", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    private fun copy(value: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LinkPilot", value))
        Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
    }

    private fun newTab(url: String) {
        tabs.add(url)
        activeTab = tabs.lastIndex
        web.loadUrl(url)
    }

    private fun showTabs() {
        if (tabs.isEmpty()) return
        val names = tabs.mapIndexed { i, u -> "${i + 1}. $u" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("تب‌ها")
            .setItems(names) { _, which ->
                activeTab = which
                web.loadUrl(tabs[which])
            }.setPositiveButton("تب جدید") { _, _ -> newTab("https://www.google.com") }
            .setNegativeButton("بستن", null).show()
    }

    private fun showTranslatePopup(text: String) {
        val dialog = AlertDialog.Builder(this).create()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }
        layout.addView(TextView(this).apply {
            this.text = "ترجمه عنوان لینک"
            textSize = 18f
        })
        val translated = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl("https://translate.google.com/?sl=auto&tl=fa&text=${Uri.encode(text)}&op=translate")
        }
        layout.addView(translated, LinearLayout.LayoutParams(-1, 560))
        dialog.setView(layout)
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "بستن") { _, _ -> dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.94).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showCommandSettings() {
        val current = store.all().toMutableList()
        val options = current.map { "${it.title}  (${it.bot})" }.toMutableList()
        options.add("افزودن دستور جدید")
        AlertDialog.Builder(this).setTitle("مدیریت دستورات")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == current.size) editCommand(null)
                else editCommand(current[which])
            }.setNegativeButton("بستن", null).show()
    }

    private fun editCommand(existing: LinkCommand?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
        }
        fun field(label: String, value: String, hint: String = ""): EditText {
            box.addView(TextView(this).apply { text = label })
            return EditText(this).apply {
                setText(value) // no trimming: preserve exact spaces and line breaks
                this.hint = hint
                minLines = 1
                if (label.contains("متن")) minLines = 2
                box.addView(this)
            }
        }
        val title = field("نام گزینه در منو", existing?.title ?: "دستور جدید")
        val prefix = field("متن ابتدایی (عیناً حفظ می‌شود)", existing?.prefix ?: "")
        val suffix = field("متن انتهایی (عیناً حفظ می‌شود)", existing?.suffix ?: "")
        val template = field("الگوی خروجی (از {PREFIX}، {LINK}، {SUFFIX} استفاده کن)", existing?.template ?: "{PREFIX}{LINK}{SUFFIX}")
        val bot = field("ربات مقصد", existing?.bot ?: "@V2TGbot")
        AlertDialog.Builder(this).setTitle(if (existing == null) "دستور جدید" else "ویرایش دستور")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("ذخیره") { _, _ ->
                val all = store.all().toMutableList()
                val item = LinkCommand(
                    id = existing?.id ?: System.currentTimeMillis().toString(),
                    title = title.text.toString(),
                    prefix = prefix.text.toString(),
                    suffix = suffix.text.toString(),
                    template = template.text.toString(),
                    bot = bot.text.toString(),
                    enabled = existing?.enabled ?: true
                )
                if (existing == null) all.add(item)
                else {
                    val index = all.indexOfFirst { it.id == existing.id }
                    if (index >= 0) all[index] = item
                }
                store.save(all)
            }.setNegativeButton("انصراف", null).show()
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
