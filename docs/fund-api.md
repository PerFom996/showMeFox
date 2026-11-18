# 公募基金数据免费 HTTP API — 实测验证报告

> 验证时间：2026-11-18（Asia/Shanghai），网络环境：中国大陆
> 验证方式：本机 `curl` 真实请求，响应原文见 `docs/samples/`
> 结论一句话：**净值 + 涨跌幅 + 名称 + 代码搜索全部可用；实时"估算净值"（盘中估值）在本环境已全部失效**。

---

## 1. 能力总表

| 能力 | 精确 URL | 必需请求头 | 响应格式 | 验证结果 |
|---|---|---|---|---|
| **① 最新确认净值 + 日涨跌幅** | `https://api.fund.eastmoney.com/f10/lsjz?fundCode={code}&pageIndex=1&pageSize=1` | `Referer: https://fundf10.eastmoney.com/` **（必须）** | JSON | ✅ **已验证** |
| **② 批量：净值+名称（含估算字段）** | `https://fundmobapi.eastmoney.com/FundMNewApi/FundMNFInfo?Fcodes={code1},{code2}&pageIndex=1&pageSize=2&plat=Android&appType=ttjj&product=EFund&Version=6.5.5&deviceid=abc123` | 无（可选 Referer） | JSON | ✅ **已验证** |
| **③ 基金名称 / 代码互查** | `https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key={UTF8编码关键词}` | 无 | JSON | ✅ **已验证** |
| **④ 全量档案（名称/类型/经理/规模）** | `https://fund.eastmoney.com/pingzhongdata/{code}.js` | 无 | JS 变量（UTF-8） | ✅ **已验证**（766 KB） |
| **⑤ 轻量名称+净值（腾讯，GBK）** | `https://qt.gtimg.cn/q=jj{code}` | 无 | `~` 分隔文本，**GBK** | ✅ 已验证 |
| **⑥ 轻量名称+净值（新浪，GBK）** | `https://hq.sinajs.cn/list=f_{code}` | `Referer: https://finance.sina.com.cn/` | `,` 分隔文本，**GBK** | ✅ 已验证 |
| **⑦ 实时估算净值（盘中估值）** | `https://fundgz.1234567.com.cn/js/{code}.js?rt={ms}` | — | 期望 `jsonpgz({...})` | ❌ **已失效** |
| ⑧ 估算列表 `api.fund.eastmoney.com/FundGuZhi/GetFundGZList` | 同左 | — | — | ❌ 404 |
| ⑨ 蛋卷估值 `danjuanfunds.com/djapi/fund/estimate/{code}` | 同左 | — | — | ❌ 需登录 |

---

## 2. 逐项实测证据

### ① 最新确认净值 —— 首选主力接口

```bash
curl -H "Referer: https://fundf10.eastmoney.com/" \
  "https://api.fund.eastmoney.com/f10/lsjz?fundCode=000001&pageIndex=1&pageSize=1"
```

实际响应（`docs/samples/lsjz-000001.json`）：

```json
{"Data":{"LSJZList":[{"DWJZ":"0.9510","FHFCBZ":"","FHFCZ":"","FHFCZ10":"","FSRQ":"2026-11-17","JZZZL":"0.74","LJJZ":"3.5240","NAVTYPE":"1","SGZT":"开放申购","SHZT":"开放赎回","SDATE":null,"ACTUALSYI":"","DTYPE":null,"FHSP":""}],"FundType":"002","SYType":null,"isNewType":false,"Feature":"215"},"ErrCode":0,"ErrMsg":null,"TotalCount":6046,"Expansion":null,"PageSize":1,"PageIndex":1}
```

**字段含义**

| 字段 | 含义 | 类型 |
|---|---|---|
| `DWJZ` | 单位净值（今日） | string，需 `toDouble()` |
| `JZZZL` | 日涨跌幅 %（如 `"0.74"` = +0.74%） | string |
| `FSRQ` | 净值日期 `yyyy-MM-dd` | string |
| `LJJZ` | 累计净值 | string |
| `SGZT` / `SHZT` | 申购 / 赎回状态 | string |
| `ErrCode` | `0` 成功；`-999` = 缺 Referer | number |

**⚠️ 关键发现：Referer 是唯一硬门槛，UA 无所谓**

```
no UA, WITH referer  : {"Data":{"LSJZList":[{"FSRQ":"2026-11-17","DWJZ":"0.9510",...   ✅
UA, NO referer       : {"Data":"","ErrCode":-999,"ErrMsg":"","TotalCount":0,...}       ❌
```

