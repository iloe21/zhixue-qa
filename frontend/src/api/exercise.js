import request from '../utils/request'

/**
 * 练习 API（对应后端 /api/exercises）
 * 出题需调大模型（约 10~40 秒），生成接口单独放宽超时到 120 秒
 */
export const generateExercise = data => request.post('/exercises', data, { timeout: 120000 })

export const listExercises = () => request.get('/exercises')

export const getExercise = id => request.get(`/exercises/${id}`)

export const submitExercise = (id, answers) =>
  request.post(`/exercises/${id}/submit`, { answers }, { timeout: 30000 })
