package com.showmefox.fund.data

import com.showmefox.fund.model.Quote
import com.showmefox.fund.model.SearchItem
import org.json.JSONObject

/**
 * 纯解析函数：输入接口原文，输出模型。不碰网络，便于用真实样本做单元测试。
 *
 * 依据 `docs/fund-api.md` 的实测结论：
 *  - `api.fund.eastmoney.com/f10/lsjz` 不带 Referer 会返回 HTTP 200 + `ErrCode:-999`
 *  - `FundMNFInfo` 的 `GSZ/GSZZL` 目前恒为 null（估算服务已下线），需按可空处理
 *  - 货币基金（000198 等）`NAVCHGRT` 为 `"--"`，不能当数字解析
 */
object FundParsers {

    private fun JSONObject.strOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "").trim()
        return if (v.isEmpty() || v == "--" || v == "null") null else v
    }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        strOrNull(key)?.toDoubleOrNull()

    /** 批量行情：`fundmobapi.eastmoney.com/FundMNewApi/FundMNFInfo` */
    fun parseBatch(body: String): List<Quote> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        if (root.optInt("ErrCode", -1) != 0) return emptyList()
        val arr = root.optJSONArray("Datas") ?: return emptyList()
        val out = ArrayList<Quote>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.strOrNull("FCODE") ?: continue
            out += Quote(
                code = code,
                name = o.strOrNull("SHORTNAME") ?: code,
                nav = o.doubleOrNull("NAV"),
                navDate = o.strOrNull("PDATE"),
                changePct = o.doubleOrNull("NAVCHGRT"),
                estimateNav = o.doubleOrNull("GSZ"),
                estimatePct = o.doubleOrNull("GSZZL"),
                source = "eastmoney-batch",
            )
        }
        return out
    }

    /** 单只净值：`api.fund.eastmoney.com/f10/lsjz`（必须带 Referer，且要校验 ErrCode） */
    fun parseLsjz(body: String, code: String): Quote? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (root.optInt("ErrCode", -1) != 0) return null
        val data = root.optJSONObject("Data") ?: return null
        val list = data.optJSONArray("LSJZList") ?: return null
        if (list.length() == 0) return null
        val item = list.optJSONObject(0) ?: return null
        val nav = item.doubleOrNull("DWJZ") ?: return null
        return Quote(
            code = code,
            name = code,
            nav = nav,
            navDate = item.strOrNull("FSRQ"),
            changePct = item.doubleOrNull("JZZZL"),
            source = "eastmoney-lsjz",
        )
    }

    /**
     * 腾讯兜底（GBK）：`qt.gtimg.cn/q=jj000001,jj110022`
     * 格式：`v_jj000001="000001~华夏成长混合~...~0.9510~3.5240~0.7415~2026-11-17~";`
     * 索引：1 名称 / 5 单位净值 / 6 累计净值 / 7 涨跌幅% / 8 日期
     */
    fun parseTencent(body: String): List<Quote> {
        val out = ArrayList<Quote>()
        val regex = Regex("""v_jj(\d{6})="([^"]*)"""")
        for (m in regex.findAll(body)) {
            val fields = m.groupValues[2].split("~")
            if (fields.size < 8) continue
            val nav = fields[5].trim().toDoubleOrNull() ?: continue
            out += Quote(
                code = m.groupValues[1],
                name = fields[1].trim().ifEmpty { m.groupValues[1] },
                nav = nav,
                navDate = fields[8].trim().ifEmpty { null },
                changePct = fields[7].trim().toDoubleOrNull(),
                source = "tencent",
            )
        }
        return out
    }

    /**
     * 搜索：`fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx`
     * 6 位数字会同时命中股票/指数，必须按 CATEGORYDESC == "基金" 过滤。
     */
    fun parseSearch(body: String, limit: Int = 20): List<SearchItem> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        if (root.optInt("ErrCode", -1) != 0) return emptyList()
        val arr = root.optJSONArray("Datas") ?: return emptyList()
        val out = ArrayList<SearchItem>()
        for (i in 0 until arr.length()) {
            if (out.size >= limit) break
            val o = arr.optJSONObject(i) ?: continue
            val code = o.strOrNull("CODE") ?: continue
            val name = o.strOrNull("NAME") ?: continue
            val cat = o.strOrNull("CATEGORYDESC")
            val isFund = cat == null || cat == "基金" ||
                o.optJSONObject("FundBaseInfo") != null
            if (!isFund) continue
            if (!code.matches(Regex("""\d{6}"""))) continue
            out += SearchItem(code = code, name = name, typeDesc = cat)
        }
        return out
    }

    /** pingzhongdata：只取名称与最后一条净值，用于兜底和详情。 */
    fun parsePingzhongName(js: String): String? =
        Regex("""var fS_name = "(.*?)";""").find(js)?.groupValues?.get(1)
}
