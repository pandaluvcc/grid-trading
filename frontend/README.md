# 网格交易系统 - 前端项目

## 技术栈

- **Vue 3** - 渐进式 JavaScript 框架
- **Vite** - 下一代前端构建工具
- **Element Plus** - 基于 Vue 3 的组件库
- **Axios** - HTTP 请求库

## 项目结构

```
frontend/
├── index.html          # HTML 入口文件
├── package.json        # 项目配置和依赖
├── vite.config.js      # Vite 配置文件
└── src/
    ├── main.js         # 应用入口（注册插件、挂载应用）
    └── App.vue         # 根组件
```

## 快速开始

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

启动后访问：http://localhost:3000

### 3. 构建生产版本

```bash
npm run build
```

## 配置说明

### vite.config.js

- **端口**：前端运行在 3000 端口
- **代理**：所有 `/api` 开头的请求会转发到后端 `http://localhost:8080`

### package.json

包含项目依赖和脚本命令：
- `npm run dev` - 启动开发服务器
- `npm run build` - 构建生产版本
- `npm run preview` - 预览生产版本

## 代码说明（适合 Java 开发者）

### main.js - 应用入口

类似 Java 的 `main` 方法，负责：
1. 创建 Vue 应用实例
2. 注册全局插件（Element Plus）
3. 挂载应用到 DOM

```javascript
import { createApp } from 'vue'        // 导入 Vue
import ElementPlus from 'element-plus' // 导入 UI 组件库
import App from './App.vue'            // 导入根组件

const app = createApp(App)             // 创建应用实例
app.use(ElementPlus)                   // 注册插件
app.mount('#app')                      // 挂载到 <div id="app">
```

### App.vue - 根组件

Vue 组件由三部分组成：

1. **`<template>`** - HTML 模板（类似 JSP/Thymeleaf）
2. **`<script setup>`** - JavaScript 逻辑（类似 Controller）
3. **`<style>`** - CSS 样式

```vue
<template>
  <!-- HTML 结构 -->
  <div>{{ message }}</div>
</template>

<script setup>
// JavaScript 逻辑
import { ref } from 'vue'
const message = ref('Hello')
</script>

<style>
/* CSS 样式 */
</style>
```

### 响应式数据

Vue 3 使用 `ref()` 创建响应式数据，类似 Java 的可观察对象：

```javascript
const count = ref(0)        // 创建响应式变量
count.value = 1             // 修改值（自动触发 UI 更新）
```

### HTTP 请求

使用 Axios 发送 HTTP 请求，类似 Java 的 RestTemplate：

```javascript
// GET 请求
const response = await axios.get('/api/health')

// POST 请求
const response = await axios.post('/api/data', { name: 'test' })
```

## 与后端对接

前端通过 Axios 调用后端 API：

```javascript
// 前端代码
axios.get('/api/health')
  ↓
// Vite 代理转发
http://localhost:3000/api/health 
  ↓
// 后端接收
http://localhost:8080/api/health
  ↓
// Spring Boot Controller
@GetMapping("/api/health")
```

## Element Plus 组件

Element Plus 提供开箱即用的 UI 组件：

- `<el-button>` - 按钮
- `<el-card>` - 卡片
- `<el-table>` - 表格
- `<el-form>` - 表单
- 更多组件见：https://element-plus.org/

## 常用命令

```bash
# 安装依赖
npm install

# 启动开发服务器（热更新）
npm run dev

# 构建生产版本
npm run build

# 查看构建结果
npm run preview
```

## 常见问题

### 1. 后端连接失败

确保后端服务已启动：
```bash
# 后端应该运行在 8080 端口
http://localhost:8080
```

### 2. 端口被占用

修改 `vite.config.js` 中的端口：
```javascript
server: {
  port: 3001  // 改为其他端口
}
```

### 3. 依赖安装失败

尝试清理缓存：
```bash
rm -rf node_modules
npm install
```

---

**项目状态**：✅ 就绪  
**版本**：1.0.0
