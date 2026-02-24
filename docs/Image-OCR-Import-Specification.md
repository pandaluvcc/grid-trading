# 券商截图OCR识别导入交易记录方案

## 一、需求概述

### 1.1 业务场景
用户使用东方财富等券商APP进行网格交易，需要将APP中的实际成交记录批量导入到本系统中，与已创建的策略进行关联。

### 1.2 数据来源（截图分析）
从东方财富APP截图识别的交易记录字段：

| 字段 | 示例值 | 说明 |
|------|--------|------|
| 交易类型 | 买入/卖出/建仓-买入 | 红色文字标识 |
| 交易时间 | 2026-02-12 10:32:13 | 精确到秒 |
| 数量 | 1000 | 成交数量（股/份） |
| 金额 | 1394.00 | 成交金额 |
| 价格 | 1.394 | 成交单价 |
| 费用 | 0.35 | 交易手续费 |

### 1.3 目标系统字段映射

| 截图字段 | TradeRecord字段 | 类型 |
|----------|-----------------|------|
| 交易类型 | type | TradeType (BUY/SELL) |
| 交易时间 | tradeTime | LocalDateTime |
| 数量 | quantity | BigDecimal |
| 金额 | amount | BigDecimal |
| 价格 | price | BigDecimal |
| 费用 | fee | BigDecimal |

---

## 二、技术方案选型

### 2.1 OCR技术方案对比

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **百度OCR API** | 免费额度充足(1000次/月)、中文识别准确率高、接入简单 | 需联网、有调用限制 | ⭐⭐⭐⭐⭐ |
| 阿里云OCR | 准确率高、稳定 | 免费额度较少 | ⭐⭐⭐⭐ |
| Tesseract.js | 纯前端、无需服务器、无调用限制 | 中文准确率一般、首次加载慢 | ⭐⭐⭐ |
| Tess4J (Java本地) | 无网络依赖、无调用限制 | 安装复杂、中文支持需配置 | ⭐⭐⭐ |

### 2.2 推荐方案：百度OCR API

**选择理由：**
1. **免费额度充足**：标准版每月1000次免费调用，个人使用完全足够
2. **中文识别准确率高**：针对中文优化，对截图场景支持好
3. **接入简单**：提供Java SDK，与Spring Boot集成方便
4. **高精度模式**：支持高精度版本，复杂场景可升级

**备选方案：Tesseract.js**
- 适用于完全离线场景或频繁调用场景
- 可作为前端预处理或降级方案

---

## 三、系统架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Vue3)                       │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ 图片上传组件 │  │ 预览/编辑组件│  │ 批量导入确认组件  │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘ │
└─────────┼────────────────┼────────────────────┼─────────────┘
          │                │                    │
          ▼                ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                      Backend (Spring Boot)                   │
├─────────────────────────────────────────────────────────────┤
│  ┌────────────────┐  ┌──────────────┐  ┌─────────────────┐ │
│  │OcrController   │  │ OcrService   │  │ImportService    │ │
│  │(图片上传接口)   │─▶│(OCR识别服务) │─▶│(数据解析导入)   │ │
│  └────────────────┘  └───────┬──────┘  └────────┬────────┘ │
│                              │                   │          │
│                              ▼                   ▼          │
│                     ┌────────────────┐  ┌──────────────────┐│
│                     │ 百度OCR API    │  │ GridEngine       ││
│                     │ (外部服务)     │  │ (网格匹配)       ││
│                     └────────────────┘  └──────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### 3.2 处理流程

```
用户上传截图
     │
     ▼
┌─────────────┐
│ 图片预处理   │ (可选：裁剪、增强对比度)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ OCR识别     │ ─────▶ 百度OCR API
└──────┬──────┘
       │ 返回文字识别结果
       ▼
┌─────────────┐
│ 文本解析    │ 提取交易记录结构化数据
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 数据校验    │ 检查必填字段、数据格式
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ 网格线匹配      │ 根据价格匹配现有网格线
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 用户确认/编辑   │ 展示识别结果，允许修正
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 批量导入        │ 调用现有交易记录接口
└─────────────────┘
```

---

## 四、后端实现方案

