# 🎯 网格引擎 2.0 · AI 快速参考卡

> **速查版本** - 适合直接喂给 AI 编码助手

---

## 核心算法（伪代码）

```java
void executeTick(Strategy strategy, BigDecimal price) {
    // Step 0: 更新价格
    strategy.lastPrice = price;
    
    // Step 1: 买入处理（仅 RUNNING）
    if (strategy.status == RUNNING) {
        List<GridLine> buyLines = findWaitBuyLines(strategy)
            .filter(line -> price <= line.buyPrice)
            .filter(line -> strategy.availableCash >= strategy.amountPerGrid)
            .sortByBuyPriceDesc();  // 从高到低
        
        for (GridLine line : buyLines) {
            if (strategy.availableCash < strategy.amountPerGrid) break;
            
            // 执行买入
            BigDecimal quantity = strategy.amountPerGrid / line.buyPrice;
            createTradeRecord(BUY, line.buyPrice, quantity);
            
            line.state = WAIT_SELL;
            strategy.availableCash -= strategy.amountPerGrid;
            strategy.investedAmount += strategy.amountPerGrid;
            strategy.position += quantity;
        }
    }
    
    // Step 2: 卖出处理（RUNNING / STOPPED 都可以）
    List<GridLine> sellLines = findWaitSellLines(strategy)
        .filter(line -> price >= line.sellPrice)
        .sortBySellPriceAsc();  // 从低到高
    
    for (GridLine line : sellLines) {
        // 执行卖出
        BigDecimal quantity = strategy.amountPerGrid / line.buyPrice;
        BigDecimal sellAmount = quantity * line.sellPrice;
        BigDecimal profit = sellAmount - strategy.amountPerGrid;
        
        createTradeRecord(SELL, line.sellPrice, quantity);
        
        line.state = WAIT_BUY;
        strategy.availableCash += sellAmount;
        strategy.position -= quantity;
        strategy.realizedProfit += profit;
    }
    
    // Step 3: 风控 STOP
    BigDecimal lowestBuyPrice = findLowestBuyPrice(strategy);
    if (price <= lowestBuyPrice || strategy.investedAmount >= strategy.maxCapital) {
        strategy.status = STOPPED;
    }
}
```

---

## 关键规则（必须遵守）

| 规则 | 说明 |
|------|------|
| ✅ **顺序执行** | Step 0 → 1 → 2 → 3，不可颠倒 |
| ✅ **买入排序** | 按 buyPrice **从高到低**（靠近当前价先买） |
| ✅ **卖出排序** | 按 sellPrice **从低到高**（靠近当前价先卖） |
| ✅ **资金检查** | 每次买入前检查 `availableCash >= amountPerGrid` |
| ✅ **一网打尽** | 允许一次触发多个网格（循环处理） |
| ✅ **状态互斥** | 单次调用中，同一网格不能既买又卖 |
| ✅ **STOP 限制** | STOPPED 状态：不允许买入，允许卖出 |
| ✅ **幂等性** | 相同价格多次调用，不会重复触发 |

---

## 状态转换图

```
买入：WAIT_BUY  →  WAIT_SELL
卖出：WAIT_SELL  →  WAIT_BUY

策略：RUNNING  →  STOPPED（触发风控）
```

---

## 必要字段清单

### Strategy 实体

```java
// 配置字段
Long id;
String name;
String symbol;
BigDecimal basePrice;
BigDecimal gridPercent;
Integer gridCountDown;
Integer gridCountUp;
BigDecimal amountPerGrid;
BigDecimal maxCapital;
StrategyStatus status;
LocalDateTime createdAt;

// 运行时字段（必须添加）
BigDecimal lastPrice;        // 最新价格
BigDecimal availableCash;    // 可用资金
BigDecimal investedAmount;   // 已投入
BigDecimal position;         // 持仓数量
BigDecimal realizedProfit;   // 已实现收益
```

### GridLine 实体

```java
Long id;
Strategy strategy;
BigDecimal buyPrice;
BigDecimal sellPrice;
GridLineState state;
Integer level;
```

### TradeRecord 实体

```java
Long id;
Strategy strategy;
GridLine gridLine;
TradeType type;
BigDecimal price;
BigDecimal amount;
BigDecimal quantity;
LocalDateTime tradeTime;
```

---

## 关键查询方法

