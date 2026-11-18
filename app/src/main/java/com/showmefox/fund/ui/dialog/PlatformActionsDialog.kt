package com.showmefox.fund.ui.dialog

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.showmefox.fund.R
import com.showmefox.fund.model.Holding
import com.showmefox.fund.model.Platform
import com.showmefox.fund.ui.FundViewModel

/** 平台长按后的操作：改份额外的第二层菜单。 */
object PlatformActionsDialog {

    fun show(context: Context, platform: Platform, vm: FundViewModel) {
        val items = arrayOf("重命名平台", "删除平台")
        AlertDialog.Builder(context)
            .setTitle(platform.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> rename(context, platform, vm)
                    1 -> confirmDelete(context, platform, vm)
                }
            }
            .show()
    }

    private fun rename(context: Context, platform: Platform, vm: FundViewModel) {
        val input = EditText(context).apply {
            setText(platform.name)
            setSelection(platform.name.length)
            setPadding(56, 40, 56, 40)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("重命名平台")
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) vm.renamePlatform(platform.id, name)
            }
            .show()
    }

    private fun confirmDelete(context: Context, platform: Platform, vm: FundViewModel) {
        val count = vm.state.value.views.count { it.holding.platformId == platform.id }
        if (count > 0) {
            MaterialAlertDialogBuilder(context)
                .setTitle("无法删除")
                .setMessage("「${platform.name}」下还有 $count 只基金，请先删除这些基金。")
                .setPositiveButton(R.string.confirm, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("删除平台「${platform.name}」？")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> vm.deletePlatform(platform.id) }
            .show()
    }
}

/** 修改已有持仓的份额与成本。 */
fun editHoldingDialog(context: Context, holding: Holding, vm: FundViewModel) {
    val view = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(56, 32, 56, 8)
    }
    val sharesInput = EditText(context).apply {
        hint = context.getString(R.string.hint_shares)
        setText(holding.shares.toString())
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    val costInput = EditText(context).apply {
        hint = context.getString(R.string.hint_cost)
        setText(holding.cost.toString())
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    view.addView(sharesInput)
    view.addView(costInput)

    MaterialAlertDialogBuilder(context)
        .setTitle("${holding.name}（${holding.code}）")
        .setView(view)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.save) { _, _ ->
            val shares = sharesInput.text?.toString()?.trim()?.toDoubleOrNull()
            val cost = costInput.text?.toString()?.trim()?.toDoubleOrNull() ?: holding.cost
            if (shares != null && shares > 0) {
                vm.updateHolding(holding.copy(shares = shares, cost = cost.coerceAtLeast(0.0)))
            }
        }
        .show()
}
