# 📘 网格执行引擎 2.0 · 规格说明（AI 编码版）

> **用途说明**  
> 这是网格交易执行引擎的完整技术规格，可反复喂给 IDE AI / GitHub Copilot / Cursor 等 AI 编码助手使用。

---

## 一、引擎职责

**GridEngine** 接收一个**最新价格**，对指定 **Strategy** 执行以下行为：

1. ✅ **判断是否触发买卖**
2. ✅ **支持"一网打尽"**（一次价格更新触发多个网格）
3. ✅ **自动切换网格状态**
4. ✅ **维护资金 / 持仓 / 收益**
5. ✅ **支持 STOP 风控**

---

## 二、输入 & 前提

### 输入

- **Strategy**（已加载，含 GridLine 集合）
- **当前价格** `price`（BigDecimal）

### 前提条件

- `Strategy.status` ∈ { `RUNNING`, `STOPPED` }
- `GridLine.state` ∈ { `WAIT_BUY`, `WAIT_SELL` }

---

## 三、核心执行流程（顺序不可乱）

### Step 0：更新最新价格（可选）

```java
Strategy.lastPrice = price  // 可选字段
```

---

### Step 1：买入处理（仅 RUNNING）

#### 触发条件（必须同时满足）

1. `Strategy.status == RUNNING`
2. `GridLine.state == WAIT_BUY`
3. `price <= gridLine.buyPrice`
4. `availableCash >= amountPerGrid`

#### 执行规则

- **排序**：按 `buyPrice` **从高到低**排序（靠近当前价的优先）
- **一网打尽**：允许一次触发**多条**网格线
- **每触发一条**：
  1. 生成 `BUY` 类型的 `TradeRecord`
  2. `GridLine.state` → `WAIT_SELL`
  3. 扣减 `availableCash`
  4. 增加 `position`（持仓数量）

---

### Step 2：卖出处理（RUNNING / STOPPED 都允许）

#### 触发条件（必须同时满足）

1. `GridLine.state == WAIT_SELL`
2. `price >= gridLine.sellPrice`

> ⚠️ **注意**：无论策略是 RUNNING 还是 STOPPED，卖出都允许执行

#### 执行规则

- **排序**：按 `sellPrice` **从低到高**排序（靠近当前价的优先）
- **一网打尽**：允许一次触发**多条**网格线
- **每触发一条**：
  1. 生成 `SELL` 类型的 `TradeRecord`
  2. `GridLine.state` → `WAIT_BUY`
  3. 增加 `availableCash`
  4. 减少 `position`（持仓数量）
  5. 记录 `realizedProfit`（已实现收益）

---

### Step 3：风控 STOP 判断（2.0 新增）

#### 策略进入 STOPPED 状态的条件（满足任一即可）

1. ❌ `price <= 最低买入网格价格`
2. ❌ `已投入资金 >= maxCapital`

#### STOP 行为

- ✅ **不清仓**（已买入的持仓保留）
- ✅ **只停止后续买入**
- ✅ **允许卖出**（价格上涨时可平仓获利）

---

## 四、重要设计原则

### 1️⃣ **不允许隐式状态**
- 所有状态必须可从数据推导
- 不依赖内存中的临时变量

### 2️⃣ **不允许在一次 tick 中买卖同一网格**
- 同一个 `GridLine` 在单次价格更新中：
  - 要么买入（WAIT_BUY → WAIT_SELL）
  - 要么卖出（WAIT_SELL → WAIT_BUY）
  - **不能既买又卖**

### 3️⃣ **不依赖前端**
- 引擎独立运行
- 前端只负责展示和配置

### 4️⃣ **可多次调用（幂等性）**
- 相同价格多次调用不会重复触发
- 幂等性靠**状态保证**（GridLine.state）

---

## 五、数据流示例

### 示例 1：买入触发

**初始状态**

| GridLine | buyPrice | sellPrice | state    |
|----------|----------|-----------|----------|
| Grid-1   | 100.00   | 105.00    | WAIT_BUY |
| Grid-2   | 95.00    | 100.00    | WAIT_BUY |

**事件**：当前价格 = 99.00

**执行**：

1. 触发 Grid-1（100.00 >= 99.00）
2. 生成 BUY TradeRecord（buyPrice=100.00）
3. Grid-1.state → WAIT_SELL

**结果状态**

| GridLine | buyPrice | sellPrice | state     |
|----------|----------|-----------|-----------|
| Grid-1   | 100.00   | 105.00    | WAIT_SELL |
| Grid-2   | 95.00    | 100.00    | WAIT_BUY  |

---

### 示例 2：一网打尽（买入多个）

**初始状态**

| GridLine | buyPrice | sellPrice | state    |
|----------|----------|-----------|----------|
| Grid-1   | 100.00   | 105.00    | WAIT_BUY |
| Grid-2   | 95.00    | 100.00    | WAIT_BUY |
| Grid-3   | 90.00    | 95.00     | WAIT_BUY |

**事件**：当前价格 = 89.00（暴跌）

**执行**：

1. 按 buyPrice 从高到低：Grid-1 → Grid-2 → Grid-3
2. 依次触发买入（如果资金充足）
3. 生成 3 条 BUY TradeRecord

**结果状态**

