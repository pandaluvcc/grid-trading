# 网格交易系统认知说明文档 v2.0

> **文档性质**：事实说明文档（非设计提案）  
> **目标读者**：第三方系统设计者  
> **内容保证**：所有描述均基于当前代码实现，不含假设性内容  
> **文档日期**：2026年2月9日

---

## 一、系统总体说明

这是一个**固定模板网格交易辅助系统**，用于帮助用户在特定价格区间内反复低买高卖，赚取差价收益。

**核心流程：**
1. 用户创建策略时设定基准价格（basePrice）和每格投入金额（amountPerGrid）
2. 系统自动生成固定的 19 条网格计划，每条网格包含明确的买入价和卖出价
3. 用户通过手动或外部系统触发价格更新（Tick），系统判断是否触发买入或卖出
4. 触发买入后，网格进入"已买入"状态，等待价格上涨到卖出价
5. 触发卖出后，网格回到"等待买入"状态，形成循环交易
6. 系统记录每笔交易，自动计算已实现收益、持仓数量、可用资金

**解决的真实问题：**
- 用户不需要盯盘，系统按预设价格自动记录交易机会
- 通过固定的 19 条网格分散风险，避免单笔过重
- 网格分为小网（5%利润）、中网（15%利润）、大网（30%利润），小跌小赚、大跌大赚
- 系统不执行实际下单，只提供交易决策和记录管理

**明确边界：**
- 系统不连接交易所，不自动下单
- 系统不监听实时行情，需要外部调用 Tick 接口更新价格
- 网格数量和类型分布是固定的，用户不能自定义

---

## 二、核心概念与对象定义

### 1. 策略（Strategy）

**真实含义：**  
策略是一个完整的网格交易方案，包含 19 条网格计划和所有资金账本数据。

**职责：**
- 持有基准价格（basePrice）和每格投入金额（amountPerGrid）
- 管理 19 条网格线（GridLine）
- 维护资金账本：可用资金、已投入资金、当前持仓、已实现收益
- 记录策略状态（RUNNING 或 STOPPED）

**数据性质：**
- 策略本身是"配置数据"
- 其中的资金字段（availableCash、investedAmount、position、realizedProfit）是"事实数据"，由引擎自动更新

**可修改性：**
- 创建后，basePrice 和 amountPerGrid 可通过"计划阶段编辑"间接修改
- 策略进入 RUNNING 状态后开始执行，进入 STOPPED 状态后停止买入但允许卖出
- 策略不能被删除，只能停止

---

### 2. 网格计划表（Grid Plan）

**真实含义：**  
网格计划表是系统生成的 19 条买卖计划，每条计划规定了"在什么价格买入、在什么价格卖出、赚多少钱"。

**职责：**
- 提供完整的买卖价格方案
- 展示预期收益（每条网格完成一次循环的利润）

**数据性质：**
- 网格计划是"计划数据"，在策略创建时生成
- 计划可在"等待买入"状态下被编辑，编辑后会重新计算后续网格

**可修改性：**
- 仅允许修改处于 WAIT_BUY 状态的网格的买入价
- 修改后，系统自动重新计算从该网格开始的所有后续网格的价格和收益

---

### 3. 网格行（Single Grid / GridLine）

**真实含义：**  
网格行是 19 条计划中的一条，代表"一个价格档位的买卖机会"。

**职责：**
- 记录买入价（buyPrice）、卖出价（sellPrice）
- 记录实际成交价（actualBuyPrice、actualSellPrice）
- 维护状态（WAIT_BUY、BOUGHT、SOLD）
- 存储买入数量、卖出金额、利润等计算结果

**数据性质：**
- 价格字段（buyPrice、sellPrice）是"计划数据"
- 状态字段（state）和实际成交价（actualBuyPrice、actualSellPrice）是"事实数据"

**可修改性：**
- WAIT_BUY 状态：允许修改 buyPrice
- BOUGHT 状态：允许修改 actualBuyPrice（用于纠正实际成交价）
- SOLD 状态：不存在（卖出后立即回到 WAIT_BUY）

**锁定规则：**
- 网格进入 BOUGHT 状态后，buyPrice 不能再修改（已成交）
- 但 actualBuyPrice 可修改，用于校正真实成交价，并重新计算后续网格

---

### 4. 网格类型（小网 / 中网 / 大网）

**真实含义：**  
网格类型定义了该网格的"盈利目标"。

**分类标准：**
- **小网（SMALL）**：卖出价 = 买入价 + 5%，高频小利
- **中网（MEDIUM）**：卖出价 = 买入价 + 15%，中频中利
- **大网（LARGE）**：卖出价 = 买入价 + 30%，低频大利