`okhttp/4.12.0` 作为 UA + 带 Referer 同样返回 200 正常数据 —— **Android OkHttp 只需加一个 Referer 头即可**。

### ② 批量净值+名称（强烈推荐做列表页）

```bash
curl "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNFInfo?Fcodes=000001,110022&pageIndex=1&pageSize=2&plat=Android&appType=ttjj&product=EFund&Version=6.5.5&deviceid=abc123"
```

实际响应（`docs/samples/fundmnfinfo-000001.json`）：

```json
{"Datas":[{"FCODE":"000001","SHORTNAME":"华夏成长混合","PDATE":"2026-11-17","NAV":"0.9510","ACCNAV":"3.5240","NAVCHGRT":"0.74","GSZ":null,"GSZZL":null,"GZTIME":null,"NEWPRICE":null,"CHANGERATIO":null,"ZJL":null,"HQDATE":null,"ISHAVEREDPACKET":false}],"ErrCode":0,"Success":true,...,"Expansion":{"GZTIME":"2026-11-18","FSRQ":"2026-11-17"}}
```

**一次请求拿多只基金**（`Fcodes` 逗号分隔），适合自选列表。字段：`NAV` 单位净值、`ACCNAV` 累计净值、`NAVCHGRT` 日涨幅、`SHORTNAME` 名称、`PDATE` 净值日期。

⚠️ `GSZ` / `GSZZL` / `GZTIME`（估算净值/估算涨幅/估值时间）**当前全部返回 `null`**，不要依赖。
⚠️ `pageIndex` 和 `pageSize` 必传，漏掉返回 `ErrCode:61136`。

### ③ 代码 / 名称互查

```bash
curl "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=%E6%98%93%E6%96%B9%E8%BE%BE%E5%9F%BA%E9%87%91"
```

实际响应（`docs/samples/fundsuggest-yifangda.json`，已截断）：

```json
{"ErrCode":0,"ErrMsg":"fromes","Datas":[{"_id":"007379","CODE":"007379","NAME":"易方达上证50ETF联接基金A","CATEGORY":700,"CATEGORYDESC":"基金","FundBaseInfo":{"FCODE":"007379","SHORTNAME":"易方达上证50ETF联接基金A","JJGS":"易方达基金","JJJL":"余海燕","FUNDTYPE":"001","FTYPE":"指数型-股票","MINSG":10.0}}]}
```

- 取 `Datas[].CODE` 与 `Datas[].NAME`；`HIGHTLIGHT` 里含 `<font>` HTML 标签，**不要直接展示**。
- 输入 6 位数字也可直接反查，但**会混入股票和指数**：实测 `key=000001` → 3 条
  `["华夏成长混合", "平安银行", "上证指数"]`。必须用 `CATEGORYDESC == "基金"`（或 `CATEGORY == 700`）过滤。

**⚠️ 编码坑（实测踩到）**：`key` 必须是 **UTF-8** 百分号编码。
`%E6%98%93%E6%96%B9%E8%BE%BE` → 10 条结果 ✅；
GBK 编码 `%BB%AA%CF%C4` → `"Datas":[]` 空数组 ❌。
Android 用 `URLEncoder.encode(key, "UTF-8")` 即可（**不要**用默认字符集）。

### ④ pingzhongdata 全量档案

```bash
curl "https://fund.eastmoney.com/pingzhongdata/000001.js"   # 766,796 bytes, UTF-8, 无 BOM
```

用正则抽取。已验证存在的变量：
`fS_name`、`fS_code`、`fund_Rate`、`fund_sourceRate`、`fund_minsg`、`ishb`、
`Data_netWorthTrend`、`Data_ACWorthTrend`、`Data_grandTotal`、`Data_currentFundManager`、
`Data_assetAllocation`、`Data_fluctuationScale`、`Data_holderStructure`、`Data_performanceEvaluation`,
`syl_1n`、`syl_6y`、`syl_3y`、`syl_1y`, `stockCodes`、`zqCodes`。

实测取值：

```
fS_name = "华夏成长混合"
fS_code = "000001"
syl_1n = "-8.12"      # 近1年收益率 %
Data_netWorthTrend 长度 6046
最后一条: {"x":1794844800000,"y":0.951,"equityReturn":0.74,"unitMoney":""}
倒数二条: {"x":1794758400000,"y":0.944,"equityReturn":-4.45,"unitMoney":""}
Data_ACWorthTrend 最后一条: [1794844800000, 3.524]
```

