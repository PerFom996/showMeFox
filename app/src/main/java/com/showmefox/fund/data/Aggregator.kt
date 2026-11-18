package com.showmefox.fund.data

import com.showmefox.fund.model.Holding
import com.showmefox.fund.model.HoldingView
import com.showmefox.fund.model.Platform
import com.showmefox.fund.model.PlatformTotal
import com.showmefox.fund.model.Quote
import com.showmefox.fund.model.Totals

/**
 * 汇总计算，纯函数，不依赖 Android，便于单测。
 * 所有金额都从 [HoldingView] 现算，避免持久化缓存与净值不一致。
 */
object Aggregator {

    fun views(
        platforms: List<Platform>,
        holdings: List<Holding>,
        quotes: Map<String, Quote>,
    ): List<HoldingView> {
        val nameById = platforms.associate { it.id to it.name }
        return holdings.map { h ->
            HoldingView(
                holding = h,
                platformName = nameById[h.platformId] ?: "未归组",
                quote = quotes[h.code],
            )
        }
    }

    fun totals(views: List<HoldingView>): Totals {
        var value = 0.0
        var cost = 0.0
        var dayPnl = 0.0
        var stale = 0
        views.forEach { v ->
            value += v.marketValue
            cost += v.holding.cost
            dayPnl += v.dayPnl
            if (!v.hasQuote) stale++
        }
        return Totals(
            totalValue = value,
            totalCost = cost,
            dayPnl = dayPnl,
            staleCount = stale,
        )
    }

    fun platformTotals(
        platforms: List<Platform>,
        views: List<HoldingView>,
    ): List<PlatformTotal> {
        val byPlatform = views.groupBy { it.holding.platformId }
        return platforms.mapNotNull { p ->
            val list = byPlatform[p.id] ?: return@mapNotNull null
            if (list.isEmpty()) return@mapNotNull null
            PlatformTotal(
                platform = p,
                value = list.sumOf { it.marketValue },
                cost = list.sumOf { it.holding.cost },
                dayPnl = list.sumOf { it.dayPnl },
                count = list.size,
            )
        }.sortedByDescending { it.value }
    }

    /** 按「平台」分组后的持仓视图，保持平台顺序稳定（平台总和倒序）。 */
    fun groupByPlatform(
        platforms: List<Platform>,
        views: List<HoldingView>,
    ): List<Pair<Platform, List<HoldingView>>> {
        val order = platformTotals(platforms, views).map { it.platform.id }
        val byId = platforms.associateBy { it.id }
        val grouped = views.groupBy { it.holding.platformId }
        val result = ArrayList<Pair<Platform, List<HoldingView>>>()
        order.forEach { pid ->
            val p = byId[pid] ?: return@forEach
            val list = grouped[pid] ?: return@forEach
            result += p to list.sortedByDescending { it.marketValue }
        }
        // 有持仓但平台被删掉的兜底（理论上不会发生，store 已过滤）
        return result
    }
}
