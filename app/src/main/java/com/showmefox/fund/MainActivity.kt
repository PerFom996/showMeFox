package com.showmefox.fund

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.showmefox.fund.model.HoldingView
import com.showmefox.fund.model.Platform
import com.showmefox.fund.ui.FundViewModel
import com.showmefox.fund.ui.PlatformRenderer
import com.showmefox.fund.ui.UiState
import com.showmefox.fund.ui.dialog.AddHoldingSheet
import com.showmefox.fund.ui.dialog.PlatformActionsDialog
import com.showmefox.fund.ui.dialog.editHoldingDialog
import com.showmefox.fund.util.Fmt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var vm: FundViewModel
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var container: LinearLayout
    private lateinit var renderer: PlatformRenderer

    private lateinit var tvTotalValue: TextView
    private lateinit var tvDayPnl: TextView
    private lateinit var tvDayPct: TextView
    private lateinit var tvTotalPnl: TextView
    private lateinit var tvTotalPct: TextView
    private lateinit var tvCost: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvStale: TextView
    private lateinit var tvEmpty: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vm = ViewModelProvider(this)[FundViewModel::class.java]

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> { vm.refresh(); true }
                R.id.action_manage -> { showPlatformManager(); true }
                else -> false
            }
        }

        swipe = findViewById(R.id.swipe)
        swipe.setColorSchemeColors(getColor(R.color.brand))
        swipe.setProgressBackgroundColorSchemeColor(getColor(R.color.surface))
        swipe.setOnRefreshListener { vm.refresh() }

        container = findViewById(R.id.platformContainer)
        tvEmpty = findViewById(R.id.tvEmptyHint)

        tvTotalValue = findViewById(R.id.tvTotalValue)
        tvDayPnl = findViewById(R.id.tvDayPnl)
        tvDayPct = findViewById(R.id.tvDayPct)
        tvTotalPnl = findViewById(R.id.tvTotalPnl)
        tvTotalPct = findViewById(R.id.tvTotalPct)
        tvCost = findViewById(R.id.tvCost)
        tvCount = findViewById(R.id.tvCount)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvStale = findViewById(R.id.tvStale)

        renderer = PlatformRenderer(
            container = container,
            onHoldingClick = { view -> showHoldingActions(view) },
            onPlatformClick = { platform -> PlatformActionsDialog.show(this, platform, vm) },
        )

        findViewById<View>(R.id.fabAdd).setOnClickListener {
            AddHoldingSheet.show(this, vm)
        }

        lifecycleScope.launch {
            vm.state.collectLatest { render(it) }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun render(state: UiState) {
        swipe.isRefreshing = state.refreshing && !state.loading

        val t = state.totals
        tvTotalValue.text = "¥" + Fmt.money(t.totalValue)
        tvDayPnl.text = Fmt.signedMoney(t.dayPnl)
        tvDayPnl.setTextColor(Fmt.colorFor(t.dayPnl))
        tvDayPct.text = Fmt.pct(t.dayPct)
        tvDayPct.setTextColor(Fmt.colorFor(t.dayPct))

        tvTotalPnl.text = Fmt.signedMoney(t.totalPnl)
        tvTotalPnl.setTextColor(Fmt.colorFor(t.totalPnl))
        tvTotalPct.text = Fmt.pct(t.totalPct)
        tvTotalPct.setTextColor(Fmt.colorFor(t.totalPct))

        tvCost.text = "成本 ¥" + Fmt.money(t.totalCost)
        tvCount.text = if (state.platforms.isEmpty()) "" else
            "${state.platforms.size} 个平台 · ${state.views.size} 只基金"

        tvUpdated.text = if (state.lastRefreshed > 0)
            "更新于 ${Fmt.dateTime(state.lastRefreshed)}" else getString(R.string.never_updated)

        val empty = state.views.isEmpty()
        tvEmpty.visibility = if (empty && !state.loading) View.VISIBLE else View.GONE

        val totalsMap = state.platformTotals.associateBy { it.platform.id }
        renderer.render(state.grouped, totalsMap)

        val stale = state.views.count { !it.hasQuote }
        if (stale > 0) {
            tvStale.visibility = View.VISIBLE
            tvStale.text = "有 $stale 只基金暂无最新净值，显示的是上次成功数据"
        } else {
            tvStale.visibility = View.GONE
        }

        state.message?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
        state.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    private fun showHoldingActions(view: HoldingView) {
        val items = arrayOf("修改份额 / 成本", "删除这只基金")
        MaterialAlertDialogBuilder(this)
            .setTitle(view.holding.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> editHoldingDialog(this, view.holding, vm)
                    1 -> confirmDelete(view)
                }
            }
            .show()
    }

    private fun confirmDelete(view: HoldingView) {
        AlertDialog.Builder(this)
            .setTitle("删除「${view.holding.name}」？")
            .setMessage("只会删除本地记录，不影响你在平台的真实持仓。")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> vm.deleteHolding(view.holding.id) }
            .show()
    }

    private fun showPlatformManager() {
        AddHoldingSheet.promptNewPlatform(this, vm)
    }
}
