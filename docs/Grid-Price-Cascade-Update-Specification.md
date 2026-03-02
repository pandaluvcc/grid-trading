# 网格价格级联更新改造方案

## 1. 背景与问题

### 1.1 当前问题
1. 网格计划中存在「建议价格」和「真实价格」混用的情况
2. 当通过手动录入或导入真实交易记录时，只更新了 `actualBuyPrice`，但没有同步更新 `buyPrice`
3. 同一买入价位的小网、中网、大网没有正确级联更新
4. 卖出价计算逻辑不够清晰，需要区分场景处理

### 1.2 核心需求
- 区分「建议价格」和「真实价格」
- 真实交易后，价格需要级联更新到后续网格
- 保护已交易网格的真实价格不被覆盖

---

## 2. 字段设计

### 2.1 现有字段
| 字段 | 说明 |
|------|------|
| `buyPrice` | 买入价（建议/计划价格） |
| `sellPrice` | 卖出价（建议/计划价格） |
| `actualBuyPrice` | 真实买入价 |
| `actualSellPrice` | 真实卖出价 |
| `buyTriggerPrice` | 买入触发价 |
| `sellTriggerPrice` | 卖出触发价 |

### 2.2 字段使用规范

| 场景 | buyPrice | actualBuyPrice | 说明 |
|------|----------|----------------|------|
| 初始生成 | 计算值 | null | 尚未交易 |
| 真实交易后 | 更新为真实价格 | 真实价格 | 两者保持一致 |
| 后续网格重算 | 基于上一网真实价格计算 | null | 级联更新 |

**关键原则**：
- `actualBuyPrice` 不为 null 时，表示该网格已发生真实交易
- 已交易网格的 `buyPrice` 和 `actualBuyPrice` 应保持一致
- 级联更新时，跳过已交易的网格（`actualBuyPrice` 不为 null）

---

## 3. 卖出价计算规则

### 3.1 小网（SMALL）卖出价规则

**规则**：小网的卖出价 = 上一个小网的买入价

| 情况 | 卖出价计算方式 | 示例 |
|------|---------------|------|
| 上一小网已交易 | 上一小网的 `actualBuyPrice` | 第8网卖出价 = 第7网真实买入价 1.324 |
| 上一小网未交易 | 上一小网的 `buyPrice` | 计划价格级联 |
| 当前网已交易但上一网未交易 | `actualBuyPrice * 1.05` | 保证5%利润 |

**特殊处理**：
- 第1网（level=1）：卖出价 = 买入价 × 1.05
- 如果当前网已交易（有真实买入价），且计算出的卖出价低于 `actualBuyPrice * 1.05`，则使用 `actualBuyPrice * 1.05` 保底

### 3.2 中网（MEDIUM）卖出价规则

**规则**：中网的卖出价 = 向上跳3个小网的买入价

```
第5网(中网) 卖出价 = 第1网 买入价 = 1.710
第9网(中网) 卖出价 = 第5网 买入价 = 1.466（中网买入价）
第14网(中网) 卖出价 = 第9网 买入价
```

### 3.3 大网（LARGE）卖出价规则

**规则**：大网的卖出价 = 向上跳更多网格（约9个小网级别）的买入价

```
第10网(大网) 卖出价 = 第1网 买入价 = 1.710
第19网(大网) 卖出价 = 第9网 买入价
```

---

## 4. 级联更新逻辑

### 4.1 触发时机
当执行以下操作时触发级联更新：
1. 手动录入交易（tick 接口）
2. OCR 导入交易记录
3. 修改交易记录价格

### 4.2 更新流程

```
1. 确定当前交易的网格 (currentGrid)
2. 更新当前网格:
   - buyPrice = actualBuyPrice = 真实交易价格
   - 如果是买入: actualBuyPrice = price
   - 如果是卖出: actualSellPrice = price

3. 级联更新后续网格:
   FOR each grid WHERE level > currentGrid.level:
       IF grid.actualBuyPrice IS NOT NULL:
           SKIP (已交易，不覆盖)
       ELSE:
           重新计算 buyPrice 基于上一网的有效买入价
           重新计算 sellPrice 基于网格类型规则
           
4. 同级网格联动（小网、中网、大网同买入价位）:
   IF 当前是小网:
       同时更新同 level+1 的中网和 level+2 的大网的 buyPrice
```

### 4.3 伪代码

