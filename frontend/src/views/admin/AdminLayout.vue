<template>
  <div class="admin-page">
    <aside class="admin-sidebar">
      <div class="sidebar-brand" title="返回前台首页" @click="router.push('/home')">
        🎓 智学问答<span class="brand-sub">管理后台</span>
      </div>
      <el-menu :default-active="route.path" router class="side-menu">
        <el-menu-item index="/admin">
          <span>📊 仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <span>👥 用户管理</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-foot">
        <el-button text size="small" @click="router.push('/kbs')">📚 公共知识库维护</el-button>
      </div>
    </aside>
    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
</script>

<style scoped>
.admin-page {
  display: flex;
  min-height: 100vh;
  background: #f5f7fa;
}
.admin-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: #1e293b;
  display: flex;
  flex-direction: column;
}
.sidebar-brand {
  padding: 18px 16px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  border-bottom: 1px solid #334155;
}
.brand-sub {
  display: block;
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
  margin-top: 2px;
}
.side-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #cbd5e1;
  --el-menu-hover-bg-color: #334155;
  --el-menu-active-color: #818cf8;
}
.sidebar-foot {
  padding: 12px 16px;
  border-top: 1px solid #334155;
}
.sidebar-foot .el-button {
  color: #94a3b8;
}
.admin-main {
  flex: 1;
  padding: 24px;
  min-width: 0;
}
</style>
