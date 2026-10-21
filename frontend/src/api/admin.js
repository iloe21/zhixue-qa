import request from '../utils/request'

/**
 * 管理后台 API（对应后端 /api/admin，仅 admin 角色）
 */
export const getStats = () => request.get('/admin/stats')

export const listUsers = (page = 1, size = 10, keyword = '') =>
  request.get('/admin/users', { params: { page, size, keyword: keyword || undefined } })

export const updateUserStatus = (id, status) =>
  request.put(`/admin/users/${id}/status`, { status })

export const updateUserRole = (id, role) =>
  request.put(`/admin/users/${id}/role`, { role })
