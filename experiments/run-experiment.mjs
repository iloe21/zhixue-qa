/**
 * 对比实验脚本：RAG 检索增强问答 vs 直接调用大模型
 * （对应设计文档 3.1 研究内容 4：对比实验与评估）
 *
 * 用法：
 *   1. 启动后端（debug 模式）并登录拿到 token
 *   2. TOKEN=xxx node experiments/run-experiment.mjs
 *
 * 流程：每题独立新建会话 → RAG 链路作答（SSE）→ 直接调 qwen-plus 作答（基线）
 *       → LLM 评审员打分（随机交换答案顺序消除位置偏差）→ 汇总输出
 * 产出：testdata/experiment/results.json + 控制台汇总表
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

// 项目根目录 = 本脚本所在目录（experiments/）的上一级，克隆到任意位置均可运行
const ROOT = fileURLToPath(new URL('..', import.meta.url)).replace(/[\\/]+$/, '')
const TOKEN = process.env.TOKEN
if (!TOKEN) {
  console.error('缺少 TOKEN 环境变量（先登录系统获取）')
  process.exit(1)
}
const BASE = 'http://localhost:8080'
const KB_ID = Number(process.env.KB_ID || 1)

// 从 application-local.yml 读取百炼配置（实验复用系统同一模型，保证公平对比）
const yml = readFileSync(`${ROOT}/backend/src/main/resources/application-local.yml`, 'utf8')
const API_KEY = yml.match(/api-key:\s*(\S+)/)[1]
const AI_BASE = (yml.match(/base-url:\s*(\S+)/) || [])[1] || 'https://dashscope.aliyuncs.com/compatible-mode'
const MODEL = (yml.match(/model:\s*(\S+)/) || [])[1] || 'qwen-plus'

const questions = JSON.parse(readFileSync(`${ROOT}/testdata/experiment/questions.json`, 'utf8'))
const REFUSAL_MARK = '未找到'

// ===== 工具函数 =====

async function llm(messages, temperature = 0.3) {
  const resp = await fetch(`${AI_BASE}/v1/chat/completions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${API_KEY}` },
    body: JSON.stringify({ model: MODEL, messages, temperature })
  })
  const data = await resp.json()
  if (!data.choices) throw new Error('LLM 调用失败: ' + JSON.stringify(data).slice(0, 300))
  return data.choices[0].message.content
}

async function api(path, options = {}) {
  const resp = await fetch(`${BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}`, ...(options.headers || {}) }
  })
  const body = await resp.json()
  if (body.code !== 200) throw new Error(`API ${path} 失败: ${body.message}`)
  return body.data
}

/** 走系统 RAG 链路（SSE），返回 {text, citations, refused} */
async function ragAnswer(question) {
  const conv = await api('/api/conversations', {
    method: 'POST',
    body: JSON.stringify({ kbIds: [KB_ID] })
  })
  const resp = await fetch(`${BASE}/api/conversations/${conv.conversation.id}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}` },
    body: JSON.stringify({ question })
  })
  if (!resp.ok) throw new Error(`chat HTTP ${resp.status}`)

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = '', text = '', citations = []
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop()
    for (const part of parts) {
      let event = 'message'
      const dataLines = []
      for (const line of part.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
      }
      const data = dataLines.join('\n')
      if (event === 'delta') text += data
      else if (event === 'citations') { try { citations = JSON.parse(data) } catch { /* ignore */ } }
      else if (event === 'error') throw new Error('SSE error: ' + data)
    }
  }
  return { text, citations, refused: text.includes(REFUSAL_MARK) && citations.length === 0 }
}

/** 直接问大模型（基线：无检索、无系统提示词，模拟用户裸用通用大模型） */
async function directAnswer(question) {
  return llm([{ role: 'user', content: question }])
}

