<template>
  <div class="apk-generator">
    <div class="page-header">
      <h1>APK关键词文件夹生成器</h1>
    </div>

    <div class="form-container">
      <!-- Excel文件上传区域 -->
      <div class="upload-section">
        <div class="section-title">
          <el-icon><Document /></el-icon>
          <span>上传Excel文件</span>
        </div>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls"
          :on-change="handleFileChange"
          class="upload-area"
          :class="{ 'has-file': form.file }"
        >
          <div class="upload-content">
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="upload-text">
              <div class="upload-main">点击或拖拽文件到此处</div>
              <div class="upload-sub">支持 .xlsx, .xls 格式，第一行为表头</div>
            </div>
          </div>
        </el-upload>
        <div v-if="form.file" class="file-selected">
          <el-icon class="file-icon"><CircleCheck /></el-icon>
          <span class="file-name">{{ form.file.name }}</span>
          <el-button link type="danger" size="small" @click="clearFile">更换</el-button>
        </div>
      </div>

      <!-- 目标路径选择区域 -->
      <div class="path-section">
        <div class="section-title">
          <el-icon><FolderOpened /></el-icon>
          <span>选择目标路径</span>
        </div>
        <div class="path-input-wrapper">
          <el-input v-model="form.targetPath" placeholder="例如: D:\apk_folders" size="large" class="path-input" />
          <el-button type="primary" size="large" class="path-button" @click="selectTargetPath">
            <el-icon><Folder /></el-icon>
            选择文件夹
          </el-button>
        </div>
        <div class="path-tip">
          <el-icon><InfoFilled /></el-icon>
          APK文件也从此路径下获取（不遍历子文件夹）
        </div>
      </div>

      <!-- 操作按钮区域 -->
      <div class="action-section">
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          :disabled="!canSubmit"
          class="action-button primary"
          @click="handleGenerate"
        >
          <el-icon><VideoPlay /></el-icon>
          开始生成
        </el-button>
        <el-button size="large" class="action-button secondary" @click="showExportDialog">
          <el-icon><Download /></el-icon>
          导出知识库文件
        </el-button>
      </div>
    </div>

    <div v-if="result" class="result-container">
      <el-card>
        <template #header>
          <div class="result-header">
            <span>处理结果</span>
            <el-tag type="success">完成</el-tag>
          </div>
        </template>

        <div class="result-stats">
          <div class="stat-item">
            <span class="label">总关键词数</span>
            <span class="value">{{ result.totalKeywords }}</span>
          </div>
          <div class="stat-item">
            <span class="label">过滤后</span>
            <span class="value">{{ result.filteredKeywords }}</span>
          </div>
          <div class="stat-item">
            <span class="label">创建文件夹</span>
            <span class="value">{{ result.createdFolders }}</span>
          </div>
          <div class="stat-item">
            <span class="label">跳过文件夹</span>
            <span class="value">{{ result.skippedFolders }}</span>
          </div>
          <div class="stat-item">
            <span class="label">复制文件数</span>
            <span class="value">{{ result.copiedApkCount }}</span>
          </div>
        </div>

        <div v-if="result.keywords && result.keywords.length > 0" class="keyword-list">
          <div class="keyword-list-header">关键词列表（按搜索量排序，显示前20个）</div>
          <el-table :data="result.keywords" stripe max-height="400">
            <el-table-column prop="keyword" label="关键词" min-width="150" />
            <el-table-column prop="monthlySearchVolume" label="月均搜索量" width="120" />
            <el-table-column prop="feature" label="特色" min-width="100" />
            <el-table-column prop="recommendedPrice" label="推荐出价" width="100" />
            <el-table-column prop="competitionLevel" label="竞争激烈程度" width="120" />
          </el-table>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="exportDialogVisible" title="导出知识库文件" width="500px">
      <div class="export-dialog-content">
        <div class="export-upload-area">
          <el-upload
            ref="exportUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".csv"
            :on-change="handleExportFileChange"
            class="export-upload"
          >
            <el-button type="primary">选择CSV文件</el-button>
          </el-upload>
          <div v-if="exportFile" class="file-selected-small">
            <el-icon color="#67c23a"><CircleCheck /></el-icon>
            <span>{{ exportFile.name }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="exportLoading" :disabled="!exportFile" @click="handleExport">
          导出
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Document,
  UploadFilled,
  CircleCheck,
  FolderOpened,
  Folder,
  InfoFilled,
  VideoPlay,
  Download
} from '@element-plus/icons-vue'
import axios from 'axios'

const form = ref({
  file: null,
  targetPath: ''
})

const loading = ref(false)
const result = ref(null)
const uploadRef = ref(null)
const isMobile = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

const exportDialogVisible = ref(false)
const exportFile = ref(null)
const exportUploadRef = ref(null)
const exportLoading = ref(false)

const canSubmit = computed(() => {
  return form.value.file && form.value.targetPath
})

const handleFileChange = (uploadFile) => {
  if (uploadFile) {
    form.value.file = uploadFile.raw
  }
}

const clearFile = () => {
  form.value.file = null
  uploadRef.value?.clearFiles()
}

const selectTargetPath = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.webkitdirectory = true
  input.onchange = (e) => {
    const files = e.target.files
    if (files && files.length > 0) {
      const path = files[0].webkitRelativePath.split('/')[0]
      form.value.targetPath = path
    }
  }
  input.click()
}

