<template>
  <div class="chat-page">
    <!-- 左侧：会话列表 -->
    <aside class="sidebar">
      <div class="sidebar-head">
        <span class="brand" title="返回首页" @click="router.push('/home')">🎓 智学问答</span>
        <el-button type="primary" size="small" @click="openNewConv">＋ 新会话</el-button>
      </div>
      <div class="conv-list">
        <div
          v-for="c in conversations"
          :key="c.id"
          :class="['conv-item', { active: c.id === currentId }]"
          @click="selectConv(c.id)"
        >
          <span class="conv-title">{{ c.title || '新会话' }}</span>
          <el-dropdown trigger="click" @command="cmd => onConvCmd(cmd, c)">
            <span class="conv-more" @click.stop>⋯</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item command="delete">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <el-empty v-if="conversations.length === 0" description="暂无会话" :image-size="60" />
      </div>
      <div class="sidebar-foot">
        <el-button text size="small" @click="router.push('/kbs')">📚 知识库管理</el-button>
      </div>
    </aside>

    <!-- 右侧：对话区 -->
    <main class="chat-main">
      <div class="chat-header" v-if="currentDetail">
        <b>{{ currentDetail.conversation.title || '新会话' }}</b>
        <span class="kb-tags">
          <el-tag v-for="(n, i) in currentDetail.kbNames" :key="i" size="small" type="info">{{ n }}</el-tag>
        </span>
      </div>

      <div class="msg-area" ref="msgAreaRef">
        <el-empty
          v-if="!currentId"
          description="点击左上角「新会话」，选择知识库范围后开始提问"
        />
        <template v-else>
          <div v-for="(m, i) in messages" :key="i" :class="['msg-row', m.role]">
            <div class="bubble">
              <div v-if="m.role === 'assistant'" class="msg-content markdown-body" v-html="renderMd(m.content)"></div>
              <div v-else class="msg-content">{{ m.content }}</div>
              <div v-if="m.role === 'assistant' && m.citationList?.length" class="citations">
                <div class="cit-title">📎 引用来源（点击查看原文）：</div>
                <el-tag
                  v-for="cit in m.citationList"
                  :key="cit.ref"
                  class="cit-tag"
                  size="small"
                  effect="plain"
                  @click="showChunk(cit)"
                >
                  [{{ cit.ref }}] {{ cit.docName }} · 片段{{ cit.chunkIndex + 1 }} · {{ Math.round((cit.score || 0) * 100) }}%
                </el-tag>
              </div>
            </div>
          </div>
          <!-- 流式生成中的气泡 -->
          <div v-if="streaming" class="msg-row assistant">
            <div class="bubble">
              <div class="msg-content markdown-body">
                <div v-html="renderMd(streamText)"></div>
                <span class="cursor-blink">▍</span>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="input-area" v-if="currentId">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="基于所选知识库提问，如：什么是多态？（Enter 发送，Shift+Enter 换行）"
          :disabled="streaming"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="streaming" :disabled="!input.trim()" @click="send">
          发送
        </el-button>
      </div>
    </main>

    <!-- 新会话弹窗：选择知识库检索范围 -->
    <el-dialog v-model="newConvVisible" title="新会话 - 选择知识库范围" width="460px">
      <el-checkbox-group v-model="selectedKbs">
        <div v-for="kb in kbs" :key="kb.id" class="kb-option">
          <el-checkbox :value="kb.id">
            {{ kb.name }}
            <el-tag size="small" :type="kb.visibility === 'public' ? 'success' : 'info'">
              {{ kb.visibility === 'public' ? '公共' : '私有' }}
            </el-tag>
            <span class="kb-doc-count">{{ kb.docCount || 0 }} 文档</span>
          </el-checkbox>
        </div>
      </el-checkbox-group>
      <el-empty
        v-if="kbs.length === 0"
        description="暂无可用知识库，请先到「知识库管理」创建并上传教材"
        :image-size="60"
      />
      <template #footer>
        <el-button @click="newConvVisible = false">取消</el-button>
        <el-button type="primary" :disabled="selectedKbs.length === 0" :loading="creating" @click="createConv">
          开始会话
        </el-button>
      </template>
    </el-dialog>

    <!-- 引用原文弹窗 -->
    <el-dialog v-model="chunkVisible" title="原文片段" width="640px">
      <div v-loading="chunkLoading">
        <div class="cit-meta" v-if="currentCit">
          [{{ currentCit.ref }}] 《{{ currentCit.docName }}》 片段 {{ currentCit.chunkIndex + 1 }}
          · 相关度 {{ Math.round((currentCit.score || 0) * 100) }}%
        </div>
        <div class="chunk-content">{{ chunkContent }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/common'   // 常用语言子集，控制分包体积
import 'highlight.js/styles/github.css'
import {
  createConversation, listConversations, getConversation,
  renameConversation, deleteConversation, listMessages, chatStream
} from '../api/chat'
import { listKbs, getChunk } from '../api/kb'

const router = useRouter()
const route = useRoute()

// ===== Markdown 渲染（AI 回答支持分点/表格/代码高亮）=====
// html:false 关闭原始 HTML 透传，避免模型输出注入页面（XSS 防护）
const md = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: false,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(code, { language: lang, ignoreIllegals: true }).value}</code></pre>`
      } catch (e) { /* 落到默认转义 */ }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  }
})

function renderMd(text) {
  return md.render(text || '')
}

// ===== 会话列表 =====
const conversations = ref([])
const currentId = ref(null)
const currentDetail = ref(null)
const messages = ref([])

async function loadConversations() {
  try {
    conversations.value = (await listConversations()) || []
  } catch (e) { /* 拦截器已提示 */ }
}

async function selectConv(id) {
  if (streaming.value) {
    ElMessage.warning('回答生成中，请稍候')
    return
  }
  currentId.value = id
  try {
    currentDetail.value = await getConversation(id)
    const msgs = (await listMessages(id)) || []
    messages.value = msgs.map(m => ({
      ...m,
      citationList: m.citations ? safeParse(m.citations) : []
    }))
    scrollToBottom()
  } catch (e) { /* 拦截器已提示 */ }
}

function safeParse(json) {
  try { return JSON.parse(json) } catch (e) { return [] }
}

async function onConvCmd(cmd, conv) {
  if (cmd === 'rename') {
    try {
      const { value } = await ElMessageBox.prompt('输入新标题', '重命名会话', {
        inputValue: conv.title || '',
        inputValidator: v => (v && v.trim() ? true : '标题不能为空')
      })
      await renameConversation(conv.id, value.trim())
      ElMessage.success('已重命名')
      loadConversations()
      if (currentId.value === conv.id) selectConv(conv.id)
    } catch (e) { /* 取消或失败 */ }
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm('删除该会话及全部消息？', '提示', { type: 'warning' })
      await deleteConversation(conv.id)
      ElMessage.success('已删除')
      if (currentId.value === conv.id) {
        currentId.value = null
        currentDetail.value = null
        messages.value = []
      }
      loadConversations()
    } catch (e) { /* 取消或失败 */ }
  }
}

// ===== 新会话 =====
const newConvVisible = ref(false)
const creating = ref(false)
const kbs = ref([])
const selectedKbs = ref([])

async function openNewConv() {
  try {
    kbs.value = (await listKbs()) || []
  } catch (e) { /* 拦截器已提示 */ }
  selectedKbs.value = []
  newConvVisible.value = true
}

async function createConv() {
  creating.value = true
  try {
    const detail = await createConversation({ kbIds: selectedKbs.value })
    newConvVisible.value = false
    await loadConversations()
    currentId.value = detail.conversation.id
    currentDetail.value = detail
    messages.value = []
  } catch (e) { /* 拦截器已提示 */ } finally {
    creating.value = false
  }
}

// ===== 问答（SSE 流式）=====
const input = ref('')
const streaming = ref(false)
const streamText = ref('')
const msgAreaRef = ref()

function scrollToBottom() {
  nextTick(() => {
    if (msgAreaRef.value) {
      msgAreaRef.value.scrollTop = msgAreaRef.value.scrollHeight
    }
  })
}

async function send() {
  const q = input.value.trim()
  if (!q || streaming.value || !currentId.value) return

  input.value = ''
  messages.value.push({ role: 'user', content: q, citationList: [] })
  scrollToBottom()

  streaming.value = true
  streamText.value = ''
  let cits = []
  let finished = false

  await chatStream(currentId.value, q, {
    onDelta: d => {
      streamText.value += d
      scrollToBottom()
    },
    onCitations: c => { cits = c || [] },
    onDone: () => {
      if (finished) return
      finished = true
      if (streamText.value) {
        messages.value.push({ role: 'assistant', content: streamText.value, citationList: cits })
      }
      streaming.value = false
      streamText.value = ''
      scrollToBottom()
      loadConversations()   // 刷新标题（首问自动生成）与排序
    },
    onError: msg => {
      if (finished) return
      finished = true
      if (streamText.value) {
        messages.value.push({ role: 'assistant', content: streamText.value, citationList: [] })
      }
      streaming.value = false
      streamText.value = ''
      ElMessage.error(msg || 'AI 服务调用失败')
    }
  })
}

// ===== 引用溯源 =====
const chunkVisible = ref(false)
const chunkLoading = ref(false)
const currentCit = ref(null)
const chunkContent = ref('')

async function showChunk(cit) {
  currentCit.value = cit
  chunkVisible.value = true
  chunkLoading.value = true
  chunkContent.value = ''
  try {
    const chunk = await getChunk(cit.chunkId)
    chunkContent.value = chunk.content
  } catch (e) {
    chunkContent.value = cit.snippet || '（原文加载失败，展示摘要）'
  } finally {
    chunkLoading.value = false
  }
}

onMounted(async () => {
  await loadConversations()
  // 深链恢复：/chat?conv=<id> 直接打开指定会话
  const convParam = Number(route.query.conv)
  if (convParam && conversations.value.some(c => c.id === convParam)) {
    selectConv(convParam)
  }
})
</script>

<style scoped>
.chat-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 260px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}
.sidebar-head {
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f2f5;
}
.brand {
  font-weight: 700;
  font-size: 16px;
  cursor: pointer;
  background: linear-gradient(90deg, #6366f1, #06b6d4);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  color: #303133;
}
.conv-item:hover {
  background: #f5f7fa;
}
.conv-item.active {
  background: #eef2ff;
  color: #4f46e5;
  font-weight: 600;
}
.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
.conv-more {
  color: #909399;
  padding: 0 4px;
  font-weight: 700;
}
.sidebar-foot {
  padding: 10px 16px;
  border-top: 1px solid #f0f2f5;
}

/* ===== 主对话区 ===== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  min-width: 0;
}
.chat-header {
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  gap: 12px;
}
.kb-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.msg-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
.msg-row {
  display: flex;
  margin-bottom: 16px;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 76%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.8;
}
.msg-row.user .bubble {
  background: linear-gradient(120deg, #6366f1, #8b5cf6);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg-row.assistant .bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 4px;
}
.msg-content {
  white-space: pre-wrap;
  word-break: break-word;
}

/* ===== AI 回答 Markdown 排版（v-html 内容需 :deep 穿透）===== */
.markdown-body {
  white-space: normal;
}
.markdown-body :deep(p) {
  margin: 0 0 8px;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 12px 0 8px;
  font-weight: 700;
  line-height: 1.4;
}
.markdown-body :deep(h1) { font-size: 17px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3),
.markdown-body :deep(h4) { font-size: 15px; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 4px 0 8px;
  padding-left: 22px;
}
.markdown-body :deep(li) {
  margin-bottom: 4px;
}
.markdown-body :deep(strong) {
  color: #4f46e5;
}
.markdown-body :deep(code):not(pre code) {
  background: #f2f3f7;
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 13px;
  color: #c7254e;
}
.markdown-body :deep(pre.hljs) {
  margin: 8px 0;
  padding: 12px;
  border-radius: 8px;
  background: #f6f8fa;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}
.markdown-body :deep(pre.hljs code) {
  background: none;
  padding: 0;
  color: inherit;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e4e7ed;
  padding: 6px 10px;
}
.markdown-body :deep(th) {
  background: #f5f7fa;
}
.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid #c7d2fe;
  background: #f8faff;
  color: #606266;
}
.cursor-blink {
  animation: blink 0.9s infinite;
  color: #6366f1;
}
@keyframes blink {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0; }
}

/* ===== 引用卡片 ===== */
.citations {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #e4e7ed;
}
.cit-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.cit-tag {
  cursor: pointer;
  margin: 0 6px 6px 0;
}
.cit-tag:hover {
  color: #4f46e5;
  border-color: #4f46e5;
}

/* ===== 输入区 ===== */
.input-area {
  padding: 16px 24px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.input-area .el-textarea {
  flex: 1;
}

/* ===== 弹窗 ===== */
.kb-option {
  padding: 6px 0;
}
.kb-doc-count {
  color: #909399;
  font-size: 12px;
  margin-left: 6px;
}
.cit-meta {
  color: #909399;
  font-size: 13px;
  margin-bottom: 10px;
}
.chunk-content {
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-all;
  background: #fafafa;
  border-radius: 8px;
  padding: 14px;
  max-height: 50vh;
  overflow-y: auto;
}
</style>
