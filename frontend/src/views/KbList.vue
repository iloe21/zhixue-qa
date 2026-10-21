<template>
  <div class="kb-page page-container">
    <div class="page-header">
      <div class="header-left">
        <el-button text @click="router.push('/home')">← 返回首页</el-button>
        <h2>📚 知识库</h2>
      </div>
      <el-button type="primary" @click="openCreate">＋ 创建知识库</el-button>
    </div>

    <el-empty v-if="!loading && kbs.length === 0" description="还没有知识库，点击右上角创建，上传教材开始智能学习" />

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="8" v-for="kb in kbs" :key="kb.id" style="margin-bottom: 16px">
        <el-card class="kb-card" shadow="hover" @click="router.push(`/kbs/${kb.id}`)">
          <div class="kb-title">
            <span class="kb-name">{{ kb.name }}</span>
            <el-tag :type="kb.visibility === 'public' ? 'success' : 'info'" size="small">
              {{ kb.visibility === 'public' ? '公共' : '私有' }}
            </el-tag>
          </div>
          <p class="kb-desc">{{ kb.description || '暂无描述' }}</p>
          <div class="kb-footer">
            <span class="kb-count">📄 {{ kb.docCount || 0 }} 个文档</span>
            <span class="kb-actions" @click.stop>
              <el-button v-if="canManage(kb)" size="small" text @click="openEdit(kb)">编辑</el-button>
              <el-popconfirm v-if="canManage(kb)" title="确定删除该知识库及其全部文档吗？" width="240" @confirm="removeKb(kb.id)">
                <template #reference>
                  <el-button size="small" text type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 创建 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑知识库' : '创建知识库'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="70px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="100" placeholder="如：Java 程序设计教材" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500"
                    placeholder="这个知识库存放什么资料（选填）" />
        </el-form-item>
        <el-form-item label="可见性" v-if="userStore.isAdmin">
          <el-radio-group v-model="form.visibility">
            <el-radio value="private">私有（仅自己）</el-radio>
            <el-radio value="public">公共（所有学生可见）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listKbs, createKb, updateKb, deleteKb } from '../api/kb'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()

const kbs = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(null)      // null=创建模式，对象=编辑模式
const saving = ref(false)
const formRef = ref()
const form = reactive({ name: '', description: '', visibility: 'private' })

const rules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    kbs.value = (await listKbs()) || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

onMounted(load)

function canManage(kb) {
  return userStore.isAdmin || kb.ownerId === userStore.user?.id
}

function openCreate() {
  editing.value = null
  form.name = ''
  form.description = ''
  form.visibility = 'private'
  dialogVisible.value = true
}

function openEdit(kb) {
  editing.value = kb
  form.name = kb.name
  form.description = kb.description || ''
  form.visibility = kb.visibility
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (editing.value) {
      await updateKb(editing.value.id, { ...form })
      ElMessage.success('已保存')
    } else {
      await createKb({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function removeKb(id) {
  try {
    await deleteKb(id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    // 拦截器已提示
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
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
.kb-card {
  cursor: pointer;
  border-radius: 12px;
  transition: transform 0.2s;
}
.kb-card:hover {
  transform: translateY(-3px);
}
.kb-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.kb-name {
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-desc {
  color: #909399;
  font-size: 13px;
  margin: 10px 0 14px;
  height: 38px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.kb-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.kb-count {
  color: #909399;
  font-size: 13px;
}
</style>