/** LLM 评审员：对两个答案按统一标准打分（0/1/2） */
async function judge(question, reference, ans1, ans2) {
  const prompt = `你是严谨的答案质量评审员。根据题目和参考答案，对两个候选答案分别打分。

评分标准：
- 2 分：与参考答案一致，准确完整
- 1 分：方向正确但含糊、不完整或有次要错误
- 0 分：错误、编造，或【无法回答】类题目中强行作答而没有明确拒答
- 对参考答案为【无法回答】的题目：明确说明"无法回答/资料未涉及/建议补充资料"得 2 分；部分拒答但仍有编造成分得 1 分；正常作答得 0 分

只输出 JSON（不要 markdown 标记）：{"score1": 0-2, "score2": 0-2, "reason": "一句话理由"}

题目：${question}
参考答案：${reference}
答案1：${ans1.slice(0, 1500)}
答案2：${ans2.slice(0, 1500)}`
  const out = await llm([{ role: 'user', content: prompt }], 0.1)
  const cleaned = out.replace(/^```[a-zA-Z]*\s*/, '').replace(/```\s*$/, '').trim()
  const start = cleaned.indexOf('{'), end = cleaned.lastIndexOf('}')
  const parsed = JSON.parse(cleaned.slice(start, end + 1))
  return { s1: parsed.score1, s2: parsed.score2, reason: parsed.reason }
}

// ===== 主流程 =====

const results = []
console.log(`实验开始：${questions.length} 题 | 模型=${MODEL} | 知识库ID=${KB_ID}`)
console.log('='.repeat(70))

for (const q of questions) {
  const t0 = Date.now()
  try {
    const rag = await ragAnswer(q.question)
    const direct = await directAnswer(q.question)

    // 随机交换顺序消除评审位置偏差
    const swap = Math.random() < 0.5
    const a1 = swap ? rag.text : direct
    const a2 = swap ? direct : rag.text
    const j = await judge(q.question, q.reference, a1, a2)
    const ragScore = swap ? j.s1 : j.s2
    const directScore = swap ? j.s2 : j.s1

    results.push({
      id: q.id, type: q.type, question: q.question,
      ragScore, directScore,
      ragRefused: rag.refused, ragCitations: rag.citations.length,
      ragAnswer: rag.text, directAnswer: direct,
      judgeReason: j.reason, elapsedMs: Date.now() - t0
    })
    console.log(`Q${q.id} [${q.type}] RAG=${ragScore}分(引用${rag.citations.length}${rag.refused ? ',拒答' : ''}) 直接=${directScore}分 | ${j.reason} (${((Date.now() - t0) / 1000).toFixed(1)}s)`)
  } catch (e) {
    console.error(`Q${q.id} 出错: ${e.message}`)
    results.push({ id: q.id, type: q.type, question: q.question, error: e.message })
  }
}

// ===== 汇总 =====
const valid = results.filter(r => r.ragScore !== undefined)
const ans = valid.filter(r => r.type === 'answerable')
const unans = valid.filter(r => r.type === 'unanswerable')
const avg = (arr, key) => arr.length ? (arr.reduce((s, r) => s + r[key], 0) / arr.length) : 0

const summary = {
  total: questions.length,
  valid: valid.length,
  answerable: {
    count: ans.length,
    ragAvg: +avg(ans, 'ragScore').toFixed(2),
    directAvg: +avg(ans, 'directScore').toFixed(2)
  },
  unanswerable: {
    count: unans.length,
    ragAvg: +avg(unans, 'ragScore').toFixed(2),
    directAvg: +avg(unans, 'directScore').toFixed(2),
    ragRefusalRate: unans.length ? +(unans.filter(r => r.ragRefused).length / unans.length).toFixed(2) : 0,
    directHallucinationCount: unans.filter(r => r.directScore === 0).length
  },
  overall: {
    ragAvg: +avg(valid, 'ragScore').toFixed(2),
    directAvg: +avg(valid, 'directScore').toFixed(2)
  },
  model: MODEL,
  date: new Date().toISOString().slice(0, 10)
}

mkdirSync(`${ROOT}/testdata/experiment`, { recursive: true })
writeFileSync(`${ROOT}/testdata/experiment/results.json`, JSON.stringify({ summary, results }, null, 2), 'utf8')

console.log('='.repeat(70))
console.log('实验汇总：')
console.log(JSON.stringify(summary, null, 2))
console.log(`\n结果已写入 testdata/experiment/results.json`)