### 4.1 新增依赖 (pom.xml)

```xml
<!-- 百度OCR SDK -->
<dependency>
    <groupId>com.baidu.aip</groupId>
    <artifactId>java-sdk</artifactId>
    <version>4.16.18</version>
</dependency>

<!-- 文件上传支持 -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.15.1</version>
</dependency>
```

### 4.2 配置文件 (application.yml)

```yaml
# 百度OCR配置
baidu:
  ocr:
    app-id: "你的APP_ID"
    api-key: "你的API_KEY"
    secret-key: "你的SECRET_KEY"
```

### 4.3 核心类设计

#### 4.3.1 DTO定义

```java
/**
 * OCR识别请求
 */
public class OcrRecognizeRequest {
    private Long strategyId;         // 目标策略ID
    private String brokerType;       // 券商类型：EASTMONEY(东方财富)
}

/**
 * OCR识别的单条交易记录
 */
public class OcrTradeRecord {
    private String type;             // BUY/SELL
    private String tradeTime;        // 2026-02-12 10:32:13
    private BigDecimal quantity;     // 1000
    private BigDecimal amount;       // 1394.00
    private BigDecimal price;        // 1.394
    private BigDecimal fee;          // 0.35
    
    // 匹配结果
    private Long matchedGridLineId;  // 匹配到的网格线ID
    private Integer matchedLevel;    // 匹配到的网格层级
    private String matchStatus;      // MATCHED/UNMATCHED/DUPLICATE
    private String matchMessage;     // 匹配说明
}

/**
 * OCR识别响应
 */
public class OcrRecognizeResponse {
    private boolean success;
    private String message;
    private String rawText;                    // 原始OCR文本（调试用）
    private List<OcrTradeRecord> records;      // 解析出的交易记录
    private int totalCount;                    // 总记录数
    private int matchedCount;                  // 成功匹配数
}

/**
 * 批量导入请求
 */
public class BatchImportRequest {
    private Long strategyId;
    private List<OcrTradeRecord> records;      // 用户确认后的记录列表
}
```

#### 4.3.2 OcrController

```java
@RestController
@RequestMapping("/api/ocr")
@CrossOrigin(origins = "*")
public class OcrController {

    private final OcrService ocrService;
    private final ImportService importService;

    /**
     * 上传截图并识别
     * POST /api/ocr/recognize
     */
    @PostMapping("/recognize")
    public OcrRecognizeResponse recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam("strategyId") Long strategyId,
            @RequestParam(value = "brokerType", defaultValue = "EASTMONEY") String brokerType
    ) {
        return ocrService.recognizeAndParse(file, strategyId, brokerType);
    }

    /**
     * 批量导入确认后的记录
     * POST /api/ocr/import
     */
    @PostMapping("/import")
    public Map<String, Object> batchImport(@RequestBody BatchImportRequest request) {
        return importService.batchImport(request);
    }
}
```

#### 4.3.3 OcrService（核心识别服务）

```java
@Service
public class OcrService {

    @Value("${baidu.ocr.app-id}")
    private String appId;
    
    @Value("${baidu.ocr.api-key}")
    private String apiKey;
    
    @Value("${baidu.ocr.secret-key}")
    private String secretKey;

    private AipOcr client;

    @PostConstruct
    public void init() {
        client = new AipOcr(appId, apiKey, secretKey);
        // 可选：设置网络连接参数
        client.setConnectionTimeoutInMillis(2000);
        client.setSocketTimeoutInMillis(60000);
    }

    /**
     * 识别图片并解析交易记录
     */
    public OcrRecognizeResponse recognizeAndParse(
            MultipartFile file, 
            Long strategyId, 
            String brokerType
    ) {
        try {
            // 1. 调用百度OCR
            byte[] imageBytes = file.getBytes();
            JSONObject result = client.accurateGeneral(imageBytes, new HashMap<>());
            
            // 2. 提取文字内容
            String rawText = extractText(result);
            
            // 3. 根据券商类型解析
            List<OcrTradeRecord> records = parseByBrokerType(rawText, brokerType);
            
            // 4. 匹配网格线
            matchGridLines(records, strategyId);
            
            // 5. 构建响应
            return buildResponse(rawText, records);
            
        } catch (Exception e) {
            return OcrRecognizeResponse.error("OCR识别失败: " + e.getMessage());
        }
    }
}
```

