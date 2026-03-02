# 交易执行流程优化 - 修改说明

## 📋 需求分析

### 原有问题
1. ❌ 点击"执行"按钮就直接写入数据库，无法取消
2. ❌ 弹窗只显示价格和金额，没有显示交易数量
3. ❌ "跳过"按钮只是关闭弹窗，但交易已经写入数据库

### 用户需求
1. ✅ 增加交易数量栏位显示
2. ✅ 增加关闭逻辑，点击"执行"不立即写库
3. ✅ 点击"保存"才真正执行并写库
4. ✅ "取消"按钮可以放弃执行

---

## 🔧 解决方案

### 新的执行流程

```
点击"执行"按钮
    ↓
调用预览接口 (不写库)
    ↓
弹窗显示将要执行的交易
  - 买入/卖出类型
  - 价格
  - 数量 ✨ (新增)
  - 金额
  - 手续费输入框
    ↓
用户选择:
  - 点击"取消" → 关闭弹窗，不执行 ✨ (新增)
  - 点击"保存并执行" → 真正写库并更新费用 ✨ (修改)
    ↓
执行成功，刷新页面数据
```

---

## 📝 修改内容

### 后端修改

#### 1. 新增 DTO 类

**PreviewTradeDto.java** - 预览交易项
```java
- type: 交易类型 (BUY/SELL)
- price: 价格
- quantity: 数量 ✨
- amount: 金额
- gridLineId: 网格线ID
- level: 网格层级
```

**TickPreviewResponse.java** - 预览响应
```java
- trades: List<PreviewTradeDto>
```

**TickConfirmRequest.java** - 确认执行请求
```java
- price: 价格
- trades: List<TradeWithFee>
  - gridLineId: 网格线ID
  - fee: 手续费
```

#### 2. GridEngine 新增方法

**previewPrice()** - 预览价格触发
- 不写入数据库
- 只计算并返回将要触发的交易
- 返回 List<PreviewTradeDto>

#### 3. StrategyController 新增接口

**POST /api/strategies/{id}/tick/preview** - 预览接口
- 调用 GridEngine.previewPrice()
- 返回将要执行的交易列表
- 不修改数据库

**POST /api/strategies/{id}/tick/confirm** - 确认执行接口
- 真正调用 GridEngine.processPrice() 写库
- 同时更新交易记录的手续费
- 返回执行结果

---

### 前端修改

#### 1. API 接口 (api.js)

新增两个接口：
```javascript
// 预览价格触发
export function previewTick(strategyId, price)

// 确认执行价格触发
export function confirmTick(strategyId, price, trades)
```

#### 2. MobileStrategyDetail.vue

**handleExecute()** - 执行方法修改
```javascript
// 原来：直接调用 executeTick() 写库
// 现在：调用 previewTick() 预览

- 不再立即写库
- 获取预览的交易列表
- 弹窗显示交易信息
```

**saveTickFees()** - 保存方法修改
```javascript
// 原来：只更新手续费
// 现在：调用 confirmTick() 真正执行

- 构建交易费用数据
- 调用 confirmTick() 写库
- 成功后刷新页面数据
```

**cancelTickFees()** - 新增取消方法
```javascript
// 原来：skipTickFees() 只关闭弹窗
// 现在：cancelTickFees() 取消执行

- 关闭弹窗
- 提示"已取消执行"
- 不写入数据库
```

**弹窗修改**
```vue
<!-- 标题 -->
原来：录入本次交易费用
现在：确认执行交易 ✨

<!-- 提示文本 -->
原来：本次触发产生了以下交易，请录入手续费：
现在：本次将触发以下交易，请录入手续费后保存执行： ✨

<!-- 交易信息显示 -->
新增：交易数量 (份数) ✨
<span class="tick-fee-quantity">{{ formatQuantity(trade.quantity) }}份</span>

<!-- 按钮 -->
原来：跳过 | 保存
现在：取消 | 保存并执行 ✨

<!-- 弹窗关闭 -->
原来：:show-close="false" (不能关闭)
现在：可以点击右上角 X 关闭 ✨
```

**新增格式化方法**
```javascript
const formatQuantity = (val) => val == null ? '0' : Number(val).toFixed(4)
```

---

## 🎯 核心改进

