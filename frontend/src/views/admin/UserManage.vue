<template>
  <div>
    <h2 class="page-title">👥 用户管理</h2>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名 / 昵称"
          clearable
          style="width: 260px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" @click="search">搜索</el-button>
        <span class="total-tip">共 {{ total }} 名用户</span>
      </div>

      <el-table :data="users" v-loading="loading" style="margin-top: 12px">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180">
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="150">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">
              {{ row.role === 'admin' ? '管理员' : '学生' }}
            </el-tag>
            <el-button
              v-if="!isSelf(row)"
              size="small"
              text
              type="primary"
              @click="toggleRole(row)"
            >
              {{ row.role === 'admin' ? '降为学生' : '升为管理员' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :disabled="isSelf(row)"
              @change="val => toggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170" />
      </el-table>

      <el-pagination
        v-if="total > size"
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        style="justify-content: center; margin-top: 16px"
        @current-change="p => { page = p; load() }"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, updateUserStatus, updateUserRole } from '../../api/admin'
import { useUserStore } from '../../store/user'

const userStore = useUserStore()

const users = ref([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = 10
const total = ref(0)

function isSelf(row) {
  return row.id === userStore.user?.id
}

async function load() {
  loading.value = true
  try {
    const data = await listUsers(page.value, size, keyword.value)
    users.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

onMounted(load)

async function toggleStatus(row, val) {
  const action = val ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${action}用户「${row.username}」吗？`, '提示', { type: 'warning' })
  } catch (e) { return }
  try {
    await updateUserStatus(row.id, val ? 1 : 0)
    ElMessage.success(`已${action}`)
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

async function toggleRole(row) {
  const newRole = row.role === 'admin' ? 'student' : 'admin'
  const label = newRole === 'admin' ? '升为管理员' : '降为学生'
  try {
    await ElMessageBox.confirm(`确定将「${row.username}」${label}吗？`, '提示', { type: 'warning' })
  } catch (e) { return }
  try {
    await updateUserRole(row.id, newRole)
    ElMessage.success('角色已更新')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}
</script>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.total-tip {
  color: #909399;
  font-size: 13px;
  margin-left: auto;
}
</style>
