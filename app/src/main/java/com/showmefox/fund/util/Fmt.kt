package com.showmefox.fund.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 金额 / 百分比的统一格式化，避免各个界面各写一套导致口径不一。 */
object Fmt {

    /** 金额：¥1,234.56；超过 1 万自动用「万」，避免面板数字太长。 */
    fun money(value: Double, compact: Boolean = false): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        val abs = kotlin.math.abs(value)
        return if (compact && abs >= 10000) {
            val wan = BigDecimal(value / 10000.0).setScale(2, RoundingMode.HALF_UP)
            "${if (value < 0) "-" else ""}${wan.toPlainString()}万"
        } else {
            val bd = BigDecimal(value).setScale(2, RoundingMode.HALF_UP)
            "${if (value < 0) "-" else ""}${bd.abs().toPlainString()}"
        }
    }

    fun signedMoney(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        val prefix = if (value > 0) "+" else if (value < 0) "-" else ""
        return prefix + money(kotlin.math.abs(value))
    }

    /** 百分比：+1.23%；[digits] 控制小数位。 */
    fun pct(value: Double?, digits: Int = 2): String {
        if (value == null || value.isNaN() || value.isInfinite()) return "—"
        val bd = BigDecimal(value).setScale(digits, RoundingMode.HALF_UP)
        val prefix = if (value > 0) "+" else if (value < 0) "-" else ""
        return prefix + bd.abs().toPlainString() + "%"
    }

    fun nav(value: Double?): String {
        if (value == null || value.isNaN()) return "—"
        return BigDecimal(value).setScale(4, RoundingMode.HALF_UP).toPlainString()
    }

    fun shares(value: Double): String =
        BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    fun clock(ts: Long): String = if (ts <= 0) "—" else timeFmt.format(Date(ts))

    fun dateTime(ts: Long): String = if (ts <= 0) "—" else dateTimeFmt.format(Date(ts))

    /** 国内习惯：涨红跌绿。 */
    fun colorFor(value: Double?): Int = when {
        value == null || value == 0.0 -> 0xFF6B7280.toInt()
        value > 0 -> 0xFFE5484D.toInt()
        else -> 0xFF12A150.toInt()
    }
}
