package com.showmefox.fund.ui.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.showmefox.fund.R
import com.showmefox.fund.model.Platform
import com.showmefox.fund.model.SearchItem
import com.showmefox.fund.ui.FundViewModel

/** 「添加基金」底部弹窗：选平台 → 选基金（搜索 / 代码）→ 填份额与成本。 */
object AddHoldingSheet {

    fun show(context: Context, vm: FundViewModel) {
        val platforms = vm.state.value.platforms
        if (platforms.isEmpty()) {
            promptNewPlatform(context, vm)
            return
        }

        val root = LayoutInflater.from(context).inflate(R.layout.sheet_add_holding, null)
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(root)
        dialog.behavior.peekHeight = 1600

        val etSearch = root.findViewById<TextInputEditText>(R.id.etSearch)
        val etCode = root.findViewById<TextInputEditText>(R.id.etCode)
        val etShares = root.findViewById<TextInputEditText>(R.id.etShares)
        val etCost = root.findViewById<TextInputEditText>(R.id.etCost)
        val tilCode = root.findViewById<TextInputLayout>(R.id.tilCode)
        val tilShares = root.findViewById<TextInputLayout>(R.id.tilShares)
        val tvPicked = root.findViewById<TextView>(R.id.tvPicked)
        val tvPlatform = root.findViewById<TextView>(R.id.tvPlatformPicked)
        val rv = root.findViewById<RecyclerView>(R.id.rvSearch)

        var selectedPlatform: Platform = platforms.first()
        var pickedName: String? = null

        fun refreshPlatformLabel() {
            tvPlatform.text = "平台：${selectedPlatform.name}  ▾"
        }
        refreshPlatformLabel()

        tvPlatform.setOnClickListener {
            val list = vm.state.value.platforms
            val names = (list.map { it.name } + "＋ 新建平台").toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("选择平台")
                .setItems(names) { _, which ->
                    if (which == list.size) {
                        promptNewPlatform(context, vm)
                        dialog.dismiss()
                    } else {
                        selectedPlatform = list[which]
                        refreshPlatformLabel()
                    }
                }
                .show()
        }

        val adapter = SearchAdapter { item ->
            etCode.setText(item.code)
            etSearch.setText("")
            rv.visibility = View.GONE
            pickedName = item.name
            tvPicked.visibility = View.VISIBLE
            tvPicked.text = "已选择：${item.name}（${item.code}）"
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        fun runSearch() {
            val kw = etSearch.text?.toString()?.trim().orEmpty()
            if (kw.isEmpty()) return
            vm.search(kw) { list ->
                adapter.submit(list)
                rv.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                if (list.isEmpty()) toast(context, "没找到匹配的基金")
            }
        }

        etSearch.setOnEditorActionListener { _, _, event ->
            if (event == null || event.action == KeyEvent.ACTION_DOWN) runSearch()
            true
        }

        // 输入 6 位代码后自动回显全名，防止代码敲错
        etCode.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val code = etCode.text?.toString()?.trim().orEmpty()
            if (code.length != 6) return@setOnFocusChangeListener
            vm.lookup(code) { name ->
                if (name != null && name != code) {
                    pickedName = name
                    tvPicked.visibility = View.VISIBLE
                    tvPicked.text = "已识别：$name（$code）"
                } else if (name == null) {
                    tvPicked.visibility = View.VISIBLE
                    tvPicked.text = "未找到该代码对应的基金，请检查"
                }
            }
        }

        root.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        root.findViewById<View>(R.id.btnSave).setOnClickListener {
            tilCode.error = null
            tilShares.error = null
            val code = etCode.text?.toString()?.trim().orEmpty()
            val shares = etShares.text?.toString()?.trim()?.toDoubleOrNull()
            val cost = etCost.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
            when {
                !code.matches(Regex("""\d{6}""")) -> tilCode.error = "请输入 6 位基金代码"
                shares == null || shares <= 0 -> tilShares.error = "请填写持有份额"
                else -> {
                    vm.addHolding(
                        platformId = selectedPlatform.id,
                        code = code,
                        name = pickedName ?: code,
                        shares = shares,
                        cost = cost,
                    )
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    /** 新建平台输入框。 */
    fun promptNewPlatform(context: Context, vm: FundViewModel) {
        val input = EditText(context).apply {
            hint = context.getString(R.string.hint_platform_name)
            setPadding(56, 40, 56, 40)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.title_add_platform)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) vm.addPlatform(name)
            }
            .show()
    }
}

/** 搜索候选列表适配器。 */
internal class SearchAdapter(
    private val onClick: (SearchItem) -> Unit,
) : RecyclerView.Adapter<SearchAdapter.VH>() {

    private val items = ArrayList<SearchItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<SearchItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val sub: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        holder.sub.text = item.code
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