#### 4.3.4 文本解析器（东方财富格式）

```java
/**
 * 东方财富APP交易记录解析器
 */
@Component
public class EastMoneyParser implements BrokerParser {

    /**
     * 解析OCR文本，提取交易记录
     * 
     * 东方财富格式示例：
     * "买入 2026-02-12 10:32:13 数量 1000 金额 1394.00 价格 1.394 费用 0.35"
     */
    @Override
    public List<OcrTradeRecord> parse(String rawText) {
        List<OcrTradeRecord> records = new ArrayList<>();
        
        // 按交易类型关键词分割
        String[] blocks = rawText.split("(?=买入|卖出|建仓)");
        
        for (String block : blocks) {
            if (block.trim().isEmpty()) continue;
            
            OcrTradeRecord record = new OcrTradeRecord();
            
            // 解析交易类型
            if (block.contains("卖出")) {
                record.setType("SELL");
            } else if (block.contains("买入") || block.contains("建仓")) {
                record.setType("BUY");
            } else {
                continue; // 无法识别类型，跳过
            }
            
            // 解析时间 (格式: 2026-02-12 10:32:13)
            Pattern timePattern = Pattern.compile(
                "(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})"
            );
            Matcher timeMatcher = timePattern.matcher(block);
            if (timeMatcher.find()) {
                record.setTradeTime(timeMatcher.group(1));
            }
            
            // 解析数值字段
            record.setQuantity(extractNumber(block, "数量"));
            record.setAmount(extractNumber(block, "金额"));
            record.setPrice(extractNumber(block, "价格"));
            record.setFee(extractNumber(block, "费用"));
            
            // 数据校验
            if (isValidRecord(record)) {
                records.add(record);
            }
        }
        
        return records;
    }
    
    private BigDecimal extractNumber(String text, String label) {
        // 支持多种格式：
        // "数量 1000" 或 "数量1000" 或 "数量：1000"
        Pattern pattern = Pattern.compile(
            label + "[\\s：:]*([\\d,]+\\.?\\d*)"
        );
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String numStr = matcher.group(1).replace(",", "");
            return new BigDecimal(numStr);
        }
        return null;
    }
    
    private boolean isValidRecord(OcrTradeRecord record) {
        return record.getType() != null
            && record.getPrice() != null
            && record.getQuantity() != null;
    }
}
```

#### 4.3.5 网格线匹配服务

```java
@Service
public class GridLineMatcher {

    private final GridLineRepository gridLineRepository;
    private final TradeRecordRepository tradeRecordRepository;

    /**
     * 为识别出的交易记录匹配网格线
     */
    public void matchGridLines(List<OcrTradeRecord> records, Long strategyId) {
        // 获取策略的所有网格线
        List<GridLine> gridLines = gridLineRepository.findByStrategyId(strategyId);
        
        // 获取已有交易记录（用于去重）
        List<TradeRecord> existingRecords = tradeRecordRepository
            .findByStrategyId(strategyId);
        
        for (OcrTradeRecord record : records) {
            // 1. 检查是否重复
            if (isDuplicate(record, existingRecords)) {
                record.setMatchStatus("DUPLICATE");
                record.setMatchMessage("该记录已存在，将跳过");
                continue;
            }
            
            // 2. 匹配网格线
            GridLine matched = findMatchingGridLine(
                record, gridLines, record.getType()
            );
            
            if (matched != null) {
                record.setMatchedGridLineId(matched.getId());
                record.setMatchedLevel(matched.getLevel());
                record.setMatchStatus("MATCHED");
                record.setMatchMessage("匹配网格第" + matched.getLevel() + "层");
            } else {
                record.setMatchStatus("UNMATCHED");
                record.setMatchMessage("未找到匹配的网格线，需手动选择");
            }
        }
    }

    /**
     * 根据价格匹配网格线
     * 
     * 匹配规则：
     * - 买入：找 buyTriggerPrice 最接近的网格线
     * - 卖出：找 sellTriggerPrice 最接近的网格线
     * - 允许误差范围：0.5%
     */
    private GridLine findMatchingGridLine(
        OcrTradeRecord record, 
        List<GridLine> gridLines,
        String type
    ) {
        BigDecimal targetPrice = record.getPrice();
        BigDecimal tolerance = targetPrice.multiply(new BigDecimal("0.005")); // 0.5%
        
        return gridLines.stream()
            .filter(gl -> {
                BigDecimal gridPrice = "BUY".equals(type) 
                    ? gl.getBuyTriggerPrice() 
                    : gl.getSellTriggerPrice();
                BigDecimal diff = targetPrice.subtract(gridPrice).abs();
                return diff.compareTo(tolerance) <= 0;
            })
            .min(Comparator.comparing(gl -> {
                BigDecimal gridPrice = "BUY".equals(type)
                    ? gl.getBuyTriggerPrice()
                    : gl.getSellTriggerPrice();
                return targetPrice.subtract(gridPrice).abs();
            }))
            .orElse(null);
    }

    /**
     * 检查是否为重复记录
     * 判断条件：类型相同 + 时间相同 + 价格相同
     */
    private boolean isDuplicate(OcrTradeRecord newRecord, List<TradeRecord> existing) {
        return existing.stream().anyMatch(r -> 
            r.getType().name().equals(newRecord.getType())
            && r.getPrice().compareTo(newRecord.getPrice()) == 0
            && r.getTradeTime().toString().equals(newRecord.getTradeTime())
        );
    }
}
```

