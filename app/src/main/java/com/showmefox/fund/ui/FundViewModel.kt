package com.showmefox.fund.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.showmefox.fund.data.Aggregator
import com.showmefox.fund.data.FundApi
import com.showmefox.fund.data.FundStore
import com.showmefox.fund.model.Holding
import com.showmefox.fund.model.HoldingView
import com.showmefox.fund.model.Platform
import com.showmefox.fund.model.PlatformTotal
import com.showmefox.fund.model.Quote
import com.showmefox.fund.model.Totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class UiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val platforms: List<Platform> = emptyList(),
    val views: List<HoldingView> = emptyList(),
    val grouped: List<Pair<Platform, List<HoldingView>>> = emptyList(),
    val platformTotals: List<PlatformTotal> = emptyList(),
    val totals: Totals = Totals(),
    val lastRefreshed: Long = 0L,
    val message: String? = null,
    val error: String? = null,
)

class FundViewModel(app: Application) : AndroidViewModel(app) {

    private val store = FundStore(app)
    private val api = FundApi()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 行情缓存：code -> Quote。刷新后整体替换。 */
    private var quotes: MutableMap<String, Quote> = LinkedHashMap()

    init {
        viewModelScope.launch { loadFromDisk() }
    }

    private suspend fun loadFromDisk() = withContext(Dispatchers.IO) {
        val snap = store.load()
        quotes = snap.quoteCache.mapValues { (code, c) ->
            Quote(
                code = code,
                name = snap.holdings.firstOrNull { it.code == code }?.name ?: code,
                nav = c.nav,
                navDate = c.navDate,
                changePct = c.changePct,
                fetchedAt = c.fetchedAt,
                source = "cache",
            )
        }.toMutableMap()
        emit(snap.platforms, snap.holdings, loading = false)
        if (snap.holdings.isNotEmpty()) refresh()
    }

    private fun current(): FundStore.Snapshot {
        val s = _state.value
        return FundStore.Snapshot(s.platforms, s.views.map { it.holding }, store.load().quoteCache)
    }

    private fun persist() {
        val s = _state.value
        val snap = FundStore.Snapshot(
            platforms = s.platforms,
            holdings = s.views.map { it.holding },
            quoteCache = quotes.mapValues { (code, q) ->
                FundStore.CachedQuote(
                    code = code,
                    nav = q.nav,
                    navDate = q.navDate,
                    changePct = q.changePct,
                    fetchedAt = q.fetchedAt,
                )
            },
        )
        viewModelScope.launch(Dispatchers.IO) { store.save(snap) }
    }

    private fun emit(
        platforms: List<Platform>,
        holdings: List<Holding>,
        loading: Boolean = false,
        refreshing: Boolean = false,
        message: String? = null,
        error: String? = null,
        markRefreshed: Boolean = false,
    ) {
        val views = Aggregator.views(platforms, holdings, quotes)
        _state.value = _state.value.copy(
            loading = loading,
            refreshing = refreshing,
            platforms = platforms,
            views = views,
            grouped = Aggregator.groupByPlatform(platforms, views),
            platformTotals = Aggregator.platformTotals(platforms, views),
            totals = Aggregator.totals(views),
            message = message,
            error = error,
            lastRefreshed = if (markRefreshed && error == null) System.currentTimeMillis()
            else _state.value.lastRefreshed,
        )
    }

    /** 拉取所有持仓的最新净值。失败时保留旧数据并提示，不把金额清成 0。 */
    fun refresh() {
        val s = _state.value
        if (s.refreshing || s.views.isEmpty()) {
            if (s.views.isEmpty()) _state.value = s.copy(refreshing = false)
            return
        }
        _state.value = s.copy(refreshing = true, error = null, message = null)
        viewModelScope.launch {
            val codes = s.views.map { it.holding.code }.distinct()
            val fresh = runCatching { api.fetchQuotes(codes) }.getOrElse { emptyMap() }
            withContext(Dispatchers.IO) {
                fresh.forEach { (code, q) -> quotes[code] = q }
            }
            // 用新净值更新每笔持仓的离线兜底值
            val updatedHoldings = s.views.map { v ->
                val q = quotes[v.holding.code]
                if (q?.nav != null) {
                    v.holding.copy(
                        lastValue = q.nav * v.holding.shares,
                        lastNavDate = q.navDate,
                    )
                } else v.holding
            }
            val missing = updatedHoldings.count { quotes[it.code]?.nav == null }
            emit(
                platforms = s.platforms,
                holdings = updatedHoldings,
                refreshing = false,
                error = if (fresh.isEmpty()) "刷新失败：网络异常或接口不可用，当前显示上次数据" else null,
                message = if (missing > 0 && fresh.isNotEmpty()) "有 $missing 只基金未取到净值" else null,
                markRefreshed = fresh.isNotEmpty(),
            )
            if (fresh.isNotEmpty()) persist()
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    // ---------- 平台 ----------

    fun addPlatform(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val s = _state.value
        if (s.platforms.any { it.name == n }) {
            _state.value = s.copy(error = "平台「$n」已存在")
            return
        }
        val platform = Platform(name = n)
        emit(s.platforms + platform, s.views.map { it.holding }, message = "已添加平台「$n」")
        persist()
    }

    fun renamePlatform(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val s = _state.value
        val platforms = s.platforms.map { if (it.id == id) it.copy(name = n) else it }
        emit(platforms, s.views.map { it.holding })
        persist()
    }

    fun deletePlatform(id: String) {
        val s = _state.value
        if (s.views.any { it.holding.platformId == id }) {
            _state.value = s.copy(error = "该平台下还有基金，请先删除基金")
            return
        }
        emit(s.platforms.filterNot { it.id == id }, s.views.map { it.holding }, message = "已删除平台")
        persist()
    }

    // ---------- 持仓 ----------

    fun addHolding(platformId: String, code: String, name: String, shares: Double, cost: Double) {
        val s = _state.value
        if (code.length != 6 || !code.all { it.isDigit() }) {
            _state.value = s.copy(error = "基金代码应为 6 位数字")
            return
        }
        if (shares <= 0) {
            _state.value = s.copy(error = "份额必须大于 0")
            return
        }
        val holding = Holding(
            id = UUID.randomUUID().toString(),
            platformId = platformId,
            code = code,
            name = name.ifBlank { code },
            shares = shares,
            cost = cost.coerceAtLeast(0.0),
            lastValue = cost.coerceAtLeast(0.0),
        )
        emit(s.platforms, s.views.map { it.holding } + holding, message = "已添加 $name")
        persist()
        refresh()
    }

    fun updateHolding(holding: Holding) {
        val s = _state.value
        val list = s.views.map { if (it.holding.id == holding.id) holding else it.holding }
        emit(s.platforms, list, message = "已保存修改")
        persist()
        refresh()
    }

    fun deleteHolding(id: String) {
        val s = _state.value
        emit(s.platforms, s.views.map { it.holding }.filterNot { it.id == id }, message = "已删除")
        persist()
    }

    fun lookup(code: String, onDone: (name: String?) -> Unit) {
        viewModelScope.launch {
            val q = runCatching { api.fetchQuotes(listOf(code)) }.getOrElse { emptyMap() }
            onDone(q[code]?.name)
        }
    }

    fun search(keyword: String, onDone: (List<com.showmefox.fund.model.SearchItem>) -> Unit) {
        viewModelScope.launch {
            val list = runCatching { api.search(keyword) }.getOrElse { emptyList() }
            onDone(list)
        }
    }
}
