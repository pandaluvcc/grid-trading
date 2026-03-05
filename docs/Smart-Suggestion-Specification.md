# 智能建议操作系统 - 产品需求规格说明书

> 版本：v1.0  
> 日期：2026-03-05  
> 用途：供 AI 编码参考，定义网格交易系统的智能建议操作功能

---

## 1. 功能概述

### 1.1 背景

当前网格交易系统的建议操作过于机械（如买了第1网就建议买第2网），缺乏对市场行情和仓位风险的判断。本文档定义一套智能建议系统，能够：

1. 基于**最新价格**判断应该买入还是卖出哪个网格
2. 在**短期密集买入**时，自动调整中网/大网的买入策略
3. 通过**仓位控制**防止过度买入
4. 管理**暂缓买入**的网格，并在合适时机提示补买

### 1.2 核心概念

| 概念 | 定义 |
|------|------|
| 最新价格（lastPrice） | 用户手动输入或通过交易自动更新的当前市场价格 |
| 短期密集买入 | 14 天内买入网格数 ≥ 3 网 |
| 暂缓买入 | 因风险控制而延迟买入的中网/大网 |
| 持仓比例 | 已投入资金 / 最大投入资金 × 100% |

---

## 2. 数据模型变更

### 2.1 Strategy 表新增/修改字段

```sql
ALTER TABLE strategy ADD COLUMN IF NOT EXISTS last_price DECIMAL(20,8) DEFAULT NULL COMMENT '最新价格';
ALTER TABLE strategy ADD COLUMN IF NOT EXISTS max_position_ratio DECIMAL(5,4) DEFAULT 0.8000 COMMENT '最大持仓比例，默认80%';
```

| 字段名 | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| last_price | DECIMAL(20,8) | NULL | 最新市场价格，用于计算建议操作 |
| max_position_ratio | DECIMAL(5,4) | 0.8000 | 最大持仓比例上限（0-1） |

### 2.2 GridLine 表新增字段

```sql
ALTER TABLE grid_line ADD COLUMN IF NOT EXISTS deferred BOOLEAN DEFAULT FALSE COMMENT '是否暂缓买入';
ALTER TABLE grid_line ADD COLUMN IF NOT EXISTS deferred_reason VARCHAR(100) DEFAULT NULL COMMENT '暂缓原因';
ALTER TABLE grid_line ADD COLUMN IF NOT EXISTS deferred_at DATETIME DEFAULT NULL COMMENT '暂缓时间';
```

| 字段名 | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| deferred | BOOLEAN | FALSE | 是否处于暂缓买入状态 |
| deferred_reason | VARCHAR(100) | NULL | 暂缓原因（如：DENSE_BUY、POSITION_LIMIT） |
| deferred_at | DATETIME | NULL | 暂缓发生的时间 |

### 2.3 新增 DeferredReason 枚举

```java
public enum DeferredReason {
    DENSE_BUY,       // 短期密集买入导致暂缓
    POSITION_LIMIT   // 持仓比例达到上限导致暂缓
}
```

---

## 3. 最新价格管理

### 3.1 自动更新规则

| 触发场景 | 更新逻辑 |
|---------|---------|
| 买入交易执行成功 | `strategy.lastPrice = tradeRecord.price` |
| 卖出交易执行成功 | `strategy.lastPrice = tradeRecord.price` |

### 3.2 手动更新 API

```
PUT /api/strategies/{id}/last-price
Content-Type: application/json

{
  "lastPrice": 1.217
}
```

**响应**：

```json
{
  "id": 1,
  "lastPrice": 1.217,
  "updatedAt": "2026-03-05T14:30:00"
}
```

### 3.3 前端交互

- 策略详情页显示"最新价格"字段
- 点击可弹窗编辑
- 保存后自动刷新建议操作

---

## 4. 智能建议操作算法

### 4.1 API 定义

```
GET /api/strategies/{id}/suggestion
```

**响应**：