```java
public void cascadeUpdateGridPrices(Strategy strategy, GridLine updatedGrid, BigDecimal actualPrice) {
    List<GridLine> allGrids = strategy.getGridLines();
    
    // 1. 更新当前网格
    updatedGrid.setBuyPrice(actualPrice);
    updatedGrid.setActualBuyPrice(actualPrice);
    
    // 2. 找到需要同步更新买入价的中网/大网
    updateSameLevelMediumAndLargeGrids(allGrids, updatedGrid, actualPrice);
    
    // 3. 级联更新后续网格
    for (GridLine grid : allGrids) {
        if (grid.getLevel() <= updatedGrid.getLevel()) {
            continue; // 跳过当前及之前的网格
        }
        
        if (grid.getActualBuyPrice() != null) {
            continue; // 已交易，保护真实价格
        }
        
        // 获取上一个有效网格的买入价
        BigDecimal prevBuyPrice = getPreviousEffectiveBuyPrice(allGrids, grid);
        
        // 计算新的买入价
        BigDecimal newBuyPrice = calculateBuyPrice(prevBuyPrice, grid.getGridType());
        grid.setBuyPrice(newBuyPrice);
        
        // 计算新的卖出价
        BigDecimal newSellPrice = calculateSellPrice(allGrids, grid);
        grid.setSellPrice(newSellPrice);
    }
}
```

---

## 5. 具体场景示例

### 场景1：第8网录入真实买入价 1.275

**操作前**：
| 网格 | 类型 | buyPrice | actualBuyPrice |
|------|------|----------|----------------|
| 8 | SMALL | 1.258 | null |
| 9 | MEDIUM | 1.258 | null |
| 10 | LARGE | 1.258 | null |
| 11 | SMALL | 1.195 | null |

**操作后**：
| 网格 | 类型 | buyPrice | actualBuyPrice | sellPrice |
|------|------|----------|----------------|-----------|
| 8 | SMALL | 1.275 | 1.275 | 1.324（第7网买入价） |
| 9 | MEDIUM | 1.275 | null | 1.466（第5网买入价） |
| 10 | LARGE | 1.275 | null | 1.710（第1网买入价） |
| 11 | SMALL | 1.211 | null | 1.275（第8网买入价） |

### 场景2：已有交易记录的网格不被覆盖

**假设**：第7网已交易（actualBuyPrice = 1.324），第8网录入 1.275

**结果**：
- 第7网保持不变（已交易保护）
- 第8网及之后按新价格计算

---

## 6. 价格精度规范

### 6.1 小数位数
- 所有价格字段：保留3位小数
- 数量字段：保留8位小数（原有设计）
- 金额字段：保留2位小数

### 6.2 舍入规则
| 字段 | 舍入方式 | 原因 |
|------|----------|------|
| 买入价 | 向下截取（FLOOR） | 降低买入成本 |
| 卖出价 | 四舍五入（HALF_UP） | 争取更多卖出收益 |
| 触发价 | 四舍五入（HALF_UP） | 通用处理 |

### 6.3 实现方式
```java
// 买入价向下截取
buyPrice = buyPrice.setScale(3, RoundingMode.FLOOR);

// 卖出价四舍五入
sellPrice = sellPrice.setScale(3, RoundingMode.HALF_UP);
```

---

## 7. 实施步骤

### Phase 1: 核心逻辑修改
1. [ ] 修改 `GridEngine.processTick()` 方法，加入级联更新逻辑
2. [ ] 新增 `cascadeUpdateGridPrices()` 方法
3. [ ] 修改价格计算精度处理

### Phase 2: 卖出价计算优化
1. [ ] 实现小网卖出价 = 上一小网买入价的逻辑
2. [ ] 实现中网、大网卖出价查找目标网格逻辑
3. [ ] 处理边界情况（第1网、已交易网格等）

### Phase 3: 测试验证
1. [ ] 单元测试：级联更新逻辑
2. [ ] 集成测试：完整交易流程
3. [ ] 边界测试：第1网、最后一网、中间插入等场景

---

## 8. 注意事项

1. **已交易保护**：`actualBuyPrice` 不为 null 的网格不能被级联更新覆盖
2. **同级联动**：小网更新时要同步更新同买入价位的中网和大网
3. **卖出价兜底**：确保卖出价至少比买入价高出对应比例（小网5%、中网15%、大网30%）
4. **触发价更新**：买入价/卖出价更新后，对应的触发价也需要重新计算

---

## 9. 附录：网格类型与利润率

| 网格类型 | 利润率 | 卖出价计算 |
|----------|--------|-----------|
| SMALL | 5% | 上一小网买入价 |
| MEDIUM | 15% | 向上跳3个小网级别的买入价 |
| LARGE | 30% | 向上跳约9个小网级别的买入价 |

**文档版本**：v1.0  
**创建日期**：2026-03-03  
**作者**：GitHub Copilot

