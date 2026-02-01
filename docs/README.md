# 📚 网格交易系统 · 文档中心

> **Grid Trading System Documentation Hub**

---

## 📖 文档导航

### 🎯 核心规格文档

#### 1. [网格执行引擎 2.0 规格说明](./GridEngine-Specification-v2.0.md)
**推荐：⭐⭐⭐⭐⭐**

完整的技术规格说明，包含：
- 引擎职责定义
- 执行流程详解（Step 0-1-2-3）
- 数据流示例
- 设计原则
- 测试场景建议

**适用场景**：
- 首次实现引擎时精读
- 与团队成员讨论设计
- 代码审查参考标准

---

#### 2. [AI 快速参考卡](./AI-Quick-Reference.md)
**推荐：⭐⭐⭐⭐⭐**

浓缩版速查手册，包含：
- 核心算法伪代码
- 关键规则表格
- 计算公式
- AI 提示词模板
- 常见错误清单

**适用场景**：
- 直接喂给 AI 编码助手
- 快速查询关键逻辑
- Debug 时检查清单

---

#### 3. [实现检查清单](./Implementation-Checklist.md)
**推荐：⭐⭐⭐⭐**

项目实施路线图，包含：
- 待补充的实体字段
- Repository/Service/Controller 结构
- 数据库表设计
- 测试用例列表
- 实现优先级分阶段

**适用场景**：
- 项目规划
- 进度追踪
- 任务分配

---

## 🚀 快速开始

### 对于开发者

**第一次接触项目？**

1. 阅读 [GridEngine-Specification-v2.0.md](./GridEngine-Specification-v2.0.md) 了解核心业务逻辑
2. 查看 [Implementation-Checklist.md](./Implementation-Checklist.md) 了解当前进度
3. 开始编码时参考 [AI-Quick-Reference.md](./AI-Quick-Reference.md)

**已经熟悉项目？**

直接查阅 [AI-Quick-Reference.md](./AI-Quick-Reference.md) 获取算法和公式。

---

### 对于 AI 编码助手

#### 场景 1：实现核心引擎

**输入提示词**：
```
请根据 docs/GridEngine-Specification-v2.0.md 和 docs/AI-Quick-Reference.md 
实现 com.gridtrading.service.GridEngine 类的 executeTick() 方法。

要求：
1. 严格遵循 Step 0-1-2-3 执行顺序
2. 支持"一网打尽"
3. 实现风控 STOP 逻辑
4. 代码清晰，添加详细注释
```

#### 场景 2：创建 Repository 接口

**输入提示词**：
```
请根据 docs/AI-Quick-Reference.md 中的"关键查询方法"部分，
创建以下 Repository 接口：

1. StrategyRepository
2. GridLineRepository
3. TradeRecordRepository

包含必要的查询方法和排序。
```

#### 场景 3：编写单元测试

**输入提示词**：
```
请根据 docs/GridEngine-Specification-v2.0.md 第八章"测试场景建议"，
为 GridEngine.executeTick() 编写完整的单元测试。

使用 JUnit 5 + Mockito。
```

---

## 📂 项目结构

```
grid-trading/
├── docs/                                    # 📚 文档目录（当前位置）
│   ├── README.md                            # 本文件
│   ├── GridEngine-Specification-v2.0.md     # 核心规格
│   ├── AI-Quick-Reference.md                # AI 速查卡
│   └── Implementation-Checklist.md          # 实现清单
│
├── backend/                                 # 后端项目
│   ├── src/main/java/com/gridtrading/
│   │   ├── domain/                          # ✅ 领域模型（已完成）
│   │   │   ├── Strategy.java
│   │   │   ├── GridLine.java
│   │   │   ├── TradeRecord.java
│   │   │   ├── StrategyStatus.java
│   │   │   ├── GridLineState.java
│   │   │   └── TradeType.java
│   │   │
│   │   ├── repository/                      # ⬜ 待实现
│   │   ├── service/                         # ⬜ 待实现
│   │   │   └── GridEngine.java              # 🎯 核心引擎
│   │   ├── controller/                      # ⬜ 待实现
│   │   └── dto/                             # ⬜ 待实现
│   │
│   ├── src/test/java/                       # ⬜ 待编写测试
│   ├── init.sql                             # 数据库初始化
│   └── pom.xml
│
└── pom.xml                                  # Maven 父项目
```

---

## ✅ 当前进度

### 已完成

- ✅ 项目结构初始化（Maven 多模块）
- ✅ 领域模型创建（Strategy, GridLine, TradeRecord）
- ✅ 枚举类型定义（StrategyStatus, GridLineState, TradeType）
- ✅ 完整文档编写（规格说明 + 参考卡 + 检查清单）

### 待实现

- ⬜ 补充 Strategy 运行时字段（lastPrice, availableCash, etc.）
- ⬜ 创建 Repository 接口
- ⬜ 实现 GridEngine 核心引擎
- ⬜ 实现 Service 层
- ⬜ 实现 Controller 层
- ⬜ 编写单元测试
- ⬜ 数据库表初始化

---

## 🎯 核心概念速览

### 什么是网格交易？

网格交易是一种量化交易策略：
- 在基准价上下设置若干买卖网格
- 价格下跌时逐级买入（摊低成本）
- 价格上涨时逐级卖出（获利离场）
- 适合震荡市场

### 引擎核心逻辑

```
价格更新 → 检查买入触发 → 检查卖出触发 → 风控判断
```

### 关键状态

| 实体     | 状态字段 | 可选值              |
|----------|----------|---------------------|
| Strategy | status   | RUNNING / STOPPED   |
| GridLine | state    | WAIT_BUY / WAIT_SELL|

---

## 🔗 相关资源

### 技术栈

- **后端框架**：Spring Boot 3.2.2
- **持久化**：Spring Data JPA + MySQL
- **Java 版本**：17
- **构建工具**：Maven

### 外部链接

- [Spring Data JPA 官方文档](https://spring.io/projects/spring-data-jpa)
- [网格交易策略介绍](https://www.investopedia.com/terms/g/grid-trading.asp)

---

## 📝 更新日志

| 日期       | 版本 | 变更说明                          |
|------------|------|-----------------------------------|
| 2026-02-01 | 1.0  | 初始版本，完成核心文档编写        |

---

## 🤝 贡献指南

### 文档更新原则

1. **保持一致性**：修改规格时，同步更新所有相关文档
2. **AI 友好**：使用清晰的格式，便于 AI 解析
3. **完整性**：提供完整的示例和错误案例
4. **版本控制**：重大变更更新版本号

### 文档更新流程

```
修改规格说明 
  ↓
更新快速参考卡 
  ↓
同步实现清单 
  ↓
更新 README 版本号
```

---

## 📬 联系方式

有问题或建议？请通过以下方式联系：

- 项目 Issue 系统
- 团队内部讨论组

---

**📌 将此文档加入书签，随时快速导航到所需文档！**

---

<div align="center">

**Grid Trading System Documentation** © 2026

*Designed for AI-Assisted Development*

</div>