```json
{
  "suggestionType": "BUY",
  "suggestions": [
    {
      "gridLineId": 9,
      "gridLevel": 9,
      "gridType": "MEDIUM",
      "action": "BUY",
      "price": 1.275,
      "quantity": 1000,
      "amount": 1275,
      "quantityRatio": 1.0,
      "reason": "中网跟随第8网买入"
    },
    {
      "gridLineId": 10,
      "gridLevel": 10,
      "gridType": "LARGE",
      "action": "BUY",
      "price": 1.275,
      "quantity": 500,
      "amount": 637.5,
      "quantityRatio": 0.5,
      "reason": "大网半仓买入（短期密集买入保护）"
    }
  ],
  "warnings": [
    {
      "type": "DENSE_BUY",
      "message": "过去14天内已买入4网，建议谨慎操作"
    },
    {
      "type": "HIGH_POSITION",
      "message": "当前持仓比例66.68%，接近上限80%"
    }
  ],
  "deferredGrids": [
    {
      "gridLineId": 10,
      "gridLevel": 10,
      "gridType": "LARGE",
      "deferredReason": "DENSE_BUY",
      "deferredAt": "2026-03-02T10:00:00",
      "canResume": true,
      "resumeCondition": "任意网格卖出后可补买"
    }
  ],
  "marketStatus": {
    "lastPrice": 1.217,
    "positionRatio": 0.6668,
    "boughtGridCount": 8,
    "totalGridCount": 19,
    "recentBuyCount": 4,
    "recentBuyDays": 14
  }
}
```

### 4.2 核心算法伪代码

```python
def calculate_suggestion(strategy_id):
    strategy = get_strategy(strategy_id)
    grid_lines = get_grid_lines(strategy_id)
    trade_records = get_trade_records(strategy_id)
    
    last_price = strategy.last_price
    if last_price is None:
        return {"error": "请先设置最新价格"}
    
    # 1. 计算市场状态
    position_ratio = calculate_position_ratio(strategy)
    recent_buy_count = count_recent_buys(trade_records, days=14)
    bought_grid_count = count_bought_grids(grid_lines)
    
    # 2. 检查仓位上限
    if position_ratio >= strategy.max_position_ratio:
        return {
            "suggestionType": "HOLD",
            "reason": "持仓比例已达上限，暂停买入",
            "warnings": [{"type": "POSITION_LIMIT", "message": "..."}]
        }
    
    # 3. 查找可卖出的网格（优先级最高）
    sell_candidates = find_sell_candidates(grid_lines, last_price)
    if sell_candidates:
        return {
            "suggestionType": "SELL",
            "suggestions": build_sell_suggestions(sell_candidates)
        }
    
    # 4. 查找可买入的网格
    buy_candidates = find_buy_candidates(grid_lines, last_price)
    if buy_candidates:
        # 应用密集买入保护策略
        adjusted_suggestions = apply_dense_buy_protection(
            buy_candidates, 
            recent_buy_count,
            position_ratio
        )
        return {
            "suggestionType": "BUY",
            "suggestions": adjusted_suggestions
        }
    
    # 5. 检查是否有暂缓网格可以补买
    deferred_grids = find_resumable_deferred_grids(grid_lines, trade_records)
    if deferred_grids:
        return {
            "suggestionType": "RESUME_BUY",
            "suggestions": build_resume_suggestions(deferred_grids)
        }
    
    # 6. 无操作建议
    return {
        "suggestionType": "HOLD",
        "reason": "当前价格处于持仓区间，建议观望"
    }
```

### 4.3 查找可卖出网格

```python
def find_sell_candidates(grid_lines, last_price):
    """
    查找所有可卖出的网格
    条件：state == BOUGHT 且 last_price >= sell_trigger_price
    """
    candidates = []
    for grid in grid_lines:
        if grid.state == "BOUGHT" and last_price >= grid.sell_trigger_price:
            candidates.append(grid)
    
    # 按 level 升序排列（优先卖出高层网格，即价格更高的）
    candidates.sort(key=lambda g: g.level)
    return candidates
```

