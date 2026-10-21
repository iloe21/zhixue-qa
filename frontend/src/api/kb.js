import request from '../utils/request'

/**
 * 知识库与文档 API（对应后端 /api/kbs、/api/docs、/api/chunks）
 * request 拦截器已解包，返回值直接是业务数据
 */

// ===== 知识库 =====
export const listKbs = () => request.get('/kbs')

export const createKb = data => request.post('/kbs', data)

export const getKb = id => request.get(`/kbs/${id}`)

export const updateKb = (id, data) => request.put(`/kbs/${id}`, data)

export const deleteKb = id => request.delete(`/kbs/${id}`)

// ===== 文档 =====
export const uploadDoc = (kbId, file, onProgress) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`/kbs/${kbId}/docs`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}

export const listDocs = kbId => request.get(`/kbs/${kbId}/docs`)

export const deleteDoc = id => request.delete(`/docs/${id}`)

export const reparseDoc = id => request.post(`/docs/${id}/reparse`)

// ===== 片段 =====
export const listChunks = (docId, page = 1, size = 5) =>
  request.get(`/docs/${docId}/chunks`, { params: { page, size } })

export const getChunk = id => request.get(`/chunks/${id}`)
