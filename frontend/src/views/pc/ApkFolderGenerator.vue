<template>
  <div class="apk-generator">
    <div class="page-header">
      <h1>APK关键词文件夹生成器</h1>
    </div>

    <div class="form-container">
      <el-form :model="form" label-width="100px">
        <el-form-item label="Excel文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :on-change="handleFileChange"
          >
            <el-button type="primary">选择Excel文件</el-button>
            <template #tip>
              只支持 .xlsx, .xls 格式，第一行为表头
            </template>
          </el-upload>
          <div v-if="form.file" class="file-name">
            已选择: {{ form.file.name }}
          </div>
        </el-form-item>

        <el-form-item label="目标路径">
          <el-input v-model="form.targetPath" placeholder="例如: D:\apk_folders">
            <template #append>
              <el-button @click="selectTargetPath">选择文件夹</el-button>
            </template>
          </el-input>
          <div class="path-tip">
            APK文件也从此路径下获取（不遍历子文件夹）
          </div>
        </el-form-item>

        <el-form-item>
          <el-button 
            type="primary" 
            size="large"
            :loading="loading"
            :disabled="!canSubmit"
            @click="handleGenerate"
          >
            开始生成
          </el-button>
          <el-button 
            size="large"
            @click="showExportDialog"
          >
            导出知识库文件
          </el-button>
        </el-form-item>
      </el-form>
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
          <div class="keyword-list-header">
            关键词列表（按搜索量排序，显示前20个）
          </div>
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
      <el-form label-width="100px">
        <el-form-item label="CSV文件">
          <el-upload
            ref="exportUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".csv"
            :on-change="handleExportFileChange"
          >
            <el-button type="primary">选择CSV文件</el-button>
          </el-upload>
          <div v-if="exportFile" class="file-name">
            已选择: {{ exportFile.name }}
          </div>
        </el-form-item>
      </el-form>
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
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const form = ref({
  file: null,
  targetPath: ''
})

const loading = ref(false)
const result = ref(null)
const uploadRef = ref(null)

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

    const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
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
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 30px;
}

.page-header h1 {
  font-size: 24px;
  color: #303133;
  margin: 0;
}

.form-container {
  background: #fff;
  padding: 30px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
}

.file-name {
  margin-top: 10px;
  color: #67c23a;
  font-size: 14px;
}

.path-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.result-container {
  margin-top: 20px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
  font-weight: bold;
}

.result-stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.stat-item {
  text-align: center;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-item .label {
  display: block;
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-item .value{
  display: block;
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
}

.keyword-list {
  margin-top: 20px;
}

.keyword-list-header {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}
</style>