```java
// Repository 接口示例
public interface GridLineRepository extends JpaRepository<GridLine, Long> {
    
    // 查询等待买入的网格（按价格从高到低）
    List<GridLine> findByStrategyAndStateOrderByBuyPriceDesc(
        Strategy strategy, 
        GridLineState state
    );
    
    // 查询等待卖出的网格（按价格从低到高）
    List<GridLine> findByStrategyAndStateOrderBySellPriceAsc(
        Strategy strategy, 
        GridLineState state
    );
    
    // 查询最低买入网格
    @Query("SELECT MIN(g.buyPrice) FROM GridLine g WHERE g.strategy = :strategy")
    BigDecimal findLowestBuyPrice(@Param("strategy") Strategy strategy);
}
```

---

## 计算公式

### 买入

```
成交数量 = 每格金额 ÷ 买入价格
quantity = amountPerGrid / buyPrice

资金变化：
availableCash   -= amountPerGrid
investedAmount  += amountPerGrid
position        += quantity
```

### 卖出

```
成交金额 = 成交数量 × 卖出价格
sellAmount = quantity * sellPrice

收益 = 成交金额 - 买入金额
profit = sellAmount - amountPerGrid

资金变化：
availableCash   += sellAmount
position        -= quantity
realizedProfit  += profit
```

### 风控触发

```
条件 1：price <= MIN(buyPrice)
条件 2：investedAmount >= maxCapital

满足任一 → status = STOPPED
```

---

## 测试用例模板

```java
@Test
void testBuyTrigger() {
    // Given: 策略配置，网格线 WAIT_BUY
    Strategy strategy = createStrategy(status=RUNNING, cash=1000);
    GridLine line = createGridLine(buyPrice=100, state=WAIT_BUY);
    
    // When: 价格触发买入
    engine.executeTick(strategy, new BigDecimal("99"));
    
    // Then: 验证结果
    assertEquals(WAIT_SELL, line.getState());
    assertTrue(strategy.getAvailableCash() < 1000);
    assertTrue(strategy.getPosition() > 0);
    assertEquals(1, tradeRecordRepository.count());
}
```

---

## 常见错误 ❌

| 错误 | 后果 |
|------|------|
| 买入时未检查资金 | 资金为负 |
| 排序错误 | 不符合"靠近当前价先执行" |
| 卖出时 STOPPED 也禁止 | 无法止盈 |
| 同一网格既买又卖 | 状态混乱 |
| 未实现幂等性 | 重复触发交易 |

---

## AI 提示词模板

### 实现引擎核心

```
请实现 GridEngine.executeTick() 方法：

1. 输入：Strategy（含 GridLine 集合）、BigDecimal price
2. 执行顺序：Step 0 → 1 → 2 → 3
3. 买入：RUNNING 状态，price <= buyPrice，从高到低
4. 卖出：任何状态，price >= sellPrice，从低到高
5. 风控：price <= 最低网格 或 资金耗尽 → STOPPED
6. 每次交易生成 TradeRecord，更新资金/持仓
7. 支持一次触发多个网格
8. 代码添加详细注释
```

### 创建 Repository

```
请创建 GridLineRepository 接口：

1. 继承 JpaRepository<GridLine, Long>
2. 添加查询方法：
   - 按策略和状态查询（等待买入）
   - 按策略和状态查询（等待卖出）
   - 排序：买入从高到低，卖出从低到高
3. 添加查询最低买入网格价格的方法
```

### 编写测试

```
请为 GridEngine 编写单元测试：

1. 测试正常买入触发
2. 测试正常卖出触发
3. 测试一网打尽（买入多个）
4. 测试资金不足
5. 测试风控 STOP
6. 测试 STOPPED 后仍可卖出
7. 使用 Mockito 模拟依赖
8. 验证状态变更和资金计算
```

---

## 快速调试检查清单

运行后检查：

- [ ] 买入后，GridLine.state 变为 WAIT_SELL
- [ ] 卖出后，GridLine.state 变为 WAIT_BUY
- [ ] availableCash 减少 = 买入金额
- [ ] availableCash 增加 = 卖出金额
- [ ] position 增减正确
- [ ] TradeRecord 记录完整
- [ ] 价格跌破或资金耗尽时，status 变为 STOPPED
- [ ] STOPPED 后不再买入，但仍可卖出

---

**📌 保存此文档，随时喂给 AI 快速实现！**
