package com.showmefox.fund.data

import android.content.Context
import com.showmefox.fund.model.Holding
import com.showmefox.fund.model.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 轻量持久化：用 JSON 文件存在 App 私有目录。
 *
 * 选它而不是 Room 的原因：数据量级是「几十个平台 × 几十只基金」，全量读写在主线程外
 * 一次完成即可；不引注解处理器能让首次构建更稳。写入采用「临时文件 → rename」的
 * 原子替换，避免进程被杀时留下半个 JSON 把用户数据读废。
 */
class FundStore(context: Context) {

    private val file = File(context.filesDir, "fund_data.json")
    private val lock = Any()
    private var cache: Snapshot? = null

    data class Snapshot(
        val platforms: List<Platform> = emptyList(),
        val holdings: List<Holding> = emptyList(),
        val quoteCache: Map<String, CachedQuote> = emptyMap(),
    )

    data class CachedQuote(
        val code: String,
        val nav: Double?,
        val navDate: String?,
        val changePct: Double?,
        val fetchedAt: Long,
    )

    fun load(): Snapshot = synchronized(lock) {
        cache?.let { return it }
        val snap = if (!file.exists()) Snapshot()
        else runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrElse { Snapshot() }
        cache = snap
        snap
    }

    fun save(snapshot: Snapshot) = synchronized(lock) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.writeText(serialize(snapshot), Charsets.UTF_8)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
            cache = snapshot
        }
    }

    // ---------- 序列化 ----------

    private fun serialize(s: Snapshot): String {
        val root = JSONObject()
        root.put("version", 1)
        val platforms = JSONArray()
        s.platforms.forEach { p ->
            platforms.put(JSONObject().put("id", p.id).put("name", p.name))
        }
        root.put("platforms", platforms)

        val holdings = JSONArray()
        s.holdings.forEach { h ->
            holdings.put(
                JSONObject()
                    .put("id", h.id)
                    .put("platformId", h.platformId)
                    .put("code", h.code)
                    .put("name", h.name)
                    .put("shares", h.shares)
                    .put("cost", h.cost)
                    .put("lastValue", h.lastValue)
                    .put("lastNavDate", h.lastNavDate ?: JSONObject.NULL)
                    .put("createdAt", h.createdAt)
            )
        }
        root.put("holdings", holdings)

        val quotes = JSONObject()
        s.quoteCache.forEach { (code, q) ->
            quotes.put(
                code, JSONObject()
                    .put("nav", q.nav ?: JSONObject.NULL)
                    .put("navDate", q.navDate ?: JSONObject.NULL)
                    .put("changePct", q.changePct ?: JSONObject.NULL)
                    .put("fetchedAt", q.fetchedAt)
            )
        }
        root.put("quotes", quotes)
        return root.toString()
    }

    private fun parse(text: String): Snapshot {
        val root = JSONObject(text)
        val platforms = ArrayList<Platform>()
        root.optJSONArray("platforms")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty()) continue
                platforms += Platform(id = id, name = o.optString("name", "未命名平台"))
            }
        }
        val holdings = ArrayList<Holding>()
        root.optJSONArray("holdings")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty()) continue
                val pid = o.optString("platformId")
                if (platforms.none { it.id == pid }) continue
                holdings += Holding(
                    id = id,
                    platformId = pid,
                    code = o.optString("code"),
                    name = o.optString("name", o.optString("code")),
                    shares = o.optDouble("shares", 0.0),
                    cost = o.optDouble("cost", 0.0),
                    lastValue = o.optDouble("lastValue", 0.0),
                    lastNavDate = o.optString("lastNavDate").takeIf {
                        it.isNotEmpty() && it != "null"
                    },
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        }
        val quotes = LinkedHashMap<String, CachedQuote>()
        root.optJSONObject("quotes")?.let { obj ->
            obj.keys().forEach { code ->
                val o = obj.optJSONObject(code) ?: return@forEach
                quotes[code] = CachedQuote(
                    code = code,
                    nav = o.optDoubleOrNull("nav"),
                    navDate = o.optString("navDate").takeIf { it.isNotEmpty() && it != "null" },
                    changePct = o.optDoubleOrNull("changePct"),
                    fetchedAt = o.optLong("fetchedAt", 0L),
                )
            }
        }
        return Snapshot(platforms, holdings, quotes)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val d = optDouble(key, Double.NaN)
        return if (d.isNaN()) null else d
    }
}
