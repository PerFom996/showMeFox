package com.showmefox.fund

import com.showmefox.fund.data.FundApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实网络集成测试：直连东方财富接口，验证 App 的数据链路端到端可用。
 *
 * 默认跳过（离线环境会失败），需要时用 -DnetTest=true 显式开启：
 *   gradle testDebugUnitTest -DnetTest=true
 */
class FundApiNetworkTest {

    private val enabled: Boolean = System.getProperty("netTest") == "true"

    @Test
    fun `fetches real quotes for known fund codes`() = runBlocking {
        if (!enabled) return@runBlocking
        val api = FundApi()
        val quotes = api.fetchQuotes(listOf("000001", "110022"))

        assertTrue("应至少返回一只基金的行情", quotes.isNotEmpty())
        val q = quotes["000001"]
        assertNotNull("000001 必须有净值", q?.nav)
        assertNotNull("000001 必须有净值日期", q?.navDate)
        assertTrue("净值应大于 0", (q?.nav ?: 0.0) > 0.0)
        println("=== 实测行情 ===")
        quotes.values.forEach {
            println("${it.code} ${it.name} nav=${it.nav} chg=${it.changePct}% date=${it.navDate} src=${it.source}")
        }
    }

    @Test
    fun `searches fund by chinese keyword`() = runBlocking {
        if (!enabled) return@runBlocking
        val api = FundApi()
        val items = api.search("易方达")
        assertTrue("搜索应有结果", items.isNotEmpty())
        assertTrue("结果应为 6 位基金代码", items.all { it.code.matches(Regex("""\d{6}""")) })
        println("=== 搜索结果（前 5）===")
        items.take(5).forEach { println("${it.code} ${it.name}") }
    }

    @Test
    fun `falls back gracefully for invalid code`() = runBlocking {
        if (!enabled) return@runBlocking
        val api = FundApi()
        val quotes = api.fetchQuotes(listOf("999999"))
        // 无效代码不应抛异常，返回空即可
        assertTrue("无效代码应返回空而不是崩溃", quotes["999999"]?.nav == null)
    }
}