### 4.4 查找可买入网格

```python
def find_buy_candidates(grid_lines, last_price):
    """
    查找所有可买入的网格
    条件：state == WAIT_BUY 且 last_price <= buy_trigger_price 且 deferred == False
    """
    candidates = []
    for grid in grid_lines:
        if grid.state == "WAIT_BUY" and last_price <= grid.buy_trigger_price and not grid.deferred:
            candidates.append(grid)
    
    # 按 level 升序排列（优先买入低层网格）
    candidates.sort(key=lambda g: g.level)
    return candidates
```

### 4.5 中网/大网跟随买入规则

```python
def find_following_grids(small_grid, grid_lines):
    """
    查找跟随小网买入的中网和大网
    规则：中网/大网的 buy_trigger_price == 小网的 buy_trigger_price
    """
    following = []
    for grid in grid_lines:
        if grid.grid_type in ["MEDIUM", "LARGE"]:
            if grid.buy_trigger_price == small_grid.buy_trigger_price:
                if grid.state == "WAIT_BUY":
                    following.append(grid)
    return following
```

---

## 5. 短期密集买入保护机制

### 5.1 定义

| 参数 | 值 | 说明 |
|------|-----|------|
| 统计周期 | 14 天 | 固定值 |
| 密集阈值 | 3 网 | 14天内买入 ≥3 网视为密集 |

### 5.2 统计方法

```python
def count_recent_buys(trade_records, days=14):
    """
    统计过去 N 天内的买入网格数（去重）
    注意：同一网格多次买入只算 1 次
    """
    cutoff_date = now() - timedelta(days=days)
    recent_records = [r for r in trade_records if r.type == "BUY" and r.trade_time >= cutoff_date]
    
    # 按网格去重
    unique_levels = set(r.grid_level for r in recent_records)
    return len(unique_levels)
```

### 5.3 中网/大网买入比例调整

```python
def calculate_quantity_ratio(grid_type, recent_buy_count):
    """
    根据短期买入网格数，计算中网/大网的买入比例
    
    | 短期买入数 | 中网比例 | 大网比例 |
    |-----------|---------|---------|
    | ≤ 2       | 100%    | 100%    |
    | 3         | 100%    | 50%     |
    | 4         | 50%     | 50%     |
    | ≥ 5       | 50%     | 0%（暂缓） |
    """
    if grid_type == "SMALL":
        return 1.0  # 小网始终全仓
    
    if grid_type == "MEDIUM":
        if recent_buy_count <= 3:
            return 1.0
        else:
            return 0.5
    
    if grid_type == "LARGE":
        if recent_buy_count <= 2:
            return 1.0
        elif recent_buy_count <= 4:
            return 0.5
        else:
            return 0.0  # 暂缓
    
    return 1.0
```

### 5.4 暂缓处理

```python
def apply_dense_buy_protection(buy_candidates, recent_buy_count, position_ratio):
    """
    应用密集买入保护，返回调整后的建议列表
    """
    suggestions = []
    
    for grid in buy_candidates:
        ratio = calculate_quantity_ratio(grid.grid_type, recent_buy_count)
        
        if ratio == 0:
            # 标记为暂缓
            mark_grid_deferred(grid, reason="DENSE_BUY")
            continue
        
        adjusted_quantity = grid.quantity * ratio
        adjusted_amount = grid.buy_amount * ratio
        
        suggestions.append({
            "gridLineId": grid.id,
            "gridLevel": grid.level,
            "gridType": grid.grid_type,
            "action": "BUY",
            "price": grid.buy_trigger_price,
            "quantity": adjusted_quantity,
            "amount": adjusted_amount,
            "quantityRatio": ratio,
            "reason": generate_buy_reason(grid, ratio, recent_buy_count)
        })
    
    return suggestions

def mark_grid_deferred(grid, reason):
    """
    将网格标记为暂缓状态
    """
    grid.deferred = True
    grid.deferred_reason = reason
    grid.deferred_at = now()
    save(grid)
```