| GridLine | buyPrice | sellPrice | state     |
|----------|----------|-----------|-----------|
| Grid-1   | 100.00   | 105.00    | WAIT_SELL |
| Grid-2   | 95.00    | 100.00    | WAIT_SELL |
| Grid-3   | 90.00    | 95.00     | WAIT_SELL |

---

### 示例 3：卖出触发

**初始状态**

| GridLine | buyPrice | sellPrice | state     |
|----------|----------|-----------|-----------|
| Grid-1   | 100.00   | 105.00    | WAIT_SELL |
| Grid-2   | 95.00    | 100.00    | WAIT_SELL |

**事件**：当前价格 = 106.00

**执行**：

1. 按 sellPrice 从低到高：Grid-2 → Grid-1
2. 依次触发卖出
3. 生成 2 条 SELL TradeRecord

**结果状态**

| GridLine | buyPrice | sellPrice | state    |
|----------|----------|-----------|----------|
| Grid-1   | 100.00   | 105.00    | WAIT_BUY |
| Grid-2   | 95.00    | 100.00    | WAIT_BUY |

---

## 六、关键字段说明

### Strategy 字段

| 字段             | 类型        | 说明                      |
|------------------|-------------|---------------------------|
| status           | Enum        | RUNNING / STOPPED         |
| lastPrice        | BigDecimal  | 最新价格（可选）          |
| availableCash    | BigDecimal  | 可用资金                  |
| investedAmount   | BigDecimal  | 已投入资金                |
| position         | BigDecimal  | 当前持仓数量              |
| realizedProfit   | BigDecimal  | 已实现收益                |
| maxCapital       | BigDecimal  | 最大资金（风控阈值）      |

### GridLine 字段

| 字段      | 类型        | 说明                               |
|-----------|-------------|------------------------------------|
| buyPrice  | BigDecimal  | 买入价格                           |
| sellPrice | BigDecimal  | 卖出价格                           |
| state     | Enum        | WAIT_BUY / WAIT_SELL               |
| level     | Integer     | 网格层级（0=基准，正=向上，负=向下）|

### TradeRecord 字段

| 字段      | 类型          | 说明                     |
|-----------|---------------|--------------------------|
| type      | Enum          | BUY / SELL               |
| price     | BigDecimal    | 成交价格                 |
| amount    | BigDecimal    | 成交金额                 |
| quantity  | BigDecimal    | 成交数量                 |
| tradeTime | LocalDateTime | 成交时间                 |

---

## 七、实现检查清单

在实现 GridEngine 时，请确保：

- [ ] Step 0-1-2-3 的执行顺序严格遵守
- [ ] 买入时检查资金是否充足
- [ ] 买入/卖出支持"一网打尽"（循环处理）
- [ ] 正确排序（买入从高到低，卖出从低到高）
- [ ] 每次状态变更生成 TradeRecord
- [ ] 维护 availableCash、position、realizedProfit
- [ ] 实现风控 STOP 逻辑
- [ ] STOPPED 状态下允许卖出但不允许买入
- [ ] 单次调用不会买卖同一网格两次
- [ ] 代码可重复调用（幂等性）

---

## 八、测试场景建议

### 场景 1：正常买入
- 价格下跌触发买入
- 验证资金扣减、持仓增加

### 场景 2：正常卖出
- 价格上涨触发卖出
- 验证资金增加、持仓减少、收益计算

### 场景 3：一网打尽（买入）
- 价格暴跌触发多个买入网格
- 验证按 buyPrice 从高到低顺序执行

### 场景 4：一网打尽（卖出）
- 价格暴涨触发多个卖出网格
- 验证按 sellPrice 从低到高顺序执行

### 场景 5：资金不足
- 触发买入但资金不足
- 验证不生成 TradeRecord

### 场景 6：风控 STOP（价格跌破）
- 价格跌破最低网格
- 验证策略变为 STOPPED

### 场景 7：风控 STOP（资金耗尽）
- 已投入资金达到 maxCapital
- 验证策略变为 STOPPED

### 场景 8：STOPPED 状态下卖出
- 策略已 STOPPED 但价格上涨
- 验证仍可卖出平仓

### 场景 9：幂等性测试
- 相同价格多次调用
- 验证不会重复触发

---

## 九、版本历史

| 版本 | 日期       | 变更说明                        |
|------|------------|---------------------------------|
| 1.0  | 2026-01-15 | 初版，基础买卖逻辑              |
| 2.0  | 2026-02-01 | 新增风控 STOP、一网打尽、幂等性 |

---

## 十、AI 使用建议

### 推荐提示词模板

```
请根据《网格执行引擎 2.0 规格说明》实现 GridEngine 类：

1. 创建 com.gridtrading.service.GridEngine
2. 实现核心方法：executeTick(Strategy strategy, BigDecimal price)
3. 严格遵循 Step 0-1-2-3 的执行顺序
4. 支持"一网打尽"
5. 实现风控 STOP 逻辑
6. 代码清晰，添加详细注释
```

### 分步实现建议

1. **第一步**：实现 Step 0 和 Step 1（买入）
2. **第二步**：实现 Step 2（卖出）
3. **第三步**：实现 Step 3（风控）
4. **第四步**：编写单元测试
5. **第五步**：集成到 Service 层

---

**📌 本规格说明是网格交易系统的核心设计文档，请妥善保存并在开发时严格遵循。**