#### 4.3.6 导入服务

```java
@Service
public class ImportService {

    private final GridEngine gridEngine;
    private final GridLineRepository gridLineRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final StrategyRepository strategyRepository;

    /**
     * 批量导入交易记录
     */
    @Transactional
    public Map<String, Object> batchImport(BatchImportRequest request) {
        int successCount = 0;
        int skipCount = 0;
        List<String> errors = new ArrayList<>();
        
        Strategy strategy = strategyRepository.findById(request.getStrategyId())
            .orElseThrow(() -> new IllegalArgumentException("策略不存在"));
        
        for (OcrTradeRecord ocrRecord : request.getRecords()) {
            // 跳过重复记录
            if ("DUPLICATE".equals(ocrRecord.getMatchStatus())) {
                skipCount++;
                continue;
            }
            
            // 跳过未匹配且未手动选择的记录
            if (ocrRecord.getMatchedGridLineId() == null) {
                errors.add("记录 " + ocrRecord.getTradeTime() + " 未选择网格线");
                continue;
            }
            
            try {
                // 创建交易记录
                TradeRecord record = new TradeRecord();
                record.setStrategy(strategy);
                record.setGridLine(gridLineRepository.findById(
                    ocrRecord.getMatchedGridLineId()).orElseThrow());
                record.setType(TradeType.valueOf(ocrRecord.getType()));
                record.setPrice(ocrRecord.getPrice());
                record.setAmount(ocrRecord.getAmount());
                record.setQuantity(ocrRecord.getQuantity());
                record.setFee(ocrRecord.getFee());
                record.setTradeTime(LocalDateTime.parse(
                    ocrRecord.getTradeTime(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                ));
                
                tradeRecordRepository.save(record);
                successCount++;
                
            } catch (Exception e) {
                errors.add("导入失败: " + ocrRecord.getTradeTime() + " - " + e.getMessage());
            }
        }
        
        return Map.of(
            "success", true,
            "successCount", successCount,
            "skipCount", skipCount,
            "errors", errors
        );
    }
}
```

---

## 五、前端实现方案

### 5.1 新增组件

```
frontend/src/components/
├── OcrImport/
│   ├── ImageUploader.vue      # 图片上传组件
│   ├── OcrResultPreview.vue   # 识别结果预览
│   ├── RecordEditor.vue       # 单条记录编辑
│   └── ImportConfirm.vue      # 导入确认对话框
```

### 5.2 图片上传组件 (ImageUploader.vue)

