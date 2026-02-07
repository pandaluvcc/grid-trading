<template>
  <div class="strategy-list">
    <div class="header">
      <h1>策略列表</h1>
      <el-button type="primary" @click="goToCreate">创建新策略</el-button>
    </div>

    <el-table :data="strategies" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="策略名称" width="200" />
      <el-table-column prop="symbol" label="证券代码" width="150" />
      <el-table-column prop="basePrice" label="基准价" width="120">
        <template #default="scope">
          {{ formatPrice(scope.row.basePrice) }}
        </template>
      </el-table-column>
      <el-table-column prop="amountPerGrid" label="单格金额" width="120">
        <template #default="scope">
          {{ formatAmount(scope.row.amountPerGrid) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'RUNNING' ? 'success' : 'info'">
            {{ scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="scope">
          {{ formatDate(scope.row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="200">
        <template #default="scope">
          <el-button size="small" type="primary" @click="goToDetail(scope.row.id)">
            查看详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAllStrategies } from '../api'

const router = useRouter()
const strategies = ref([])
const loading = ref(false)

// 加载策略列表
const loadStrategies = async () => {
  loading.value = true
  try {
    const response = await getAllStrategies()
    strategies.value = response.data
  } catch (error) {
    console.error('加载策略列表失败:', error)
    ElMessage.error('加载策略列表失败')
  } finally {
    loading.value = false
  }
}

// 跳转到创建页
const goToCreate = () => {
  router.push('/create')
}

// 跳转到详情页
const goToDetail = (id) => {
  router.push(`/strategy/${id}`)
}

// 格式化价格
const formatPrice = (value) => {
  if (value == null) return '-'
  return Number(value).toFixed(2)
}

// 格式化金额
const formatAmount = (value) => {
  if (value == null) return '-'
  return Number(value).toFixed(2)
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  loadStrategies()
})
</script>

<style scoped>
.strategy-list {
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
</style>