**固定分布（19 条）：**
```
Level 1-4:   小网（4条）
Level 5:     中网（1条）
Level 6-8:   小网（3条）
Level 9:     中网（1条）
Level 10:    大网（1条）
Level 11-13: 小网（3条）
Level 14:    中网（1条）
Level 15-17: 小网（3条）
Level 18:    中网（1条）
Level 19:    大网（1条）
```
**总计：** 小网 13 条 / 中网 4 条 / 大网 2 条

**数据性质：**  
网格类型是"配置数据"，创建后不可更改。

---

### 5. 成交记录（TradeRecord / Transaction）

**真实含义：**  
成交记录是一次真实的买入或卖出操作的日志。

**职责：**
- 记录交易类型（BUY / SELL）
- 记录成交价格、成交数量、成交金额
- 记录成交时间
- 绑定到对应的网格行（gridLineId）

**数据性质：**
- 成交记录是"事实数据"，一旦生成不可修改
- 每次买入或卖出触发时，系统生成一条记录

**绑定规则：**
- 每条成交记录必须绑定到一个网格行
- 一个网格行可以有多条成交记录（一次买入记录 + 一次卖出记录 + 下一次买入记录...）
- 系统不支持一笔成交不绑定网格

---

### 6. 当前状态（GridLineState）

**状态枚举：**
- **WAIT_BUY（等待买入）**：网格尚未触发，或已卖出后回到等待
- **BOUGHT（已买入）**：买入已触发，等待价格上涨到卖出价
- **SOLD（已卖出）**：临时状态，卖出后立即转为 WAIT_BUY
- **WAIT_SELL（已废弃）**：兼容旧版本，实际上等同于 BOUGHT

**状态机：**
```
WAIT_BUY → BOUGHT → SOLD → WAIT_BUY（循环）
```

**锁定规则：**
- WAIT_BUY：允许修改 buyPrice
- BOUGHT：不允许修改 buyPrice，但允许修改 actualBuyPrice
- SOLD：不存在持久状态（立即转为 WAIT_BUY）

---

## 三、网格生成与计算规则

### 1. 网格总数固定为 19 条

**原因：**  
系统采用固定模板设计，用户不能自定义网格数量。19 条是经过权衡的最优配置：
- 足够分散风险（不会单笔过重）
- 资金占用可控（19 × 每格金额 = 最大投入）
- 覆盖合理的价格区间（从 basePrice 向下约 45%-50%）

---

### 2. 小网 / 中网 / 大网的数量分布

**固定规则：**
- **小网：13 条**（Level 1-4, 6-8, 11-13, 15-17）
- **中网：4 条**（Level 5, 9, 14, 18）
- **大网：2 条**（Level 10, 19）

**分布逻辑：**
- 小网密集分布，确保高频交易
- 中网均匀穿插，提供中等收益机会
- 大网放在深度回撤位置（Level 10 和 19），捕捉极端行情

---

### 3. 每一类网格的价格计算规则

#### **基础概念：**
- **basePrice（基准价格）**：用户设定的初始价格，作为网格计算的锚点
- **smallStep = basePrice × 5%**：小网的价格步长
- **mediumStep = basePrice × 15%**：中网的价格步长
- **largeStep = basePrice × 30%**：大网的价格步长

---

#### **买入价计算规则（差值递减）：**

**核心原则：** 禁止使用乘法，必须使用差值递减。

**小网买入价：**
```
Level 1:  buyPrice = basePrice
Level 2:  buyPrice = Level 1.buyPrice - smallStep
Level 3:  buyPrice = Level 2.buyPrice - smallStep
...以此类推
```

**中网买入价：**
```
中网的 buyPrice = 当前最新小网.buyPrice（继承，不再递减）
```
**示例：**
- Level 5（第1个中网）的 buyPrice = Level 4（最新小网）.buyPrice

**大网买入价：**
```
大网的 buyPrice = 当前最新小网.buyPrice（继承，不再递减）
```

---

#### **卖出价计算规则（锚点回撤）：**

**小网卖出价：**
```
Level 1:  sellPrice = basePrice + smallStep
Level 2+: sellPrice = 上一条小网.buyPrice（阶梯回撤）
```

**中网卖出价（锚点反弹）：**
```
Level 5（第1个中网）： sellPrice = basePrice
Level 9+（后续中网）：  sellPrice = 上一个中网.buyPrice
```

**大网卖出价（特殊锚点）：**
```
Level 10（第1个大网）： sellPrice = basePrice
Level 19（第2个大网）： sellPrice = Level 9（第2个中网）.buyPrice
```

---

#### **触发价计算规则：**