---

## 6. 暂缓网格补买机制

### 6.1 补买触发条件

当满足以下**任一条件**时，暂缓的网格可以补买：

| 条件 | 说明 |
|------|------|
| 任意网格卖出成功 | 释放了仓位，可以补买 |
| 短期买入数回落 | 过了 14 天后，之前的买入不再计入统计 |
| 用户手动触发 | 用户主动要求补买 |

### 6.2 检查可补买的暂缓网格

```python
def find_resumable_deferred_grids(grid_lines, trade_records):
    """
    查找可以补买的暂缓网格
    """
    deferred_grids = [g for g in grid_lines if g.deferred == True]
    if not deferred_grids:
        return []
    
    # 检查是否有最近的卖出（触发补买条件）
    recent_sell = find_most_recent_sell(trade_records)
    
    resumable = []
    for grid in deferred_grids:
        if grid.deferred_at and recent_sell:
            if recent_sell.trade_time > grid.deferred_at:
                # 暂缓后有卖出，可以补买
                resumable.append(grid)
    
    return resumable

def find_most_recent_sell(trade_records):
    """
    查找最近一次卖出记录
    """
    sells = [r for r in trade_records if r.type == "SELL"]
    if not sells:
        return None
    return max(sells, key=lambda r: r.trade_time)
```

### 6.3 补买建议生成

```python
def build_resume_suggestions(deferred_grids):
    """
    生成暂缓网格的补买建议
    """
    suggestions = []
    
    for grid in deferred_grids:
        # 重新计算买入比例（可能情况已改善）
        recent_buy_count = count_recent_buys(trade_records, days=14)
        ratio = calculate_quantity_ratio(grid.grid_type, recent_buy_count)
        
        if ratio > 0:
            suggestions.append({
                "gridLineId": grid.id,
                "gridLevel": grid.level,
                "gridType": grid.grid_type,
                "action": "RESUME_BUY",
                "price": grid.buy_trigger_price,
                "quantity": grid.quantity * ratio,
                "amount": grid.buy_amount * ratio,
                "quantityRatio": ratio,
                "reason": f"暂缓网格补买（原因已解除）"
            })
    
    return suggestions
```

### 6.4 补买执行后

```python
def execute_resume_buy(grid_line_id, quantity, price):
    """
    执行暂缓网格的补买
    """
    grid = get_grid_line(grid_line_id)
    
    # 执行买入
    execute_buy(grid, quantity, price)
    
    # 清除暂缓状态
    grid.deferred = False
    grid.deferred_reason = None
    grid.deferred_at = None
    save(grid)
```

---

## 7. 仓位控制

### 7.1 持仓比例计算

```python
def calculate_position_ratio(strategy):
    """
    计算当前持仓比例
    公式：已投入资金 / 最大投入资金
    """
    invested = calculate_invested_amount(strategy)  # 所有 BOUGHT 状态网格的 buyAmount 之和
    max_capital = strategy.max_capital
    
    if max_capital == 0:
        return 0
    
    return invested / max_capital
```

### 7.2 仓位预警

| 持仓比例 | 预警级别 | 颜色 | 说明 |
|---------|---------|------|------|
| < 50% | 正常 | 绿色 | 可正常买入 |
| 50% - 70% | 提示 | 黄色 | 建议控制买入 |
| 70% - 80% | 警告 | 橙色 | 接近上限，谨慎买入 |
| ≥ 80% | 禁止 | 红色 | 暂停所有买入 |

### 7.3 仓位上限保护

