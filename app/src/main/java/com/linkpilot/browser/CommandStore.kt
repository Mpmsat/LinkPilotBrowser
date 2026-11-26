package com.linkpilot.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LinkCommand(
    val id: String,
    val title: String,
    val prefix: String,
    val suffix: String,
    val template: String = "{PREFIX}{LINK}{SUFFIX}",
    val bot: String = "@V2TGbot",
    val enabled: Boolean = true
) {
    // Deliberately do not trim, normalize whitespace, or alter user-entered text.
    fun render(link: String): String = template
        .replace("{PREFIX}", prefix)
        .replace("{LINK}", link)
        .replace("{SUFFIX}", suffix)
}

class CommandStore(context: Context) {
    private val prefs = context.getSharedPreferences("commands", Context.MODE_PRIVATE)

    fun all(): List<LinkCommand> {
        val raw = prefs.getString("items", null) ?: return defaults()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                LinkCommand(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    prefix = o.optString("prefix"),
                    suffix = o.optString("suffix"),
                    template = o.optString("template", "{PREFIX}{LINK}{SUFFIX}"),
                    bot = o.optString("bot", "@V2TGbot"),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) { defaults() }
    }

    fun save(items: List<LinkCommand>) {
        val a = JSONArray()
        items.forEach { c ->
            a.put(JSONObject().apply {
                put("id", c.id); put("title", c.title)
                put("prefix", c.prefix); put("suffix", c.suffix)
                put("template", c.template); put("bot", c.bot)
                put("enabled", c.enabled)
            })
        }
        prefs.edit().putString("items", a.toString()).apply()
    }

    private fun defaults() = listOf(
        LinkCommand("ql", "ارسال با /ql", "/ql ", "", bot = "@V2TGbot"),
        LinkCommand("jl", "ارسال با /jl", "/jl ", "", bot = "@V2TGbot"),
        LinkCommand("l", "ارسال با /l", "/l ", "", bot = "@V2TGbot"),
        LinkCommand("yl", "ارسال با /yl", "/yl ", "", bot = "@V2TGbot")
    )
}
