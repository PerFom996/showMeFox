package com.showmefox.fund.data

import com.showmefox.fund.model.Quote
import com.showmefox.fund.model.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 东方财富接口封装，带「主用 → 降级」三级策略（见 docs/fund-api.md 第 5 节）。
 *
 * 1. FundMNFInfo 批量（一次拿多只，列表页主力）
 * 2. f10/lsjz 单只精确（必须带 Referer，且要校验 ErrCode）
 * 3. 腾讯 qt.gtimg.cn 兜底（GBK 编码）
 */
class FundApi(
    private val client: OkHttpClient = defaultClient(),
) {

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun get(url: String, referer: String? = null): String? {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (referer != null) builder.header("Referer", referer)
        return runCatching {
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /** 批量拉取行情；内部会按 20 只一批切分，失败自动降级到单只接口。 */
    suspend fun fetchQuotes(codes: List<String>): Map<String, Quote> =
        withContext(Dispatchers.IO) {
            val clean = codes.map { it.trim() }.filter { it.matches(Regex("""\d{6}""")) }.distinct()
            if (clean.isEmpty()) return@withContext emptyMap()

            val result = LinkedHashMap<String, Quote>()

            // 第 1 层：批量接口
            clean.chunked(20).forEach { chunk ->
                val url = "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNFInfo" +
                    "?Fcodes=${chunk.joinToString(",")}&pageIndex=1&pageSize=${chunk.size}" +
                    "&plat=Android&appType=ttjj&product=EFund&Version=6.5.5&deviceid=abc123"
                get(url)?.let { body ->
                    FundParsers.parseBatch(body).filter { it.nav != null }
                        .forEach { result[it.code] = it }
                }
            }

            // 第 2 层：单只精确
            val missing = clean.filter { result[it]?.nav == null }
            for (code in missing) {
                val url = "https://api.fund.eastmoney.com/f10/lsjz" +
                    "?fundCode=$code&pageIndex=1&pageSize=1"
                val body = get(url, referer = "https://fundf10.eastmoney.com/") ?: continue
                FundParsers.parseLsjz(body, code)?.let { quote ->
                    val name = result[code]?.name
                    result[code] = if (name != null && name != code) quote.copy(name = name) else quote
                }
            }

            // 第 3 层：腾讯兜底
            val stillMissing = clean.filter { result[it]?.nav == null }
            if (stillMissing.isNotEmpty()) {
                stillMissing.chunked(20).forEach { chunk ->
                    val url = "https://qt.gtimg.cn/q=" + chunk.joinToString(",") { "jj$it" }
                    runCatching {
                        val req = Request.Builder().url(url).header("User-Agent", UA).build()
                        client.newCall(req).execute().use { resp ->
                            val bytes = resp.body?.bytes() ?: return@use
                            // 腾讯是 GBK，必须显式解码
                            val text = String(bytes, charset("GBK"))
                            FundParsers.parseTencent(text).forEach { q ->
                                if (result[q.code]?.nav == null) result[q.code] = q
                            }
                        }
                    }
                }
            }

            result
        }

    /** 搜索基金。key 必须是 UTF-8 百分号编码，否则返回空数组。 */
    suspend fun search(keyword: String): List<SearchItem> = withContext(Dispatchers.IO) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(kw, "UTF-8")
        val url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=$encoded"
        get(url)?.let { FundParsers.parseSearch(it) } ?: emptyList()
    }
}