```python
def check_position_limit(strategy, buy_amount):
    """
    检查买入后是否会超过仓位上限
    返回：(can_buy, adjusted_amount, warning)
    """
    current_invested = calculate_invested_amount(strategy)
    max_allowed = strategy.max_capital * strategy.max_position_ratio
    
    remaining = max_allowed - current_invested
    
    if remaining <= 0:
        return (False, 0, "持仓已达上限，暂停买入")
    
    if buy_amount > remaining:
        return (True, remaining, f"买入金额已调整为{remaining}元（仓位上限保护）")
    
    return (True, buy_amount, None)
```

---

## 8. 交易执行时的自动更新

### 8.1 买入执行后

```python
def after_buy_execute(strategy, grid_line, trade_record):
    """
    买入执行后的自动更新
    """
    # 1. 更新最新价格
    strategy.last_price = trade_record.price
    
    # 2. 检查是否需要标记中网/大网为暂缓
    recent_buy_count = count_recent_buys(strategy.trade_records, days=14)
    following_grids = find_following_grids(grid_line, strategy.grid_lines)
    
    for grid in following_grids:
        ratio = calculate_quantity_ratio(grid.grid_type, recent_buy_count)
        if ratio == 0:
            mark_grid_deferred(grid, reason="DENSE_BUY")
    
    save(strategy)
```

### 8.2 卖出执行后

```python
def after_sell_execute(strategy, grid_line, trade_record):
    """
    卖出执行后的自动更新
    """
    # 1. 更新最新价格
    strategy.last_price = trade_record.price
    
    # 2. 检查是否有暂缓网格可以补买（在下次获取建议时处理）
    # 这里只更新价格，补买建议在 /suggestion API 中生成
    
    save(strategy)
```

---

## 9. 前端展示

### 9.1 策略详情页新增内容

#### 9.1.1 最新价格区域

```
┌─────────────────────────────────────┐
│ 最新价格                    [编辑] │
│ ¥1.217                              │
│ 更新时间：2026-03-05 14:30          │
└─────────────────────────────────────┘
```

#### 9.1.2 智能建议区域

```
┌─────────────────────────────────────┐
│ 💡 操作建议                         │
├─────────────────────────────────────┤
│ ⚠️ 过去14天已买入4网，建议谨慎操作  │
│ ⚠️ 当前持仓66.68%，接近上限80%      │
├─────────────────────────────────────┤
│ 建议买入：                          │
│ • 第9网（中网）全仓 1000股 ¥1275    │
│ • 第10网（大网）半仓 500股 ¥637.5   │
│                                     │
│ [一键执行] [查看详情]               │
└─────────────────────────────────────┘
```

#### 9.1.3 暂缓网格提示

```
┌─────────────────────────────────────┐
│ ⏸️ 暂缓买入的网格                   │
├─────────────────────────────────────┤
│ 第10网（大网）- 因短期密集买入暂缓  │
│ 暂缓时间：2026-03-02 10:00          │
│ 补买条件：任意网格卖出后             │
│                                     │
│ [手动补买]                          │
└─────────────────────────────────────┘
```

### 9.2 网格卡片状态显示

网格卡片需要显示暂缓状态：

```
┌─────────────────────────────────────┐
│ 第10网 大网              [⏸️ 暂缓] │
│ 买入价：1.275  卖出价：1.710        │
│ 暂缓原因：短期密集买入              │
└─────────────────────────────────────┘
```

---

## 10. API 汇总

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/strategies/{id}/suggestion | 获取智能建议操作 |
| PUT | /api/strategies/{id}/last-price | 更新最新价格 |
| POST | /api/strategies/{id}/grids/{gridId}/resume-buy | 手动补买暂缓网格 |
| GET | /api/strategies/{id}/deferred-grids | 获取所有暂缓网格 |

---

## 11. 配置参数

以下参数可在系统配置或策略级别设置：

| 参数名 | 默认值 | 说明 |
|-------|--------|------|
| dense_buy_days | 14 | 短期密集买入统计天数 |
| dense_buy_threshold | 3 | 密集买入阈值（网格数） |
| max_position_ratio | 0.80 | 最大持仓比例 |
| position_warning_ratio | 0.70 | 持仓预警比例 |
| position_caution_ratio | 0.50 | 持仓提示比例 |