系统引入了 `buyTriggerPrice` 和 `sellTriggerPrice`，用于实际触发判断：

```
buyTriggerPrice  = buyPrice + 0.02
sellTriggerPrice = sellPrice - 0.02
```

**注意：** 当前引擎实际判断时使用的是 `buyPrice` 和 `sellPrice`，触发价字段仅作为数据库存储，未在引擎中生效。

---

### 4. 完整计算示例

假设 `basePrice = 100`，`amountPerGrid = 1000`：

| Level | 类型   | buyPrice | sellPrice | 计算说明                              |
|-------|--------|----------|-----------|---------------------------------------|
| 1     | SMALL  | 100.00   | 105.00    | base, base + 5                        |
| 2     | SMALL  | 95.00    | 100.00    | 100 - 5, 上一小网 buy                |
| 3     | SMALL  | 90.00    | 95.00     | 95 - 5, 上一小网 buy                 |
| 4     | SMALL  | 85.00    | 90.00     | 90 - 5, 上一小网 buy                 |
| 5     | MEDIUM | 85.00    | 100.00    | 继承小网, 锚点 base                   |
| 6     | SMALL  | 80.00    | 85.00     | 85 - 5, 上一小网 buy                 |
| ...   | ...    | ...      | ...       | ...                                   |
| 10    | LARGE  | 57.50    | 100.00    | 继承小网, 锚点 base                   |
| ...   | ...    | ...      | ...       | ...                                   |
| 19    | LARGE  | 22.50    | 60.00     | 继承小网, 锚点 Level 9.buyPrice       |

---

## 四、成交记录与网格的绑定关系

### 1. 成交记录如何绑定到网格

**绑定时机：**
- 买入触发时：系统生成一条 `TradeRecord`，`type=BUY`，`gridLineId=触发的网格ID`
- 卖出触发时：系统生成一条 `TradeRecord`，`type=SELL`，`gridLineId=触发的网格ID`

**绑定字段：**
- `TradeRecord.gridLine`：外键，指向触发的 `GridLine`
- `TradeRecord.strategy`：外键，指向所属策略

---

### 2. 是否允许一笔成交不绑定网格

**不允许。**

系统强制要求每笔成交必须绑定到一个网格。如果不绑定网格，系统将无法：
- 更新网格状态
- 计算后续网格价格
- 判断是否允许继续买入

---

### 3. 成交发生后的影响范围

#### **买入触发后：**

**步骤1：** 生成买入交易记录
```java
TradeRecord.type = BUY
TradeRecord.price = gridLine.buyPrice
TradeRecord.quantity = amountPerGrid / buyPrice
TradeRecord.amount = amountPerGrid
```

**步骤2：** 更新当前网格状态
```java
gridLine.state = WAIT_BUY → BOUGHT
```

**步骤3：** 更新策略资金账本
```java
strategy.availableCash -= amountPerGrid
strategy.investedAmount += amountPerGrid
strategy.position += quantity
```

**步骤4：** 对后续网格的影响
- **无影响**。买入不会重新计算后续网格价格。

**步骤5：** 对上方已完成网格的影响
- **无影响**。买入不会修改已卖出的网格。

---

#### **卖出触发后：**

**步骤1：** 生成卖出交易记录
```java
TradeRecord.type = SELL
TradeRecord.price = gridLine.sellPrice
TradeRecord.quantity = buyQuantity（买入时的数量）
TradeRecord.amount = quantity × sellPrice
```

**步骤2：** 更新当前网格状态
```java
gridLine.state = BOUGHT → WAIT_BUY（循环复位）
```

**步骤3：** 更新策略资金账本
```java
sellAmount = quantity × sellPrice
profit = sellAmount - amountPerGrid

strategy.availableCash += sellAmount
strategy.investedAmount -= amountPerGrid
strategy.position -= quantity
strategy.realizedProfit += profit
```

**步骤4：** 对后续网格的影响
- **无影响**。卖出不会重新计算后续网格价格。

**步骤5：** 对上方已完成网格的影响
- **无影响**。卖出后网格复位为 WAIT_BUY，等待下一次触发。

---

#### **特殊情况：修改 actualBuyPrice 后**

如果用户修改了某个已买入网格的 `actualBuyPrice`（纠正实际成交价），系统会：

**步骤1：** 更新该网格的 actualBuyPrice
```java
gridLine.actualBuyPrice = newPrice
```

**步骤2：** 重新计算后续所有 WAIT_BUY 状态的网格
- 使用新的 actualBuyPrice 作为基准
- 重新计算 buyPrice、sellPrice、profit、profitRate

