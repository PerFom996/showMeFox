package com.showmefox.fund.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.showmefox.fund.R
import com.showmefox.fund.model.HoldingView
import com.showmefox.fund.model.Platform
import com.showmefox.fund.model.PlatformTotal
import com.showmefox.fund.util.Fmt

/**
 * 用原生 View 手动拼装「平台卡片 + 持仓行」，不依赖 RecyclerView 嵌套。
 *
 * 原因是数据量很小（几十条），一次性 inflate 到 NestedScrollView 里最简单、最不容易出错；
 * 用 findViewById 绑定，避免 viewBinding 在 include 上的坑。
 */
class PlatformRenderer(
    private val container: LinearLayout,
    private val onHoldingClick: (HoldingView) -> Unit,
    private val onPlatformClick: (Platform) -> Unit,
) {

    fun render(grouped: List<Pair<Platform, List<HoldingView>>>, totals: Map<String, PlatformTotal>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        grouped.forEach { (platform, holdings) ->
            val card = inflater.inflate(R.layout.item_platform_card, container, false)
            val total = totals[platform.id]

            card.findViewById<TextView>(R.id.tvPlatformName).text = platform.name
            card.findViewById<TextView>(R.id.tvPlatformSub).text = buildString {
                append("${holdings.size} 只基金")
                total?.pct?.let { append(" · 累计 ${Fmt.pct(it)}") }
            }
            card.findViewById<TextView>(R.id.tvPlatformValue).text = "¥" + Fmt.money(total?.value ?: 0.0)
            // 这一行只展示「当日盈亏」，颜色必须与当日盈亏一致；
            // 累计收益率放在上方副标题里，避免同一行两种口径混色。
            val dayView = card.findViewById<TextView>(R.id.tvPlatformDay)
            dayView.text = "今日 " + Fmt.signedMoney(total?.dayPnl ?: 0.0)
            dayView.setTextColor(color(dayView, total?.dayPnl))

            card.findViewById<View>(R.id.headerRow).setOnClickListener { onPlatformClick(platform) }

            val rowContainer = card.findViewById<LinearLayout>(R.id.holdingContainer)
            holdings.forEach { view ->
                val row = inflater.inflate(R.layout.item_holding_row, rowContainer, false)
                bindRow(row, view)
                row.setOnClickListener { onHoldingClick(view) }
                rowContainer.addView(row)
            }
            container.addView(card)
        }
    }

    private fun bindRow(row: View, view: HoldingView) {
        val h = view.holding
        row.findViewById<TextView>(R.id.tvName).text = h.name
        row.findViewById<TextView>(R.id.tvCode).text = h.code
        row.findViewById<TextView>(R.id.tvNav).text = "净值 " + Fmt.nav(view.quote?.nav)
        row.findViewById<TextView>(R.id.tvNavDate).text = view.navDate ?: ""
        row.findViewById<TextView>(R.id.tvValue).text = "¥" + Fmt.money(view.marketValue)

        val pct = row.findViewById<TextView>(R.id.tvPct)
        pct.text = Fmt.pct(view.dayPct)
        pct.setTextColor(color(pct, view.dayPct))

        val day = row.findViewById<TextView>(R.id.tvDayPnl)
        day.text = Fmt.signedMoney(view.dayPnl)
        day.setTextColor(color(day, view.dayPnl))
    }

    private fun color(v: View, value: Double?): Int = Fmt.colorFor(value)
}
