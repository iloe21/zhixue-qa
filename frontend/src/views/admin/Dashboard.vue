<template>
  <div v-loading="loading">
    <h2 class="page-title">📊 仪表盘</h2>

    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label" style="margin-bottom: 16px">
        <div class="stat-card" :style="{ background: card.bg }">
          <div class="stat-icon">{{ card.icon }}</div>
          <div class="stat-info">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 8px">
      <template #header>系统说明</template>
      <p class="sys-note">
        「智学问答」基于检索增强生成（RAG）技术：用户上传教材构建知识库，问答时先检索相关片段再由大模型生成带引用溯源的回答，
        知识库无相关内容时明确拒答以抑制幻觉。公共知识库由管理员在「知识库管理」中创建（visibility=public），全体学生共享。
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getStats } from '../../api/admin'

const loading = ref(false)
const stats = ref(null)

const cards = computed(() => {
  const s = stats.value || {}
  return [
    { icon: '👥', label: '注册用户', value: s.users ?? '-', bg: 'linear-gradient(120deg, #6366f1, #8b5cf6)' },
    { icon: '📚', label: `知识库（公共 ${s.publicKbs ?? '-'}）`, value: s.knowledgeBases ?? '-', bg: 'linear-gradient(120deg, #0ea5e9, #06b6d4)' },
    { icon: '📄', label: '文档总数', value: s.documents ?? '-', bg: 'linear-gradient(120deg, #f59e0b, #f97316)' },
    { icon: '🧩', label: '知识片段', value: s.chunks ?? '-', bg: 'linear-gradient(120deg, #10b981, #14b8a6)' },
    { icon: '💬', label: '会话数', value: s.conversations ?? '-', bg: 'linear-gradient(120deg, #ec4899, #f43f5e)' },
    { icon: '🤖', label: 'AI 回答数', value: s.qaMessages ?? '-', bg: 'linear-gradient(120deg, #8b5cf6, #d946ef)' },
    { icon: '📝', label: '练习份数', value: s.exercises ?? '-', bg: 'linear-gradient(120deg, #0d9488, #22c55e)' },
    { icon: '🔍', label: '检索阈值', value: 0.28, bg: 'linear-gradient(120deg, #64748b, #475569)' }
  ]
})

async function load() {
  loading.value = true
  try {
    stats.value = await getStats()
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
}
.stat-card {
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: #fff;
}
.stat-icon {
  font-size: 30px;
}
.stat-value {
  font-size: 26px;
  font-weight: 800;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  opacity: 0.9;
}
.sys-note {
  color: #606266;
  font-size: 13px;
  line-height: 1.9;
  margin: 0;
}
</style>