**步骤3：** 跳过已成交网格
- 已经处于 BOUGHT 状态的网格不受影响

---

## 五、状态流转规则（状态机视角）

### 1. 完整状态流转图

```
创建策略
    ↓
[19条网格初始化] → WAIT_BUY
    ↓
价格下跌，触发买入
    ↓
WAIT_BUY → BOUGHT
    ↓
价格上涨，触发卖出
    ↓
BOUGHT → WAIT_BUY（循环）
```

---

### 2. 每次状态变化的触发条件

| 当前状态   | 触发事件              | 触发条件                                        | 下一状态   |
|------------|----------------------|-------------------------------------------------|------------|
| WAIT_BUY   | 价格更新（Tick）     | price ≤ buyPrice <br> + 策略状态 = RUNNING <br> + 可用资金充足 <br> + 已买入数量 < 19 | BOUGHT     |
| BOUGHT     | 价格更新（Tick）     | price ≥ sellPrice                               | WAIT_BUY   |
| WAIT_BUY   | 用户修改计划买入价   | 用户调用 updatePlanBuyPrice API                 | WAIT_BUY   |
| BOUGHT     | 用户修改实际买入价   | 用户调用 updateActualBuyPrice API               | BOUGHT     |

---

### 3. 各状态下的编辑权限

| 状态       | 允许修改 buyPrice | 允许修改 actualBuyPrice | 允许删除 | 允许触发买入 | 允许触发卖出 |
|------------|-------------------|-------------------------|----------|--------------|--------------|
| WAIT_BUY   | ✅ 是             | ❌ 否（尚未买入）       | ❌ 否    | ✅ 是        | ❌ 否        |
| BOUGHT     | ❌ 否（已成交）   | ✅ 是（纠正实际价格）   | ❌ 否    | ❌ 否        | ✅ 是        |

---

## 六、前端与后端的职责边界

### 1. 只能由后端计算的数据

**网格价格（buyPrice、sellPrice）：**
- 前端不允许自行计算网格价格
- 前端只能调用 `updatePlanBuyPrice` API，由后端重新计算

**网格状态（state）：**
- 前端不允许修改网格状态
- 状态由后端在 Tick 触发时自动更新

**资金账本（availableCash、investedAmount、position、realizedProfit）：**
- 前端不允许修改这些字段
- 由后端在买入/卖出时自动更新

**交易记录（TradeRecord）：**
- 前端不允许创建或删除交易记录
- 由后端在触发买入/卖出时自动生成

---

### 2. 只由前端展示或临时计算的数据

**浮动盈亏（unrealizedProfit）：**
- 前端可根据当前价格和持仓计算浮动盈亏
- 公式：`(currentPrice - 平均成本价) × position`
- 后端不存储此字段

**网格触发进度：**
- 前端可展示"已触发 X / 19"
- 后端不存储此统计值

**预计总收益：**
- 前端可累加所有网格的 profit 字段
- 后端不存储此汇总值

---

### 3. 前端是否允许修改网格价格

**允许，但有条件限制：**

**场景1：计划阶段修改（WAIT_BUY 状态）**
- 前端调用 `PUT /api/strategies/grid-lines/{gridLineId}/update-plan-buy-price`
- 传入新的 buyPrice
- 后端自动重新计算该网格及所有后续网格

**场景2：实际成交价纠正（BOUGHT 状态）**
- 前端调用 `PUT /api/strategies/grid-lines/actual-buy-price`
- 传入新的 actualBuyPrice
- 后端自动重新计算后续所有 WAIT_BUY 状态的网格

**禁止场景：**
- 不允许直接修改 BOUGHT 状态网格的 buyPrice（已成交价格不可更改）
- 不允许修改 sellPrice（卖出价由系统根据网格类型自动计算）

---

## 七、当前系统的明确边界（不做什么）

### 1. 不支持自动下单

系统不连接任何交易所或券商 API，不执行真实的买入/卖出操作。  
用户需要手动在交易平台下单，然后回到系统记录成交结果。

---

### 2. 不支持行情自动触发

系统不监听实时行情数据。  
用户需要手动调用 `POST /api/strategies/{id}/tick` 接口，传入当前价格，系统才会判断是否触发买卖。

---

### 3. 不支持多笔成交拆分

一个网格只能记录一次买入和一次卖出。  
如果实际成交被拆分成多笔（如部分成交），系统无法分别记录，用户需要合并成一笔输入。

---

### 4. 不支持同一网格多次成交

一个网格在同一轮循环中：
- 只能买入一次（WAIT_BUY → BOUGHT）
- 只能卖出一次（BOUGHT → WAIT_BUY）
- 卖出后可进入下一轮循环，但仍然是"单次买入 + 单次卖出"