- `x` = 毫秒时间戳（UTC 零点，对应北京时间当日 08:00，**取日期要用 UTC 或直接除以 86400000 取整**）
- `y` = 单位净值；`equityReturn` = 日涨跌幅 %；`unitMoney` = 分红送配说明
- `Data_ACWorthTrend` 是 `[timestamp, 累计净值]` 二元数组

⚠️ **货币基金没有 `Data_netWorthTrend`**（实测 `000198` 该变量不存在，`ishb=true`），必须走 ①的 lsjz。

### ⑤⑥ 轻量备用源（GBK！）

```
腾讯  https://qt.gtimg.cn/q=jj000001
      v_jj000001="000001~华夏成长混合~0.0000~0.0000~~0.9510~3.5240~0.7415~2026-11-17~";
                                  ↑净值和累计净值在 index 5,6  涨跌幅 index 7  日期 index 8
新浪  https://hq.sinajs.cn/list=f_000001   （需 Referer: https://finance.sina.com.cn/）
      var hq_str_f_000001="华夏成长混合A,0.951,3.524,0.944,2026-11-17,23.8905";
                              ↑名称      ↑净值  ↑累计  ↑昨日净值 ↑日期
```

两者**都是 GBK 编码**，必须 `new String(bytes, "GBK")`，否则中文全是乱码（实测原始字节即乱码，`iconv` 后正常）。腾讯支持 `jj000001,jj110022` 批量。

---

## 3. 关于「实时估算净值」——实测已失效，请勿依赖

**结论：`fundgz.1234567.com.cn` 全站返回 404 页面，与 https/UA/Referer/参数/TLS 版本/节点 IP 全部无关。**

排查过程（全部实测）：

| 测试 | 结果 |
|---|---|
| `http://` 与 `https://` 无头 | 均 200，但 `Content-Type: text/html`，3924 字节「页面未找到 - 东方财富网」 |
| `+ User-Agent + Referer: https://fund.eastmoney.com/` | 仍是同样 404 页 |
| `+ ?rt=<timestamp>` 时间戳破缓存 | 仍是同样 404 页 |
| `+ full browser headers（Accept/Origin/Accept-Encoding）` | 仅因 gzip 变成 1653 字节，解压后仍是同一个 404 HTML |
| 多个基金代码 000001 / 110022 / 005827 / 161725 / 000961 | **全部** 3924 字节 404 页 —— 不是单只基金问题 |
| 逐个解析 DNS 的 3 个 CDN IP（`--resolve` 121.233.195.181 / 180.119.119.116 / 218.92.141.97） | 全部命中同一份 `Last-Modified: 2025-09-03` 的陈旧缓存 404 |
| TLS 1.2 / TLS 1.3 分别强制 | 均正常握手，与加密无关 |

**最关键证据**：天天基金自己的前端 JS
（`https://j5.dfcfw.com/sc/js/pinzhong/pz_common_min_20250708.js`）里，构造的就是这个 URL：

```js
var reGetGZ = function () {
  common.JsLoader("//fundgz.1234567.com.cn/js/" + fS_code + ".js?rt=" + new Date().getTime(), "utf-8", ...)
};
```

即：**我的请求格式和官方完全一致，是服务端本身挂了/下线了，不是调用姿势问题。** 官方页面 `fund.eastmoney.com/000001.html` 仍保留 `id="gz_gsz"` 等 DOM 占位，但 `FundGZ()` 拉不到数据，接口层（`api.fund.eastmoney.com/FundGuZhi/GetFundGZList`、`FundMNewApi/FundMNFInfo` 的 `GSZ` 字段）也全部返回 null / 404；蛋卷 `djapi/fund/estimate/{code}` 返回 `{"result_code":300001,"message":"请重新登录"}`。

> **给 App 的设计建议**：估算字段一律做「可空」处理，UI 上不要把它当作核心数据。

---

## 4. Android / OkHttp 兼容性（TLS/SNI）

实测结论：**没有 SNI 或证书坑**。