```vue
<template>
  <div class="ocr-uploader">
    <el-upload
      ref="uploadRef"
      :auto-upload="false"
      :show-file-list="false"
      :on-change="handleFileChange"
      accept="image/*"
      drag
    >
      <template v-if="!previewUrl">
        <el-icon class="upload-icon"><Upload /></el-icon>
        <div class="upload-text">
          拖拽截图到此处，或<em>点击上传</em>
        </div>
        <div class="upload-tip">
          支持东方财富APP持仓明细截图
        </div>
      </template>
      <template v-else>
        <img :src="previewUrl" class="preview-image" />
      </template>
    </el-upload>
    
    <div class="actions" v-if="previewUrl">
      <el-button @click="clearImage">重新选择</el-button>
      <el-button type="primary" @click="startRecognize" :loading="loading">
        开始识别
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import { recognizeImage } from '@/api'

const props = defineProps({
  strategyId: { type: Number, required: true }
})

const emit = defineEmits(['recognized'])

const uploadRef = ref()
const previewUrl = ref('')
const currentFile = ref(null)
const loading = ref(false)

function handleFileChange(file) {
  currentFile.value = file.raw
  previewUrl.value = URL.createObjectURL(file.raw)
}

function clearImage() {
  previewUrl.value = ''
  currentFile.value = null
}

async function startRecognize() {
  if (!currentFile.value) return
  
  loading.value = true
  try {
    const formData = new FormData()
    formData.append('file', currentFile.value)
    formData.append('strategyId', props.strategyId)
    formData.append('brokerType', 'EASTMONEY')
    
    const response = await recognizeImage(formData)
    emit('recognized', response.data)
  } catch (error) {
    ElMessage.error('识别失败：' + error.message)
  } finally {
    loading.value = false
  }
}
</script>
```

### 5.3 识别结果预览组件 (OcrResultPreview.vue)

```vue
<template>
  <div class="ocr-result">
    <el-alert 
      v-if="result.message" 
      :type="result.success ? 'success' : 'error'"
      :title="result.message"
    />
    
    <div class="summary">
      识别到 <b>{{ result.totalCount }}</b> 条记录，
      匹配成功 <b>{{ result.matchedCount }}</b> 条
    </div>
    
    <el-table :data="result.records" stripe>
      <el-table-column prop="type" label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.type === 'BUY' ? 'success' : 'danger'">
            {{ row.type === 'BUY' ? '买入' : '卖出' }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column prop="tradeTime" label="时间" width="160" />
      <el-table-column prop="price" label="价格" width="100" />
      <el-table-column prop="quantity" label="数量" width="100" />
      <el-table-column prop="amount" label="金额" width="100" />
      <el-table-column prop="fee" label="费用" width="80" />
      
      <el-table-column label="匹配状态" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.matchStatus === 'MATCHED'" type="success">
            {{ row.matchMessage }}
          </el-tag>
          <el-tag v-else-if="row.matchStatus === 'DUPLICATE'" type="info">
            已存在
          </el-tag>
          <el-select 
            v-else 
            v-model="row.matchedGridLineId" 
            placeholder="选择网格"
            size="small"
          >
            <el-option 
              v-for="gl in gridLines" 
              :key="gl.id" 
              :label="`第${gl.level}层 ${gl.buyTriggerPrice}`"
              :value="gl.id"
            />
          </el-select>
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="80">
        <template #default="{ row, $index }">
          <el-button 
            type="danger" 
            size="small" 
            @click="removeRecord($index)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <div class="actions">
      <el-button @click="$emit('cancel')">取消</el-button>
      <el-button type="primary" @click="confirmImport">
        确认导入 ({{ validCount }} 条)
      </el-button>
    </div>
  </div>
</template>
```

### 5.4 API接口 (api.js 新增)

```javascript
/**
 * OCR识别图片
 */
export function recognizeImage(formData) {
  return api.post('/ocr/recognize', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 批量导入交易记录
 */
export function batchImportRecords(data) {
  return api.post('/ocr/import', data)
}
```

---

## 六、百度OCR接入指南

### 6.1 申请步骤

