package com.showmefox.fund

import com.showmefox.fund.data.Aggregator
import com.showmefox.fund.data.FundParsers
import com.showmefox.fund.model.Holding
import com.showmefox.fund.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用 docs/samples/ 里的真实响应做断言，确保解析逻辑跟线上格式一致。 */
class FundParsersTest {

    private fun sample(name: String): String {
        // Gradle 测试的工作目录是 app 模块，样本在项目根的 docs/samples 下
        val root = System.getProperty("project.root") ?: ".."
        val file = java.io.File(root, "docs/samples/$name")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun `parses batch quote from real response`() {
        // 该样本是 000001 + 110022 的批量响应，用来验证「一次请求多只」
        val quotes = FundParsers.parseBatch(sample("fundmnfinfo-000001.json"))
        assertEquals(2, quotes.size)
        val q = quotes.first { it.code == "000001" }
        assertEquals("华夏成长混合", q.name)
        assertEquals(0.9510, q.nav!!, 0.00001)
        assertEquals(0.74, q.changePct!!, 0.00001)
        assertEquals("2026-11-17", q.navDate)

        val second = quotes.first { it.code == "110022" }
        assertEquals("易方达消费行业股票", second.name)
        assertEquals(3.0270, second.nav!!, 0.00001)
    }

    @Test
    fun `parses lsjz and rejects error code`() {
        val q = FundParsers.parseLsjz(sample("lsjz-000001.json"), "000001")
        assertNotNull(q)
        assertEquals(0.9510, q!!.nav!!, 0.00001)
        assertEquals(0.74, q.changePct!!, 0.00001)

        // 缺 Referer 时服务端返回 HTTP 200 但 ErrCode=-999，必须判定为失败
        val errBody = """{"Data":"","ErrCode":-999,"ErrMsg":"","TotalCount":0,"PageSize":1,"PageIndex":1}"""
        assertNull(FundParsers.parseLsjz(errBody, "000001"))

        // 无效代码返回 ErrCode=0 但空列表，同样要判定为失败
        val emptyBody = """{"Data":{"LSJZList":[]},"ErrCode":0,"TotalCount":0}"""
        assertNull(FundParsers.parseLsjz(emptyBody, "999999"))
    }

    @Test
    fun `parses search and filters out stocks`() {
        val body = """
            {"ErrCode":0,"Datas":[
              {"CODE":"000001","NAME":"华夏成长混合","CATEGORYDESC":"基金"},
              {"CODE":"000001","NAME":"平安银行","CATEGORYDESC":"股票"},
              {"CODE":"007379","NAME":"易方达上证50ETF联接基金A","CATEGORYDESC":"基金"}
            ]}
        """.trimIndent()
        val items = FundParsers.parseSearch(body)
        assertEquals(2, items.size)
        assertTrue(items.none { it.name == "平安银行" })
    }

    @Test
    fun `parses tencent gbk fallback format`() {
        val text = """v_jj000001="000001~华夏成长混合~0.0000~0.0000~~0.9510~3.5240~0.7415~2026-11-17~";"""
        val quotes = FundParsers.parseTencent(text)
        assertEquals(1, quotes.size)
        val q = quotes.first()
        assertEquals("华夏成长混合", q.name)
        assertEquals(0.9510, q.nav!!, 0.00001)
        assertEquals(0.7415, q.changePct!!, 0.00001)
        assertEquals("2026-11-17", q.navDate)
    }

    @Test
    fun `money fund with dash chg does not crash`() {
        val body = """
            {"ErrCode":0,"Datas":[{"FCODE":"000198","SHORTNAME":"天弘余额宝货币",
            "NAV":"0.3546","NAVCHGRT":"--","PDATE":"2026-11-17","GSZ":null,"GSZZL":null}]}
        """.trimIndent()
        val q = FundParsers.parseBatch(body).first()
        assertEquals(0.3546, q.nav!!, 0.00001)
        assertNull(q.changePct)
        assertNull(q.estimateNav)
    }
}

/** 汇总计算：市值 / 当日盈亏 / 累计盈亏 / 平台小计。 */
class AggregatorTest {

    private val platform = Platform(id = "p1", name = "支付宝")

    private fun holding(shares: Double, cost: Double, code: String = "000001") = Holding(
        id = "h-$code-$shares",
        platformId = "p1",
        code = code,
        name = "测试基金",
        shares = shares,
        cost = cost,
    )

    @Test
    fun `market value and pnl math`() {
        val h = holding(shares = 1000.0, cost = 900.0)
        val quote = com.showmefox.fund.model.Quote(
            code = "000001", name = "测试基金", nav = 1.0, changePct = 2.0, navDate = "2026-11-17",
        )
        val views = Aggregator.views(listOf(platform), listOf(h), mapOf("000001" to quote))
        val v = views.first()

        assertEquals(1000.0, v.marketValue, 0.001)
        assertEquals(20.0, v.dayPnl, 0.001)      // 1000 * 2%
        assertEquals(100.0, v.totalPnl, 0.001)   // 1000 - 900
        assertEquals(11.111, v.totalPct!!, 0.01) // 100/900
    }

    @Test
    fun `falls back to last value when quote missing`() {
        val h = holding(shares = 1000.0, cost = 900.0).copy(lastValue = 980.0)
        val views = Aggregator.views(listOf(platform), listOf(h), emptyMap())
        val v = views.first()

        assertTrue(!v.hasQuote)
        assertEquals(980.0, v.marketValue, 0.001)
        assertEquals(0.0, v.dayPnl, 0.001)
        assertEquals(80.0, v.totalPnl, 0.001)
    }

    @Test
    fun `totals aggregate across platforms`() {
        val p2 = Platform(id = "p2", name = "天天基金")
        val h1 = holding(shares = 100.0, cost = 100.0)
        val h2 = Holding(
            id = "h2", platformId = "p2", code = "110022", name = "易方达消费",
            shares = 200.0, cost = 400.0,
        )
        val quotes = mapOf(
            "000001" to com.showmefox.fund.model.Quote("000001", "A", nav = 1.0, changePct = 1.0),
            "110022" to com.showmefox.fund.model.Quote("110022", "B", nav = 2.5, changePct = -2.0),
        )
        val views = Aggregator.views(listOf(platform, p2), listOf(h1, h2), quotes)
        val totals = Aggregator.totals(views)

        assertEquals(600.0, totals.totalValue, 0.001) // 100 + 500
        assertEquals(500.0, totals.totalCost, 0.001)
        assertEquals(100.0, totals.totalPnl, 0.001)
        // 当日：100*1% = 1.0 ; 500*-2% = -10.0 → -9.0
        assertEquals(-9.0, totals.dayPnl, 0.001)

        val platformTotals = Aggregator.platformTotals(listOf(platform, p2), views)
        assertEquals(2, platformTotals.size)
        assertEquals("p2", platformTotals.first().platform.id) // 按金额倒序
    }

    @Test
    fun `grouping drops platforms without holdings`() {
        val empty = Platform(id = "p3", name = "空平台")
        val h = holding(shares = 10.0, cost = 10.0)
        val views = Aggregator.views(
            listOf(platform, empty), listOf(h),
            mapOf("000001" to com.showmefox.fund.model.Quote("000001", "A", nav = 1.0)),
        )
        val grouped = Aggregator.groupByPlatform(listOf(platform, empty), views)
        assertEquals(1, grouped.size)
        assertEquals("p1", grouped.first().first.id)
    }
}