- `fund.eastmoney.com` 证书 `CN=*.eastmoney.com`，SAN 含 `DNS:*.eastmoney.com` **以及** `DNS:eastmoney.com`（裸域也在列），DigiCert GeoTrust G2 签发，`Verify return code: 0 (ok)`，严格校验证书通过。
- `api.fund.eastmoney.com` 证书 `CN=*.fund.eastmoney.com`，同样 `*.fund.eastmoney.com`生效、校验通过。
- `fundgz.1234567.com.cn` 证书 `CN=*.1234567.com.cn`，也正常（它的 404 是业务层的，不是证书问题）。
- 服务器只支持 **HTTP/1.1**（ALPN 协商结果为 `http/1.1`，无 h2），OkHttp 默认兼容，无需配置。
- TLS 1.2 与 TLS 1.3 均可用，OkHttp 默认策略直接跑通。
- `https://fund.eastmoney.com/pingzhongdata/000001.js` 证书正常，无需 `--insecure`。

**唯一必须做的事：给 `api.fund.eastmoney.com` 请求加 `Referer` 头。**（OkHttp 默认不发 Referer，这是最容易踩的坑）

---

## 5. 推荐策略：主用 + 降级

```
第 1 层（盘中/日常刷新，主力）
  GET fundmobapi.../FundMNFInfo?Fcodes=<批量>      → 名称 + 最新净值 + 日涨幅（一次多只）
      ↓ 失败（超时/非 JSON/ErrCode!=0）
第 2 层（单只精确，权威）
  GET api.fund.eastmoney.com/f10/lsjz  (带 Referer) → 单只净值 + 日涨幅 + 净值日期
      ↓ 失败
第 3 层（轻量兜底，GBK）
  GET qt.gtimg.cn/q=jj<code>                        → 名称 + 净值 + 涨幅
      ↓ 失败
第 4 层（离线）
  用本地缓存的「最后一次成功净值」并显示「数据更新于 X」
```

**估算净值**：UI 上保留字段但默认置空/隐藏；若某天 `FundMNFInfo` 的 `GSZ` 非 null，可直接点亮展示（同一份代码不用改）。

用户添加基金时的校验流程：
`FundSearchAPI(key=用户输入)` → 若用户输的是 6 位数字，直接用 `FundMNFInfo?Fcodes=<code>` 拿 `SHORTNAME` 回显给用户确认 → 确认后入库。

---

## 6. 金额与盈亏计算

持有数据模型只需存两个量：**份额 `shares`** 和 **成本 `cost`（总买入金额）**。

```
持仓金额(市值)  = shares × 最新单位净值
当日盈亏        = 持仓金额 × 当日涨跌幅
                = shares × 净值 × (JZZZL / 100)
累计盈亏        = 持仓金额 − cost                    // 有成本时
累计盈亏率      = 累计盈亏 / cost × 100
持仓成本单价    = cost / shares
```

**用代码分支写清楚（注意 null 与 "--"）：**

```kotlin
// 净值与涨幅均来自字符串，务必安全解析
val nav = json.optString("NAV").toDoubleOrNull() ?: return   // 可能为 null / "--"
val chg = json.optString("NAVCHGRT").toDoubleOrNull()        // 货币基金为 "--" → null

val marketValue = shares * nav
val dayPnl      = if (chg != null) marketValue * (chg / 100.0) else 0.0
val totalPnl    = marketValue - cost
val totalPnlPct = if (cost > 0) totalPnl / cost * 100.0 else 0.0
```

**注意事项**

- 货币基金（`ishb=true` / `NAVCHGRT == "--"`）：没有日涨跌幅概念，`DWJZ` 是**万份收益**口径，不能套上面的公式；应改用 `LJJZ`（累计净值）差值或直接展示万份收益。
- `当日盈亏` 在**净值未更新时（如 15:00 前、非交易日）** 应为 0 或沿用上一交易日，判断依据是 `FSRQ == 今天`。
- QDII / 海外基金净值滞后 1～2 天，`FSRQ` 可能是前天的，UI 应展示净值日期而不是「今天」。
- `shares` 建议用 `BigDecimal` 存，避免浮点累积误差；净值本身是 4 位小数。

---

## 7. 可靠性注意事项

