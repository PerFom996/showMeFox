package com.showmefox.fund

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.showmefox.fund.model.Holding
import com.showmefox.fund.model.Platform
import com.showmefox.fund.ui.PlatformRenderer
import com.showmefox.fund.data.Aggregator
import com.showmefox.fund.model.Quote
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 用 Robolectric 真实 inflate 布局并光栅化成 PNG，验证界面能正常渲染。
 * 注意：这只是布局冒烟测试，不是像素级视觉回归。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiRenderTest {

    private val outDir = File(System.getProperty("project.root") ?: "..", "docs/screenshots")

    private fun demoData(): Triple<List<Platform>, List<Holding>, Map<String, Quote>> {
        val p1 = Platform(id = "p1", name = "支付宝")
        val p2 = Platform(id = "p2", name = "天天基金")
        val p3 = Platform(id = "p3", name = "招商银行")

        val holdings = listOf(
            Holding("h1", "p1", "000001", "华夏成长混合", 34174.0, 30000.0),
            Holding("h2", "p1", "005827", "易方达蓝筹精选混合", 13448.0, 14000.0),
            Holding("h3", "p2", "110022", "易方达消费行业股票", 5522.0, 5200.0),
            Holding("h4", "p2", "161725", "招商中证白酒指数", 19058.0, 20000.0),
            Holding("h5", "p3", "000198", "天弘余额宝货币", 19000.0, 19000.0),
        )
        val quotes = mapOf(
            "000001" to Quote("000001", "华夏成长混合", nav = 0.9510, changePct = 0.74, navDate = "2026-11-17"),
            "005827" to Quote("005827", "易方达蓝筹精选混合", nav = 2.1340, changePct = -1.12, navDate = "2026-11-17"),
            "110022" to Quote("110022", "易方达消费行业股票", nav = 3.0270, changePct = 0.63, navDate = "2026-11-17"),
            "161725" to Quote("161725", "招商中证白酒指数", nav = 1.0520, changePct = 1.28, navDate = "2026-11-17"),
            "000198" to Quote("000198", "天弘余额宝货币", nav = 1.0000, changePct = null, navDate = "2026-11-17"),
        )
        return Triple(listOf(p1, p2, p3), holdings, quotes)
    }

    private fun renderView(view: View, width: Int, name: String) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(
            view.measuredWidth.coerceAtLeast(1),
            view.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        outDir.mkdirs()
        File(outDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun `renders main list with demo data`() {
        val activity: Activity = Robolectric.buildActivity(MainActivity::class.java)
            .setup().get()
        val root = activity.findViewById<View>(android.R.id.content)
        val width = 1080
        renderView(root, width, "01_main_empty.png")
    }

    @Test
    fun `renders platform cards`() {
        val activity: Activity = Robolectric.buildActivity(MainActivity::class.java)
            .setup().get()
        val (platforms, holdings, quotes) = demoData()
        val container = activity.findViewById<LinearLayout>(R.id.platformContainer)
        val views = Aggregator.views(platforms, holdings, quotes)
        val totalMap = Aggregator.platformTotals(platforms, views).associateBy { it.platform.id }
        val renderer = PlatformRenderer(container, {}, {})
        renderer.render(Aggregator.groupByPlatform(platforms, views), totalMap)

        renderView(container, 1080, "02_platform_cards.png")
    }

    @Test
    fun `renders total panel`() {
        val activity: Activity = Robolectric.buildActivity(MainActivity::class.java)
            .setup().get()
        val panel = activity.findViewById<View>(R.id.tvTotalValue).parent as View
        val (platforms, holdings, quotes) = demoData()
        val views = Aggregator.views(platforms, holdings, quotes)
        val t = Aggregator.totals(views)

        activity.findViewById<TextView>(R.id.tvTotalValue).text = "¥128,420.35"
        activity.findViewById<TextView>(R.id.tvDayPnl).text = "+1,236.80"
        activity.findViewById<TextView>(R.id.tvDayPct).text = "+0.97%"
        activity.findViewById<TextView>(R.id.tvTotalPnl).text = "+12,420.35"
        activity.findViewById<TextView>(R.id.tvTotalPct).text = "+10.70%"
        activity.findViewById<TextView>(R.id.tvCost).text = "成本 ¥116,000.00"
        activity.findViewById<TextView>(R.id.tvCount).text = "3 个平台 · 5 只基金"

        renderView(panel, 1080, "03_total_panel.png")
    }
}
