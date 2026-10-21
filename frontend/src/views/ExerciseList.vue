<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <el-button text @click="router.push('/home')">← 返回首页</el-button>
        <h2>📝 练习题</h2>
      </div>
    </div>

    <!-- AI 出题配置 -->
    <el-card class="gen-card" shadow="never">
      <template #header>
        <span>🤖 AI 智能出题（基于知识库教材内容）</span>
      </template>
      <el-form :inline="true" class="gen-form">
        <el-form-item label="知识库">
          <el-select v-model="genForm.kbId" placeholder="选择知识库" style="width: 240px">
            <el-option
              v-for="kb in kbs"
              :key="kb.id"
              :label="`${kb.name}（${kb.docCount || 0} 文档）`"
              :value="kb.id"
              :disabled="(kb.docCount || 0) === 0"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选择题">
          <el-input-number v-model="genForm.choiceCount" :min="1" :max="10" />
        </el-form-item>
        <el-form-item label="填空题">
          <el-input-number v-model="genForm.blankCount" :min="0" :max="10" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="generating" :disabled="!genForm.kbId" @click="generate">
            {{ generating ? 'AI 出题中（约 10~40 秒）…' : '生成练习' }}
          </el-button>
        </el-form-item>
      </el-form>
      <p class="gen-tip">💡 每题 5 分；出题依据教材片段并标注出处；提交后服务端自动判分，附答案与解析</p>
    </el-card>

    <!-- 练习历史 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>我的练习记录（{{ exercises.length }}）</span>
          <el-button size="small" text @click="loadExercises">🔄 刷新</el-button>
        </div>
      </template>
      <el-table :data="exercises" empty-text="暂无练习记录，先在上方生成一份吧">
        <el-table-column prop="title" label="练习" min-width="200" show-overflow-tooltip />
        <el-table-column label="题量" width="140">
          <template #default="{ row }">选择 {{ row.choiceCount }} + 填空 {{ row.blankCount }}</template>
        </el-table-column>
        <el-table-column label="得分" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" :type="row.score / row.totalScore >= 0.6 ? 'success' : 'danger'" size="small">
              {{ row.score }} / {{ row.totalScore }}
            </el-tag>
            <el-tag v-else type="warning" size="small">未作答</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="170" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="router.push(`/exercises/${row.id}`)">
              {{ row.status === 1 ? '查看结果' : '开始作答' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listKbs } from '../api/kb'
import { generateExercise, listExercises } from '../api/exercise'

const router = useRouter()

const kbs = ref([])
const exercises = ref([])
const generating = ref(false)
const genForm = reactive({ kbId: null, choiceCount: 5, blankCount: 3 })

async function loadKbs() {
  try {
    kbs.value = (await listKbs()) || []
  } catch (e) { /* 拦截器已提示 */ }
}

async function loadExercises() {
  try {
    exercises.value = (await listExercises()) || []
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(() => {
  loadKbs()
  loadExercises()
})

async function generate() {
  generating.value = true
  try {
    const detail = await generateExercise({
      kbId: genForm.kbId,
      choiceCount: genForm.choiceCount,
      blankCount: genForm.blankCount
    })
    ElMessage.success('出题成功，开始作答吧！')
    router.push(`/exercises/${detail.exercise.id}`)
  } catch (e) {
    // 拦截器已提示
  } finally {
    generating.value = false
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
.gen-card {
  border-radius: 12px;
  border: 1px solid #c7d2fe;
}
.gen-form {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.gen-tip {
  color: #909399;
  font-size: 12px;
  margin: 4px 0 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
