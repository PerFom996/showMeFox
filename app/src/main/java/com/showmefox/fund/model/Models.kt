package com.showmefox.fund.model

import java.util.UUID

/** 一个基金销售渠道/平台，例如「支付宝」「天天基金」「招商银行」。 */
data class Platform(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
)

/**
 * 一笔持仓。
 *
 * 只保存两个真正的「事实」：份额 [shares] 与累计买入成本 [cost]。
 * 市值、当日盈亏、累计盈亏都由 [Quote] 现算，避免净值变化后数据不一致。
 *
 * [lastValue] / [lastNavDate] 是离线兜底：最后一次成功拿到的市值与净值日期，
 * 断网时用它展示，而不是把持仓显示成 0。
 */
data class Holding(
    val id: String = UUID.randomUUID().toString(),
    val platformId: String,
    val code: String,
    val name: String,
    val shares: Double,
    val cost: Double,
    val lastValue: Double = cost,
    val lastNavDate: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 某只基金的最新行情。字段全部可空：接口可能拿不到估算、也可能是货币基金。 */
data class Quote(
    val code: String,
    val name: String,
    val nav: Double? = null,
    val navDate: String? = null,
    val changePct: Double? = null,
    val estimateNav: Double? = null,
    val estimatePct: Double? = null,
    val source: String = "eastmoney",
    val fetchedAt: Long = System.currentTimeMillis(),
)

/** 搜索候选项。 */
data class SearchItem(
    val code: String,
    val name: String,
    val typeDesc: String? = null,
)

/** 持仓 + 所属平台 + 当前行情，供 UI 直接渲染。 */
data class HoldingView(
    val holding: Holding,
    val platformName: String,
    val quote: Quote?,
) {
    val hasQuote: Boolean get() = quote?.nav != null

    /** 市值：有净值就现算，否则用最后一次成功的市值兜底。 */
    val marketValue: Double
        get() = quote?.nav?.let { it * holding.shares } ?: holding.lastValue

    /** 当日盈亏；拿不到涨跌幅（货币基金 / 停牌）时为 0。 */
    val dayPnl: Double
        get() = quote?.changePct?.let { marketValue * it / 100.0 } ?: 0.0

    val dayPct: Double? get() = quote?.changePct

    val totalPnl: Double get() = marketValue - holding.cost

    val totalPct: Double?
        get() = if (holding.cost > 0) totalPnl / holding.cost * 100.0 else null

    val navDate: String? get() = quote?.navDate ?: holding.lastNavDate
}

/** 顶部面板的汇总数据。 */
data class Totals(
    val totalValue: Double = 0.0,
    val totalCost: Double = 0.0,
    val dayPnl: Double = 0.0,
    val staleCount: Int = 0,
) {
    val totalPnl: Double get() = totalValue - totalCost
    val totalPct: Double? get() = if (totalCost > 0) totalPnl / totalCost * 100.0 else null
    val dayPct: Double?
        get() {
            val prev = totalValue - dayPnl
            return if (prev > 0) dayPnl / prev * 100.0 else null
        }
}

/** 单个平台的汇总。 */
data class PlatformTotal(
    val platform: Platform,
    val value: Double,
    val cost: Double,
    val dayPnl: Double,
    val count: Int,
) {
    val pnl: Double get() = value - cost
    val pct: Double? get() = if (cost > 0) pnl / cost * 100.0 else null
}
