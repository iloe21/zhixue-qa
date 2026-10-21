<template>
  <div class="home-page">
    <!-- 顶部导航栏 -->
    <header class="navbar">
      <div class="navbar-inner">
        <div class="brand">
          <span class="brand-icon">🎓</span>
          <span class="brand-name">智学问答</span>
        </div>
        <div class="user-area" v-if="userStore.isLoggedIn">
          <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="router.push('/admin')">
            管理后台
          </el-button>
          <el-tag v-if="userStore.isAdmin" type="danger" effect="light" size="small">管理员</el-tag>
          <span class="nickname">{{ userStore.displayName }}</span>
          <el-button size="small" @click="onLogout">退出登录</el-button>
        </div>
      </div>
    </header>

    <main class="page-container">
      <!-- 欢迎横幅 -->
      <div class="welcome-banner">
        <h1>你好，{{ userStore.displayName }} 👋</h1>
        <p>上传教材构建知识库，获得带引用溯源的 AI 学习问答</p>
      </div>

      <!-- 功能模块卡片（随开发逐步点亮） -->
      <el-row :gutter="20" class="feature-row">
        <el-col :span="8">
          <el-card class="feature-card clickable" shadow="hover" @click="router.push('/kbs')">
            <div class="feature-icon">📚</div>
            <h3>知识库管理</h3>
            <p>上传教材 / 讲义，构建专属知识库</p>
            <el-tag type="success" size="small">已上线 · 点击进入</el-tag>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="feature-card clickable" shadow="hover" @click="router.push('/chat')">
            <div class="feature-icon">💬</div>
            <h3>智能问答</h3>
            <p>基于教材内容回答，标注引用出处</p>
            <el-tag type="success" size="small">已上线 · 点击进入</el-tag>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="feature-card clickable" shadow="hover" @click="router.push('/exercises')">
            <div class="feature-icon">📝</div>
            <h3>练习题生成</h3>
            <p>AI 出题 + 自动判分 + 答案解析</p>
            <el-tag type="success" size="small">已上线 · 点击进入</el-tag>
          </el-card>
        </el-col>
      </el-row>

      <!-- 系统状态卡片 -->
      <el-card class="status-card" shadow="hover">
        <template #header>
          <span>🔧 系统状态（工程验收）</span>
        </template>
        <el-button type="primary" :loading="loading" @click="checkHealth">测试后端连接</el-button>
        <el-tag v-if="health" type="success" size="large" style="margin-left: 12px">
          ✅ 后端正常 · 数据库 {{ health.database }} · {{ health.tables }} 张表 · {{ health.time }}
        </el-tag>
        <el-alert v-if="error" :title="error" type="error" show-icon style="margin-top: 12px" />
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()

const health = ref(null)
const error = ref('')
const loading = ref(false)

onMounted(() => {
  // 刷新用户信息（校正本地缓存；token 失效时拦截器会自动跳登录页）
  userStore.fetchMe().catch(() => {})
})

async function checkHealth() {
  loading.value = true
  error.value = ''
  try {
    health.value = await request.get('/health')
  } catch (e) {
    error.value = '后端连接失败：请确认后端已启动（localhost:8080）'
  } finally {
    loading.value = false
  }
}

function onLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.navbar {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #e4e7ed;
  position: sticky;
  top: 0;
  z-index: 100;
}
.navbar-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
}
.brand-icon {
  font-size: 24px;
}
.brand-name {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(90deg, #6366f1, #06b6d4);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.nickname {
  font-size: 14px;
  color: #303133;
}
.welcome-banner {
  margin: 28px 0;
  padding: 32px;
  border-radius: 12px;
  background: linear-gradient(120deg, #6366f1 0%, #8b5cf6 50%, #06b6d4 100%);
  color: #fff;
}
.welcome-banner h1 {
  margin: 0 0 8px;
  font-size: 26px;
}
.welcome-banner p {
  margin: 0;
  opacity: 0.9;
}
.feature-row {
  margin-bottom: 20px;
}
.feature-card {
  text-align: center;
  padding: 12px 0;
  border-radius: 12px;
}
.feature-card.disabled {
  opacity: 0.75;
}
.feature-card.clickable {
  cursor: pointer;
  transition: transform 0.2s;
  border: 1px solid #c7d2fe;
}
.feature-card.clickable:hover {
  transform: translateY(-3px);
}
.feature-icon {
  font-size: 36px;
}
.feature-card h3 {
  margin: 8px 0 4px;
}
.feature-card p {
  color: #909399;
  font-size: 13px;
  margin: 0 0 12px;
}
.status-card {
  border-radius: 12px;
}
</style>
