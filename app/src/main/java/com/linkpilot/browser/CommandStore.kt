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
    // Never trim or normalize user text: whitespace and line breaks are significant.
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
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
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
        val array = JSONArray()
        items.forEach { command ->
            array.put(JSONObject().apply {
                put("id", command.id)
                put("title", command.title)
                put("prefix", command.prefix)
                put("suffix", command.suffix)
                put("template", command.template)
                put("bot", command.bot)
                put("enabled", command.enabled)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun defaults() = listOf(
        LinkCommand("ql", "ارسال با /ql", "/ql ", "", bot = "@V2TGbot"),
        LinkCommand("jl", "ارسال با /jl", "/jl ", "", bot = "@V2TGbot"),
        LinkCommand("l", "ارسال با /l", "/l ", "", bot = "@V2TGbot"),
        LinkCommand("yl", "ارسال با /yl", "/yl ", "", bot = "@V2TGbot")
    )
}