const handleGenerate = async () => {
  if (!canSubmit.value) {
    ElMessage.warning('请填写所有必填项')
    return
  }

  loading.value = true
  result.value = null

  try {
    const formData = new FormData()
    formData.append('file', form.value.file)
    formData.append('targetPath', form.value.targetPath)

    const response = await axios.post('/api/tools/apk-folder-generator', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    result.value = response.data
    ElMessage.success('处理完成！')
  } catch (error) {
    console.error('处理失败:', error)
    ElMessage.error(error.response?.data?.message || error.message || '处理失败')
  } finally {
    loading.value = false
  }
}

const showExportDialog = () => {
  exportFile.value = null
  exportDialogVisible.value = true
}

const handleExportFileChange = (uploadFile) => {
  if (uploadFile) {
    exportFile.value = uploadFile.raw
  }
}

const handleExport = async () => {
  if (!exportFile.value) {
    ElMessage.warning('请选择CSV文件')
    return
  }

  exportLoading.value = true

  try {
    const formData = new FormData()
    formData.append('file', exportFile.value)

    const response = await axios.post('/api/tools/knowledge-base-export', formData, {
      responseType: 'blob',
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '知识库_' + Date.now() + '.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('导出成功！')
    exportDialogVisible.value = false
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error(error.response?.data?.message || error.message || '导出失败')
  } finally {
    exportLoading.value = false
  }
}
</script>

<style scoped>
.apk-generator {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  margin-bottom: 28px;
}

.page-header h1 {
  font-size: 26px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
  letter-spacing: -0.3px;
}

.form-container {
  background: #fff;
  padding: 32px;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  margin-bottom: 24px;
}

/* 上传区域 */
.upload-section {
  margin-bottom: 28px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}

.section-title .el-icon {
  color: #667eea;
  font-size: 18px;
}

.upload-area {
  width: 100%;
}

.upload-area :deep(.el-upload) {
  width: 100%;
}

.upload-content {
  width: 100%;
  padding: 32px 24px;
  border: 2px dashed #d1d5db;
  border-radius: 12px;
  background: #fafbfc;
  text-align: center;
  transition: all 0.3s ease;
  cursor: pointer;
}

.upload-area:hover .upload-content {
  border-color: #667eea;
  background: #f5f3ff;
}

.upload-area.has-file .upload-content {
  border-color: #67c23a;
  background: #f0fdf4;
}

.upload-icon {
  font-size: 48px;
  color: #9ca3af;
  margin-bottom: 12px;
  transition: color 0.3s ease;
}

.upload-area:hover .upload-icon {
  color: #667eea;
}

.upload-area.has-file .upload-icon {
  color: #67c23a;
}

.upload-main {
  font-size: 16px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}

.upload-sub {
  font-size: 13px;
  color: #6b7280;
}

.file-selected {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding: 12px 16px;
  background: #f0fdf4;
  border-radius: 10px;
  border: 1px solid #86efac;
}

.file-icon {
  font-size: 20px;
  color: #67c23a;
}

.file-selected .file-name {
  flex: 1;
  font-size: 14px;
  color: #166534;
  font-weight: 500;
}

/* 路径选择区域 */
.path-section {
  margin-bottom: 32px;
}

.path-input-wrapper {
  display: flex;
  gap: 12px;
}

.path-input {
  flex: 1;
}

.path-button {
  white-space: nowrap;
  padding: 0 20px;
}

.path-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 13px;
  color: #6b7280;
}

.path-tip .el-icon {
  color: #667eea;
}

/* 操作按钮区域 */
.action-section {
  display: flex;
  gap: 16px;
  padding-top: 8px;
}

.action-button {
  flex: 1;
  height: 48px;
  font-size: 15px;
  font-weight: 500;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.3s ease;
}

.action-button.primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 14px rgba(102, 126, 234, 0.3);
}

.action-button.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.action-button.secondary {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  color: #374151;
}

.action-button.secondary:hover {
  background: #f3f4f6;
  border-color: #d1d5db;
}

/* 结果区域 */
.result-container {
  margin-top: 24px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
}

.result-stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-item {
  text-align: center;
  padding: 20px 12px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  transition: transform 0.2s ease;
}

.stat-item:hover {
  transform: translateY(-2px);
}

.stat-item .label {
  display: block;
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
  font-weight: 500;
}

.stat-item .value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: #667eea;
  letter-spacing: -1px;
}

.keyword-list {
  margin-top: 24px;
}

.keyword-list-header {
  font-size: 15px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f1f5f9;
}

/* 导出弹窗 */
.export-dialog-content {
  padding: 10px 0;
}

.export-upload-area {
  text-align: center;
}

.file-selected-small {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 16px;
  background: #f0fdf4;
  border-radius: 8px;
  font-size: 14px;
  color: #166534;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .apk-generator {
    padding: 16px;
    max-width: 100%;
  }

  .page-header {
    margin-bottom: 20px;
  }

  .page-header h1 {
    font-size: 20px;
  }

  .form-container {
    padding: 20px 16px;
    border-radius: 14px;
  }

  .upload-content {
    padding: 24px 16px;
  }

  .upload-icon {
    font-size: 40px;
  }

  .upload-main {
    font-size: 14px;
  }

  .upload-sub {
    font-size: 12px;
  }

  .path-input-wrapper {
    flex-direction: column;
    gap: 10px;
  }

  .path-button {
    width: 100%;
  }

  .action-section {
    flex-direction: column;
    gap: 12px;
  }

  .action-button {
    width: 100%;
  }

  .result-stats {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .stat-item {
    padding: 16px 10px;
  }

  .stat-item .label {
    font-size: 12px;
    margin-bottom: 6px;
  }

  .stat-item .value {
    font-size: 22px;
  }

  .keyword-list-header {
    font-size: 14px;
  }

  .keyword-list {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .keyword-list .el-table {
    min-width: 600px;
  }

  :deep(.el-dialog) {
    width: 90% !important;
    margin: 5vh auto !important;
  }
}

@media (max-width: 480px) {
  .result-stats {
    grid-template-columns: 1fr;
  }
}
</style>
