<template>
  <div class="strategy-create">
    <div class="header">
      <h1>创建固定模板网格策略</h1>
      <el-button @click="goBack">返回</el-button>
    </div>

    <el-alert
      title="固定模板说明"
      type="info"
      :closable="false"
      style="max-width: 600px; margin-bottom: 20px"
    >
      本策略使用固定模板：19条网格，网格类型为 SMALL(5%)、MEDIUM(15%)、LARGE(30%)，按预设顺序自动生成。
    </el-alert>

    <el-form
      :model="form"
      label-width="120px"
      style="max-width: 600px"
      ref="formRef"
    >
      <el-form-item label="策略名称" required>
        <el-input v-model="form.name" placeholder="请输入策略名称" />
      </el-form-item>

      <el-form-item label="证券代码" required>
        <el-input v-model="form.symbol" placeholder="例如：BTC/USDT" />
      </el-form-item>

      <el-form-item label="基准价" required>
        <el-input-number
          v-model="form.basePrice"
          :precision="2"
          :min="0.01"
          :step="0.01"
          placeholder="请输入基准价"
          style="width: 100%"
        />
        <div class="help-text">第1条网格的买入价格</div>
      </el-form-item>

      <el-form-item label="单格金额" required>
        <el-input-number
          v-model="form.amountPerGrid"
          :precision="2"
          :min="0.01"
          :step="1"
          placeholder="每格投入金额"
          style="width: 100%"
        />
        <div class="help-text">每条网格投入的金额（固定19条，总投资 = 单格金额 × 19）</div>
      </el-form-item>

      <el-divider>网格模板信息（自动生成）</el-divider>

      <el-descriptions :column="1" border style="margin-bottom: 20px">
        <el-descriptions-item label="网格总数">19条（固定）</el-descriptions-item>
        <el-descriptions-item label="网格类型">
          <el-tag type="success" size="small">SMALL (5%)</el-tag>
          <el-tag type="warning" size="small" style="margin-left: 5px">MEDIUM (15%)</el-tag>
          <el-tag type="danger" size="small" style="margin-left: 5px">LARGE (30%)</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="类型分布">
          SMALL: 13条 | MEDIUM: 4条 | LARGE: 2条
        </el-descriptions-item>
        <el-descriptions-item label="最大投资" v-if="form.amountPerGrid">
          {{ (form.amountPerGrid * 19).toFixed(2) }} 元
        </el-descriptions-item>
      </el-descriptions>

      <el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          创建策略
        </el-button>
        <el-button @click="goBack">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createStrategy } from '../api'

const router = useRouter()
const submitting = ref(false)

const form = ref({
  name: '',
  symbol: '',
  basePrice: null,
  amountPerGrid: null
})

// 返回列表页
const goBack = () => {
  router.push('/')
}

// 提交创建
const handleSubmit = async () => {
  // 基本校验
  if (!form.value.name) {
    ElMessage.warning('请输入策略名称')
    return
  }
  if (!form.value.symbol) {
    ElMessage.warning('请输入证券代码')
    return
  }
  if (!form.value.basePrice || form.value.basePrice <= 0) {
    ElMessage.warning('请输入有效的基准价')
    return
  }
  if (!form.value.amountPerGrid || form.value.amountPerGrid <= 0) {
    ElMessage.warning('请输入有效的单格金额')
    return
  }

  submitting.value = true
  try {
    const response = await createStrategy(form.value)
    ElMessage.success('策略创建成功')

    // 跳转到策略详情页
    router.push(`/strategy/${response.data.id}`)
  } catch (error) {
    console.error('创建策略失败:', error)
    ElMessage.error(error.response?.data?.message || '创建策略失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.strategy-create {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h1 {
  margin: 0;
  font-size: 24px;
}

.help-text {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}
</style>