| 项目 | 说明 |
|---|---|
| **Referer 硬门槛** | `api.fund.eastmoney.com/f10/lsjz` 不带 Referer 返回 `ErrCode:-999` 且 `Data:""`（**HTTP 仍是 200**，别只看状态码！必须校验 `ErrCode==0`） |
| **HTTP 200 陷阱** | fundgz 的 404 页面返回的也是 **HTTP 200**，`Content-Type: text/html`。判断成功必须校验响应体，不能只看 200 |
| **编码** | `api.fund.eastmoney.com` / `fundmobapi` / `pingzhongdata` / `fundsuggest` 均为 **UTF-8**；腾讯 `qt.gtimg.cn`、新浪 `hq.sinajs.cn` 为 **GBK** |
| **Content-Type 不可信** | pingzhongdata 返回 `application/javascript` 但内容其实是 JS 文本；fundgz 错误页返回 `text/html` |
| **参数顺序/冗余** | `FundSearchAPI` 的 `key` 必须是 UTF-8 编码；`FundMNFInfo` 必须带 `pageIndex`/`pageSize`/`plat`/`product`/`Version`/`deviceid` |
| **限流** | 实测连续 15 次 `lsjz` 全部 200，未见限流；但仍建议客户端做 ≤1 次/秒 节流 + 内存缓存 |
| **缓存新鲜度** | `lsjz` 响应头 `Cache-Control: max-age=45`；净值一般 **交易日 19:00–23:00** 更新，`FSRQ` 是最可靠的「数据日期」 |
| **刷新时间窗** | 官方 JS 仅在**工作日 9:00–15:00** 才轮询估值。非交易时段不必高频刷新 |
| **无效代码** | `lsjz` 对 `999999` 返回 `LSJZList:[]` + `ErrCode:0`（**不报错**，需自己判空）；`pingzhongdata` 对无效代码 **301 跳到 `/notfound.html`**，必须禁用跟随或校验 `fS_code` 是否等于请求 code |
| **货币基金** | 无 `Data_netWorthTrend`、`NAVCHGRT` 为 `"--"`，需单独分支 |
| **pingzhongdata 体积** | 766 KB，含 6000+ 条历史净值，**不要**在列表页调用；列表页用 ②，详情页才拉 ④ |
| **BOM** | 货币基金的 pingzhongdata 开头带 UTF-8 BOM (`\uFEFF`)，正则匹配首个 `var` 前需 strip |

---

## 8. 原始样本文件（`docs/samples/`）

| 文件 | 内容 |
|---|---|
| `lsjz-000001.json` | ① 净值历史 pageSize=5 |
| `fundmnfinfo-000001.json` | ② 批量净值+名称 |
| `fundsuggest-yifangda.json` | ③ 搜索 `易方达基金` |
| `pingzhongdata-000001.js` | ④ 全量档案（766 KB） |
| `pingzhongdata-000001-tail.json` | ④ `Data_netWorthTrend` 最后 3 条（便于看结构） |
| `tencent-jj-gbk.bin` / `tencent-jj.utf8.txt` | ⑤ 原始 GBK 字节 + 转码后 |
| `sina-f-gbk.bin` / `sina-f.utf8.txt` | ⑥ 原始 GBK 字节 + 转码后 |
| `fundgz-000001-404.html` / `fundgz-000001.headers` | ⑦ 失效证据：404 页面正文 + 响应头 |
| `danjuan-fund-000001.json` | 蛋卷档案接口（补充参考） |

---

## 9. 最小可用代码（OkHttp / Kotlin）

```kotlin
val client = OkHttpClient()

// ① 最新净值
fun latestNav(code: String): String? {
    val req = Request.Builder()
        .url("https://api.fund.eastmoney.com/f10/lsjz?fundCode=$code&pageIndex=1&pageSize=1")
        .header("Referer", "https://fundf10.eastmoney.com/")   // ← 唯一必须的 header
        .build()
    client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val body = resp.body?.string() ?: return null
        val root = JsonParser.parseString(body).asJsonObject
        if (root["ErrCode"]?.asInt != 0) return null            // ← 必须校验，200 时也可能 -999
        val item = root["Data"]?.asJsonObject?.getAsJsonArray("LSJZList")
            ?.firstOrNull()?.asJsonObject ?: return null
        // DWJZ=单位净值  JZZZL=日涨跌幅%  FSRQ=净值日期  LJJZ=累计净值
        return item["DWJZ"]?.asString
    }
}
```

```kotlin
// ③ 名称搜索（务必 UTF-8 编码）
val key = URLEncoder.encode(input, "UTF-8")
val url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=$key"
```

```kotlin
// ④ pingzhongdata 正则解析
val name  = Regex("""var fS_name = "(.*?)";""").find(js)?.groupValues?.get(1)
val trend = Regex("""var Data_netWorthTrend = (\[.*?\]);""", RegexOption.DOT_MATCHES_ALL)
    .find(js)?.groupValues?.get(1)
// trend 是 JSON 数组，可直接交给 JSON 解析器
```