1. 访问 [百度AI开放平台](https://ai.baidu.com/)
2. 注册/登录账号
3. 进入控制台 → 文字识别
4. 创建应用，获取 `APP_ID`、`API_KEY`、`SECRET_KEY`
5. 开通"通用文字识别（标准版）"或"通用文字识别（高精度版）"

### 6.2 免费额度

| 接口 | 免费调用量 | 说明 |
|------|-----------|------|
| 通用文字识别（标准版） | 1000次/月 | 适合普通截图 |
| 通用文字识别（高精度版） | 500次/月 | 适合复杂场景 |

### 6.3 调用示例

```java
import com.baidu.aip.ocr.AipOcr;
import org.json.JSONObject;

public class BaiduOcrDemo {
    public static void main(String[] args) {
        // 初始化客户端
        AipOcr client = new AipOcr("APP_ID", "API_KEY", "SECRET_KEY");
        
        // 调用通用文字识别（高精度版）
        JSONObject result = client.accurateGeneral(
            "图片路径或字节数组", 
            new HashMap<>()
        );
        
        // 解析结果
        // result.getJSONArray("words_result") 包含识别的文字
    }
}
```

---

## 七、文件变更清单

### 7.1 后端新增文件

```
backend/src/main/java/com/gridtrading/
├── controller/
│   └── OcrController.java           # OCR接口控制器
├── service/
│   ├── OcrService.java              # OCR识别服务
│   ├── ImportService.java           # 导入服务
│   ├── GridLineMatcher.java         # 网格匹配服务
│   └── parser/
│       ├── BrokerParser.java        # 券商解析器接口
│       └── EastMoneyParser.java     # 东方财富解析器
├── dto/
│   ├── OcrRecognizeRequest.java
│   ├── OcrRecognizeResponse.java
│   ├── OcrTradeRecord.java
│   └── BatchImportRequest.java
```

### 7.2 后端修改文件

```
backend/pom.xml                       # 新增百度OCR和文件上传依赖
backend/src/main/resources/application.yml  # 新增百度OCR配置
```

### 7.3 前端新增文件

```
frontend/src/components/OcrImport/
├── ImageUploader.vue
├── OcrResultPreview.vue
├── RecordEditor.vue
└── ImportConfirm.vue

frontend/src/views/
└── OcrImportView.vue                 # OCR导入页面（或集成到策略详情）
```

### 7.4 前端修改文件

```
frontend/src/api.js                   # 新增OCR相关API
frontend/src/router.js                # 新增路由（如独立页面）
frontend/src/views/StrategyDetail.vue # 新增导入按钮入口
```

---

## 八、扩展性设计

### 8.1 支持更多券商

通过 `BrokerParser` 接口扩展：

```java
public interface BrokerParser {
    String getBrokerType();
    List<OcrTradeRecord> parse(String rawText);
}

// 可扩展实现：
// - EastMoneyParser    (东方财富)
// - TongHuaShunParser  (同花顺)
// - PinAnParser        (平安证券)
// - HuaTaiParser       (华泰证券)
```

### 8.2 支持备选OCR方案

```java
public interface OcrProvider {
    String recognize(byte[] imageBytes);
}

// 可选实现：
// - BaiduOcrProvider     (百度OCR)
// - TencentOcrProvider   (腾讯OCR)
// - TesseractProvider    (本地Tesseract)
```

---

## 九、实施计划

| 阶段 | 任务 | 工时估算 |
|------|------|----------|
| **阶段1** | 百度OCR接入 + 基础识别 | 2-3小时 |
| **阶段2** | 东方财富文本解析器 | 2-3小时 |
| **阶段3** | 网格线匹配逻辑 | 1-2小时 |
| **阶段4** | 后端API完整实现 | 2-3小时 |
| **阶段5** | 前端上传和预览组件 | 3-4小时 |
| **阶段6** | 联调测试 + 优化 | 2-3小时 |
| **总计** | | 12-18小时 |

---

## 十、注意事项

1. **隐私安全**：图片仅用于OCR识别，不做持久化存储
2. **调用限额**：注意百度OCR免费额度，可考虑缓存识别结果
3. **识别准确率**：建议用户确认后再导入，避免错误数据
4. **去重机制**：通过时间+价格+类型判断重复记录
5. **网格匹配**：允许0.5%价格误差，未匹配的需手动选择