---

### 5. 不支持自定义网格数量和分布

网格总数固定为 19 条，类型分布固定为"小网13/中网4/大网2"。  
用户不能修改网格数量，不能调整类型分布。

---

### 6. 不支持删除策略或网格

策略和网格一旦创建，不能被删除。  
策略只能停止（STOPPED），网格只能修改价格。

---

### 7. 不支持多标的

一个策略只能绑定一个交易标的（symbol）。  
用户需要创建多个策略分别管理不同标的。

---

### 8. 不支持动态调整每格金额

`amountPerGrid`（每格投入金额）在策略创建后不能修改。  
如果需要调整金额，用户需要创建新的策略。

---

### 9. 不支持止盈止损触发后自动清仓

策略进入 STOPPED 状态后：
- **停止买入**：不再触发新的买入操作
- **保留持仓**：已买入的持仓不会自动卖出
- **允许卖出**：价格上涨到卖出价时仍然会触发卖出

系统不提供"一键清仓"或"自动平仓"功能。

---

### 10. 不支持回测

系统只能处理实时价格更新，不支持导入历史数据进行回测。

---

## 附录：关键字段说明

### Strategy 关键字段

| 字段名           | 类型          | 说明                          | 谁更新     |
|------------------|---------------|-------------------------------|------------|
| basePrice        | BigDecimal    | 基准价格                      | 用户/系统  |
| amountPerGrid    | BigDecimal    | 每格投入金额                  | 用户       |
| availableCash    | BigDecimal    | 可用资金                      | 后端引擎   |
| investedAmount   | BigDecimal    | 已投入资金                    | 后端引擎   |
| position         | BigDecimal    | 当前持仓数量                  | 后端引擎   |
| realizedProfit   | BigDecimal    | 已实现收益                    | 后端引擎   |
| status           | StrategyStatus| 策略状态（RUNNING/STOPPED）   | 后端引擎   |

---

### GridLine 关键字段

| 字段名           | 类型          | 说明                          | 谁更新     |
|------------------|---------------|-------------------------------|------------|
| level            | Integer       | 网格层级（1-19）              | 系统生成   |
| gridType         | GridType      | 网格类型（SMALL/MEDIUM/LARGE）| 系统生成   |
| buyPrice         | BigDecimal    | 计划买入价                    | 系统计算   |
| sellPrice        | BigDecimal    | 计划卖出价                    | 系统计算   |
| actualBuyPrice   | BigDecimal    | 实际买入价（可为空）          | 用户修正   |
| actualSellPrice  | BigDecimal    | 实际卖出价（可为空）          | 后端引擎   |
| state            | GridLineState | 网格状态（WAIT_BUY/BOUGHT）   | 后端引擎   |
| buyQuantity      | BigDecimal    | 买入数量                      | 系统计算   |
| profit           | BigDecimal    | 预期利润                      | 系统计算   |

---

### TradeRecord 关键字段

| 字段名     | 类型          | 说明                   | 谁更新     |
|------------|---------------|------------------------|------------|
| type       | TradeType     | 交易类型（BUY/SELL）   | 后端引擎   |
| price      | BigDecimal    | 成交价格               | 后端引擎   |
| quantity   | BigDecimal    | 成交数量               | 后端引擎   |
| amount     | BigDecimal    | 成交金额               | 后端引擎   |
| tradeTime  | LocalDateTime | 成交时间               | 后端引擎   |
| gridLine   | GridLine      | 关联的网格行           | 后端引擎   |

---

## 附录：核心 API 接口说明

### 1. 创建策略
```
POST /api/strategies
Body: {
  "name": "策略名称",
  "symbol": "标的代码",
  "basePrice": 100.00,
  "amountPerGrid": 1000.00
}
```

### 2. 获取网格计划表
```
GET /api/strategies/{id}/grid-plans
```

### 3. 执行价格更新（Tick）
```
POST /api/strategies/{id}/tick
Body: {
  "price": 95.50
}
```

### 4. 修改计划买入价（WAIT_BUY 状态）
```
PUT /api/strategies/grid-lines/{gridLineId}/update-plan-buy-price
Params: newBuyPrice=90.00
```

### 5. 修改实际买入价（BOUGHT 状态）
```
PUT /api/strategies/grid-lines/actual-buy-price
Body: {
  "gridLineId": 123,
  "actualBuyPrice": 89.50
}
```

### 6. 获取策略详情
```
GET /api/strategies/{id}/detail
```

---

**文档结束**

---

**版本历史：**
- v2.0 (2026-02-09): 初始版本，基于当前代码实现完整分析
