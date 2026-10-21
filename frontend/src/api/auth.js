import request from '../utils/request'

/**
 * 认证相关 API（对应后端 /api/auth）
 * 注意：request 拦截器已解包，函数返回值直接是业务数据
 */
export const getCaptcha = () => request.get('/auth/captcha')

export const login = data => request.post('/auth/login', data)

export const register = data => request.post('/auth/register', data)

export const forgotPassword = data => request.post('/auth/forgot-password', data)

export const resetPassword = data => request.post('/auth/reset-password', data)

export const getMe = () => request.get('/auth/me')

export const updateMe = data => request.put('/auth/me', data)

export const changePassword = data => request.put('/auth/password', data)