### 1. 交易数量显示 ✨
- **显示位置**：弹窗中每笔交易的信息
- **显示格式**：`0.1234份`（保留4位小数）
- **显示顺序**：类型 → 价格 → **数量** → 金额

### 2. 两阶段执行 ✨

**阶段1：预览** (不写库)
```
用户输入价格 → 点击"执行" 
  ↓
调用 /api/strategies/{id}/tick/preview
  ↓
后端计算将要触发的交易（不写库）
  ↓
返回交易列表（包含数量）
  ↓
弹窗显示
```

**阶段2：确认执行** (写库)
```
用户录入手续费 → 点击"保存并执行"
  ↓
调用 /api/strategies/{id}/tick/confirm
  ↓
后端真正执行 GridEngine.processPrice()
  ↓
写入交易记录 + 更新手续费
  ↓
返回执行结果
  ↓
刷新页面数据
```

### 3. 取消机制 ✨

**取消方式**：
1. 点击"取消"按钮
2. 点击弹窗右上角 X
3. 按 ESC 键（现在支持了）

**取消效果**：
- 关闭弹窗
- 提示"已取消执行"
- **不写入数据库**

---

## 📊 接口对比

| 接口 | 原来 | 现在 |
|------|------|------|
| **执行触发** | POST /tick (直接写库) | POST /tick/preview (预览) |
| **确认执行** | - | POST /tick/confirm (写库) ✨ |
| **更新费用** | PUT /trades/{id}/fee | 集成到 confirm 接口 |

---

## 🔍 测试要点

### 1. 预览功能
- [ ] 点击"执行"后不应该写入数据库
- [ ] 弹窗显示正确的交易信息（类型、价格、**数量**、金额）
- [ ] 预览多笔交易时全部显示

### 2. 取消功能
- [ ] 点击"取消"按钮可以关闭弹窗
- [ ] 取消后不写入数据库
- [ ] 取消后提示"已取消执行"

### 3. 确认执行功能
- [ ] 点击"保存并执行"才真正写库
- [ ] 手续费正确保存到交易记录
- [ ] 执行后正确刷新页面数据

### 4. 显示效果
- [ ] 交易数量显示格式正确（保留4位小数）
- [ ] 按钮文案正确："取消" | "保存并执行"
- [ ] 弹窗标题正确："确认执行交易"

---

## 📌 注意事项

### 1. 数据一致性
- 预览时不写库，但计算逻辑与真正执行完全一致
- 确认执行时才真正调用 GridEngine.processPrice()

### 2. 手续费处理
- 手续费可选填（可以为空）
- 通过 gridLineId 匹配交易记录
- 一次性批量更新所有手续费

### 3. 错误处理
- 预览失败：提示"预览失败"，不显示弹窗
- 执行失败：提示具体错误信息，保持弹窗打开

### 4. 用户体验
- 弹窗提示文案更清晰："将触发"而非"产生了"
- 按钮文案更明确："保存并执行"而非"保存"
- 支持取消操作，给用户反悔的机会

---

## 🎉 改进效果

### Before (原来)
```
点击"执行" → 立即写库 → 弹窗让补充费用
                ↓
          无法取消，交易已完成
```

### After (现在)
```
点击"执行" → 预览交易（含数量） → 弹窗确认
                                    ↓
                         用户可以取消或确认
                                    ↓
                点击"保存并执行" → 真正写库
```

**核心优势**：
1. ✅ 用户可以在执行前看到完整的交易信息（包括数量）
2. ✅ 用户可以取消执行，避免误操作
3. ✅ 执行时机由用户完全掌控
4. ✅ 信息展示更完整（增加了数量显示）

---

## 📁 修改的文件清单

### 后端
- ✅ `PreviewTradeDto.java` - 新增
- ✅ `TickPreviewResponse.java` - 新增
- ✅ `TickConfirmRequest.java` - 新增
- ✅ `GridEngine.java` - 新增 previewPrice() 方法
- ✅ `StrategyController.java` - 新增两个接口

### 前端
- ✅ `api.js` - 新增两个API方法
- ✅ `MobileStrategyDetail.vue` - 修改执行流程和弹窗

---

**修改完成时间**：2026-03-02  
**修改人**：AI Assistant  
**需求来源**：用户反馈优化

