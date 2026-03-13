import axios from 'axios'

// 动态获取后端地址
const getBaseURL = () => {
  // 生产环境：使用相对路径，由 Nginx 反向代理到后端
  if (import.meta.env.PROD) {
    return '/api'
  }
  // 开发环境：与前端同主机，端口8080
  const host = window.location.hostname || 'localhost'
  return `http://${host}:8080/api`
}

// 创建 axios 实例
const api = axios.create({
  baseURL: getBaseURL(),
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export default api
