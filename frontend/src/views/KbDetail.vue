<template>
  <div class="kb-page page-container" v-loading="loading">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <el-button text @click="router.push('/kbs')">← 知识库列表</el-button>
        <h2>{{ kb?.name || '加载中…' }}</h2>
        <el-tag v-if="kb" :type="kb.visibility === 'public' ? 'success' : 'info'" size="small">
          {{ kb.visibility === 'public' ? '公共' : '私有' }}
        </el-tag>
      </div>
    </div>
    <p class="kb-desc" v-if="kb?.description">{{ kb.description }}</p>

    <!-- 上传区（仅所有者/管理员可见） -->
    <el-card v-if="canManage" class="upload-card" shadow="never">
      <el-upload
        drag
        :show-file-list="false"
        :http-request="doUpload"
        accept=".pdf,.doc,.docx,.txt,.md"
        :disabled="uploading"
      >
        <div class="upload-inner">
          <span class="upload-icon">📄</span>
          <p>将教材/讲义拖到此处，或 <em>点击上传</em></p>
          <p class="upload-tip">支持 PDF / Word(doc·docx) / TXT / Markdown，单文件 ≤ 10MB；上传后自动解析入库</p>
        </div>
      </el-upload>
      <el-progress v-if="uploading" :percentage="uploadPercent" style="margin-top: 12px" />
    </el-card>
    <el-alert v-else type="info" :closable="false" style="margin-bottom: 16px"
              title="这是公共知识库，仅管理员可上传文档；你可以浏览文档与片段" />

    <!-- 文档列表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>文档列表（{{ docs.length }}）</span>
          <el-button size="small" text @click="refreshDocs">🔄 刷新</el-button>
        </div>
      </template>
      <el-table :data="docs" empty-text="暂无文档，请上传教材或讲义">
        <el-table-column prop="filename" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ (row.fileType || '').toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="片段数" width="80">
          <template #default="{ row }">{{ row.parseStatus === 1 ? row.chunkCount : '-' }}</template>
        </el-table-column>
        <el-table-column label="解析状态" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.parseStatus === 0" type="warning" size="small">⏳ 解析中…</el-tag>
            <el-tooltip v-else-if="row.parseStatus === 2" :content="row.errorMsg || '解析失败'" placement="top">
              <el-tag type="danger" size="small">❌ 失败（悬停看原因）</el-tag>
            </el-tooltip>
            <el-tag v-else type="success" size="small">✅ 已解析</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="170" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" :disabled="row.parseStatus !== 1"
                       @click="viewChunks(row)">片段</el-button>
            <el-button v-if="canManage" size="small" text :disabled="row.parseStatus === 0"
                       @click="reparse(row)">重新解析</el-button>
            <el-popconfirm v-if="canManage" title="删除该文档及其全部向量？" width="220" @confirm="removeDoc(row.id)">
              <template #reference>
                <el-button size="small" text type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 片段浏览弹窗 -->
    <el-dialog v-model="chunksVisible" :title="`文档片段 - ${currentDoc?.filename || ''}`" width="720px" top="6vh">
      <div v-loading="chunksLoading" class="chunks-body">
        <el-empty v-if="!chunksLoading && chunks.length === 0" description="暂无片段" />
        <el-card v-for="c in chunks" :key="c.id" class="chunk-card" shadow="never">
          <div class="chunk-head">
            <el-tag size="small" type="primary">片段 #{{ c.chunkIndex + 1 }}</el-tag>
            <span class="chunk-meta">{{ c.charCount }} 字符 · 向量ID {{ c.id }}</span>
          </div>
          <div class="chunk-content">{{ c.content }}</div>
        </el-card>
        <el-pagination
          v-if="chunkTotal > chunkSize"
          layout="prev, pager, next"
          :total="chunkTotal"
          :page-size="chunkSize"
          :current-page="chunkPage"
          style="justify-content: center; margin-top: 12px"
          @current-change="onPageChange"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getKb, listDocs, uploadDoc, deleteDoc, reparseDoc, listChunks } from '../api/kb'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const kbId = route.params.id

const kb = ref(null)
const docs = ref([])
const loading = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)

const canManage = computed(() =>
  kb.value && (userStore.isAdmin || kb.value.ownerId === userStore.user?.id))

// ===== 加载与轮询（解析中的文档每 3 秒刷新状态）=====
let pollTimer = null

async function load() {
  loading.value = true
  try {
    kb.value = await getKb(kbId)
    docs.value = (await listDocs(kbId)) || []
    schedulePoll()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function schedulePoll() {
  clearInterval(pollTimer)
  if (docs.value.some(d => d.parseStatus === 0)) {
    pollTimer = setInterval(refreshDocs, 3000)
  }
}

async function refreshDocs() {
  try {
    docs.value = (await listDocs(kbId)) || []
    schedulePoll()
    kb.value = await getKb(kbId)   // 同步 docCount
  } catch (e) {
    clearInterval(pollTimer)
  }
}

onMounted(load)
onUnmounted(() => clearInterval(pollTimer))

// ===== 上传 =====
async function doUpload(option) {
  uploading.value = true
  uploadPercent.value = 0
  try {
    await uploadDoc(kbId, option.file, e => {
      if (e.total) uploadPercent.value = Math.round((e.loaded * 100) / e.total)
    })
    ElMessage.success('上传成功，正在后台解析…')
    await refreshDocs()
  } catch (e) {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

// ===== 文档操作 =====
async function removeDoc(id) {
  try {
    await deleteDoc(id)
    ElMessage.success('已删除')
    refreshDocs()
  } catch (e) { /* 拦截器已提示 */ }
}

async function reparse(row) {
  try {
    await reparseDoc(row.id)
    ElMessage.success('已重新加入解析队列')
    refreshDocs()
  } catch (e) { /* 拦截器已提示 */ }
}

// ===== 片段浏览 =====
const chunksVisible = ref(false)
const chunksLoading = ref(false)
const currentDoc = ref(null)
const chunks = ref([])
const chunkTotal = ref(0)
const chunkPage = ref(1)
const chunkSize = 5

async function viewChunks(row) {
  currentDoc.value = row
  chunkPage.value = 1
  chunksVisible.value = true
  loadChunks()
}

async function loadChunks() {
  chunksLoading.value = true
  try {
    const page = await listChunks(currentDoc.value.id, chunkPage.value, chunkSize)
    chunks.value = page.records || []
    chunkTotal.value = Number(page.total || 0)
  } catch (e) {
    // 拦截器已提示
  } finally {
    chunksLoading.value = false
  }
}

function onPageChange(p) {
  chunkPage.value = p
  loadChunks()
}

function formatSize(bytes) {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-left h2 {
  margin: 0;
  font-size: 22px;
}
.kb-desc {
  color: #909399;
  font-size: 13px;
  margin: 8px 0 16px;
}
.upload-card {
  border-radius: 12px;
  border: 1px dashed #c7d2fe;
}
.upload-inner {
  padding: 8px 0;
}
.upload-icon {
  font-size: 40px;
}
.upload-inner p {
  margin: 6px 0;
  color: #606266;
}
.upload-inner em {
  color: var(--primary-color, #6366f1);
  font-style: normal;
}
.upload-tip {
  font-size: 12px;
  color: #909399 !important;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chunks-body {
  max-height: 60vh;
  overflow-y: auto;
  min-height: 120px;
}
.chunk-card {
  margin-bottom: 12px;
  border-radius: 8px;
  background: #fafafa;
}
.chunk-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.chunk-meta {
  color: #909399;
  font-size: 12px;
}
.chunk-content {
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
