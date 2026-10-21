import request from '../utils/request'

/**
 * 会话与问答 API（对应后端 /api/conversations）
 */
export const createConversation = data => request.post('/conversations', data)

export const listConversations = () => request.get('/conversations')

export const getConversation = id => request.get(`/conversations/${id}`)

export const renameConversation = (id, title) => request.put(`/conversations/${id}`, { title })

export const deleteConversation = id => request.delete(`/conversations/${id}`)

export const listMessages = id => request.get(`/conversations/${id}/messages`)

/**
 * SSE 流式问答（POST + fetch 逐块读取，EventSource 不支持 POST 所以用 fetch）
 *
 * 后端事件协议：delta(增量文本) / citations(引用JSON) / done(结束) / error(错误)
 * handlers: { onDelta, onCitations, onDone, onError }
 */
export async function chatStream(convId, question, handlers) {
  const { onDelta, onCitations, onDone, onError } = handlers
  const token = localStorage.getItem('token')

  let resp
  try {
    resp = await fetch(`/api/conversations/${convId}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({ question })
    })
  } catch (e) {
    onError?.('网络错误，请检查后端服务')
    return
  }

  if (!resp.ok) {
    let msg = `请求失败（${resp.status}）`
    try {
      const body = await resp.json()
      if (body?.message) msg = body.message
    } catch (e) { /* 非 JSON 响应 */ }
    if (resp.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
      return
    }
    onError?.(msg)
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let doneReceived = false

  const handlePart = (part) => {
    if (!part.trim()) return
    let eventName = 'message'
    const dataLines = []
    for (const line of part.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''))
      }
    }
    const data = dataLines.join('\n')

    if (eventName === 'delta') {
      onDelta?.(data)
    } else if (eventName === 'citations') {
      try { onCitations?.(JSON.parse(data)) } catch (e) { onCitations?.([]) }
    } else if (eventName === 'done') {
      doneReceived = true
      onDone?.()
    } else if (eventName === 'error') {
      doneReceived = true
      onError?.(data || 'AI 服务调用失败')
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 事件以空行分隔
      const parts = buffer.split('\n\n')
      buffer = parts.pop()
      parts.forEach(handlePart)
    }
    // 冲刷残余缓冲
    if (buffer.trim()) handlePart(buffer)
  } catch (e) {
    // 流被中断（网络波动/连接重置）：若已收到 done 则忽略，否则报错
    if (!doneReceived) {
      onError?.('连接中断：' + (e.message || e))
      return
    }
  }
  // 流自然结束但未收到 done 事件时兜底
  if (!doneReceived) onDone?.()
}
