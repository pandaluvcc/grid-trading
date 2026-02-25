# 截图识别自动导入交易记录需求文档

## 功能名称
截图识别自动导入交易记录

## 一、功能概述
支持用户上传券商 APP 截图，系统自动识别截图中的交易记录，匹配到已创建的网格策略，并导入成交记录，同时动态调整网格计划。

---

## 二、功能模块拆分

### 模块 1：图片上传接口
- 后端新增图片上传 API 接口
- 支持单张或多张图片上传
- 支持 PNG、JPG、JPEG 格式
- 图片临时存储或直接传递给 OCR 服务

**技术要点：**
- Spring Boot MultipartFile 处理
- 文件大小限制（建议 10MB）
- 新增 Controller: ImportController

---

### 模块 2：OCR 文字识别服务
- 集成 OCR 服务识别图片中的文字
- 推荐使用：百度 OCR / 腾讯 OCR / PaddleOCR（本地部署）
- 返回结构化的文字识别结果

**技术要点：**
- 新增 Service: OcrService
- 支持多种 OCR 提供商的适配器模式
- 配置文件中管理 API Key

---

### 模块 3：交易记录解析引擎
- 解析 OCR 返回的文字，提取交易记录
- 支持多种券商 APP 格式（先支持东方财富）
- 提取字段：交易类型、日期时间、价格、数量、金额、费用

**技术要点：**
- 新增 Service: TradeRecordParserService
- 使用正则表达式 + 模式匹配
- 定义券商格式模板（可扩展）

**解析规则示例（东方财富）：**
- "买入" / "卖出" / "建仓-买入" → TradeType
- "数量 1000" → quantity
- "价格 1.394" → price
- "金额 1394.00" → amount
- "费用 0.35" → fee
- "2026-02-12 10:32:13" → tradeTime

---

### 模块 4：策略智能匹配
- 根据识别出的 ETF 代码/名称，自动匹配已有策略
- 如果没有匹配策略，提示用户选择或创建新策略
- 支持模糊匹配（名称相似度）

**技术要点：**
- 新增 Service: StrategyMatcherService
- 根据 symbol (513050) 或 name (中概互联) 匹配
- 返回匹配候选列表供用户确认

---

### 模块 5：交易记录导入与去重
- 将解析后的交易记录批量导入数据库
- 智能去重：根据 (策略ID + 交易时间 + 价格 + 数量) 判断重复
- 支持预览模式：先展示待导入记录，用户确认后再导入

**技术要点：**
- 新增 Service: TradeRecordImportService
- 新增 DTO: ImportPreviewDTO, ImportResultDTO
- 事务管理保证数据一致性

---

### 模块 6：网格计划动态调整
- 导入交易记录后，自动更新对应网格线状态
- 买入记录 → 找到匹配价格的网格线，更新为 HOLDING 状态
- 卖出记录 → 找到匹配的网格线，更新为 EMPTY 状态
- 如果价格不在现有网格内，标记为"计划外交易"

**技术要点：**
- 扩展 GridEngine: syncGridLinesFromTrades()
- 价格容差匹配（如 ±0.5%）
- 新增字段区分"计划内"和"计划外"交易

---

### 模块 7：前端界面
- 新增"导入交易记录"页面/弹窗
- 支持拖拽上传多张截图
- 显示识别进度和结果预览
- 确认导入前可编辑/删除错误识别的记录
- 显示匹配的策略，支持手动更改

**技术要点：**
- 新增组件: ImageUploader.vue, ImportPreview.vue
- 新增视图: TradeImport.vue
- API 集成: api.js 新增导入相关接口

---

## 三、数据库变更

```sql
-- 新增字段：标记交易来源
ALTER TABLE trade_record ADD COLUMN source VARCHAR(20) DEFAULT 'MANUAL';
-- 可选值: MANUAL(手动), IMAGE_IMPORT(截图导入), API(接口)

-- 新增字段：标记是否计划外交易
ALTER TABLE trade_record ADD COLUMN is_unplanned BOOLEAN DEFAULT FALSE;

-- 新增表：导入批次记录（可选，用于追溯）
CREATE TABLE import_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    strategy_id BIGINT,
    import_time TIMESTAMP,
    image_count INT,
    record_count INT,
    status VARCHAR(20)
);
```

---

## 四、API 接口设计

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/import/upload` | POST | 上传截图，返回识别结果 |
| `/api/import/preview` | POST | 预览待导入记录 |
| `/api/import/confirm` | POST | 确认导入 |
| `/api/import/match-strategy` | GET | 根据 ETF 代码匹配策略 |

---

## 五、执行优先级

| 阶段 | 任务 | 预估工作量 |
|------|------|------------|
| P0 | 模块 1-3：图片上传 + OCR + 解析 | 2-3 天 |
| P1 | 模块 4-5：策略匹配 + 记录导入 | 1-2 天 |
| P2 | 模块 6：网格动态调整 | 1 天 |
| P3 | 模块 7：前端界面 | 2 天 |

---

## 六、给 AI 的执行指令

### 任务 1：创建图片导入基础架构
在 `backend/src/main/java/com/gridtrading/` 下创建：
- `controller/ImportController.java`
- `service/OcrService.java`
- `service/TradeRecordParserService.java`
- `service/StrategyMatcherService.java`
- `service/TradeRecordImportService.java`
- `dto/ParsedTradeRecord.java`
- `dto/ImportPreviewDTO.java`
- `dto/ImportResultDTO.java`

### 任务 2：实现东方财富截图解析规则
- 解析"买入"/"卖出"/"建仓-买入"
- 提取价格、数量、金额、费用、时间
- 提取 ETF 代码和名称

### 任务 3：实现策略匹配逻辑
- 根据 symbol 精确匹配
- 根据名称模糊匹配

### 任务 4：实现导入和网格同步
- 批量导入交易记录
- 更新网格线状态
- 去重逻辑

### 任务 5：前端实现
- 创建导入页面组件
- 图片上传组件
- 预览和确认界面

---

## 七、示例数据（从截图识别）

以下是从东方财富截图中识别的交易记录示例：

**ETF 信息：** 中概互联 513050

| 类型 | 日期时间 | 价格 | 数量 | 金额 | 费用 |
|------|----------|------|------|------|------|
| 买入 | 2026-02-12 10:32:13 | 1.394 | 1000 | 1394.00 | 0.35 |
| 买入 | 2026-02-03 10:15:07 | 1.467 | 1000 | 1467.00 | 0.37 |
| 买入 | 2026-02-03 10:15:13 | 1.466 | 1000 | 1466.00 | 0.37 |
| 卖出 | 2026-01-06 09:54:03 | 1.544 | 1000 | 1544.00 | 0.39 |
| 买入 | 2025-12-16 13:37:12 | 1.467 | 1000 | 1467.00 | 0.37 |
| 买入 | 2025-11-20 10:18:29 | 1.544 | 1000 | 1544.00 | 0.39 |
| 买入 | 2025-10-13 11:20:20 | 1.625 | 500 | 812.50 | 0.20 |
| 买入 | 2025-10-13 09:30:40 | 1.625 | 500 | 812.50 | 0.20 |
| 建仓-买入 | 2025-10-10 09:56:05 | 1.710 | 1000 | 1710.00 | 0.43 |

---

**建议从 P0 阶段（模块 1-3：后端基础架构）开始实现。**
