import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * Axios 封装（参考项目踩坑经验）：
 * 1. 请求拦截器自动携带 JWT
 * 2. 响应拦截器【已解包】：业务代码里拿到的直接是 data 字段内容
 * 3. 401 自动清除登录态并跳转登录页（带防重复跳转）
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// ===== 请求拦截：携带 token =====
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ===== 响应拦截：统一解包 + 错误提示 =====
let redirecting = false

request.interceptors.response.use(
  response => {
    const body = response.data
    // 标准响应体 {code, message, data}
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 200) {
        ElMessage.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body.data   // ← 已解包：调用方直接拿业务数据
    }
    return body
  },
  error => {
    const status = error.response?.status
    const msg = error.response?.data?.message || error.message

    if (status === 401) {
      // 登录过期：清登录态 → 跳登录页（防止重复跳转/重复弹错）
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      const current = router.currentRoute.value.path
      if (!redirecting && current !== '/login' && current !== '/register') {
        redirecting = true
        ElMessage.error('登录已过期，请重新登录')
        router.push('/login').finally(() => { redirecting = false })
      }
    } else {
      ElMessage.error(msg || '网络错误，请检查后端服务是否启动')
    }
    return Promise.reject(error)
  }
)

export default request