---

## 12. 测试用例

### 12.1 正常行情测试

**场景**：14天内买入 2 网，现价触发第3网买入

**预期**：
- 建议买入第3网（小网）全仓
- 第4网（中网）和第5网（大网）如果同价，也建议全仓买入

### 12.2 密集买入测试

**场景**：14天内已买入 4 网，现价触发第5网买入

**预期**：
- 建议买入第5网（小网）全仓
- 跟随的中网建议半仓
- 跟随的大网建议半仓
- 显示警告"过去14天已买入4网"

### 12.3 极端行情测试

**场景**：14天内已买入 5 网，现价触发第6网买入

**预期**：
- 建议买入第6网（小网）全仓
- 跟随的中网建议半仓
- 跟随的大网标记为暂缓，不建议买入
- 显示警告"过去14天已买入5网，建议谨慎操作"

### 12.4 仓位上限测试

**场景**：持仓比例已达 80%

**预期**：
- 不建议任何买入操作
- suggestionType = "HOLD"
- 显示警告"持仓已达上限，暂停买入"

### 12.5 暂缓补买测试

**场景**：第10网因密集买入被暂缓，随后第8网卖出成功

**预期**：
- 获取建议时，返回第10网可补买
- suggestionType = "RESUME_BUY"
- 显示"暂缓网格补买（原因已解除）"

### 12.6 卖出优先测试

**场景**：同时存在可买入和可卖出的网格

**预期**：
- suggestionType = "SELL"
- 优先返回卖出建议

---

## 13. 实施优先级

| 阶段 | 功能 | 优先级 |
|------|------|--------|
| P1 | 最新价格字段及自动更新 | 高 |
| P1 | 最新价格手动编辑 API | 高 |
| P1 | 智能建议 API 基础版（买入/卖出/持仓） | 高 |
| P2 | 短期密集买入统计 | 高 |
| P2 | 中网/大网买入比例调整 | 高 |
| P2 | 暂缓网格标记 | 中 |
| P3 | 暂缓网格补买机制 | 中 |
| P3 | 仓位控制及预警 | 中 |
| P4 | 前端智能建议展示 | 中 |
| P4 | 前端暂缓网格展示 | 低 |

---

## 14. 注意事项

1. **向后兼容**：新增字段需设置默认值，确保旧数据正常工作
2. **性能考虑**：建议 API 需要查询多个表，注意优化查询
3. **事务处理**：买入/卖出执行和状态更新需要在同一事务中
4. **并发控制**：防止同一网格被重复操作

---

## 附录 A：完整状态机

```
                    ┌─────────────┐
                    │  WAIT_BUY   │
                    │  (等待买入)  │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │   BOUGHT    │ │  DEFERRED   │ │   SKIPPED   │
    │  (已买入)    │ │  (暂缓买入)  │ │  (跳过)     │
    └──────┬──────┘ └──────┬──────┘ └─────────────┘
           │               │
           │               │ 补买
           │               ▼
           │        ┌─────────────┐
           │        │   BOUGHT    │
           │        │  (已买入)    │
           │        └──────┬──────┘
           │               │
           └───────┬───────┘
                   │ 卖出
                   ▼
            ┌─────────────┐
            │  WAIT_BUY   │
            │ (回到等待)   │
            └─────────────┘
```

---

## 附录 B：建议类型枚举

```java
public enum SuggestionType {
    BUY,        // 建议买入
    SELL,       // 建议卖出
    RESUME_BUY, // 建议补买暂缓网格
    HOLD        // 建议持仓观望
}
```

---

## 附录 C：警告类型枚举

```java
public enum WarningType {
    DENSE_BUY,       // 短期密集买入
    HIGH_POSITION,   // 持仓比例过高
    POSITION_LIMIT,  // 持仓达到上限
    PRICE_NOT_SET    // 最新价格未设置
}
```
