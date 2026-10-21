<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div class="header-left">
        <el-button text @click="router.push('/exercises')">← 练习列表</el-button>
        <h2>{{ detail?.exercise.title || '练习' }}</h2>
        <el-tag v-if="detail && !answered" type="warning" size="small">待作答</el-tag>
      </div>
    </div>

    <template v-if="detail">
      <!-- 成绩横幅（已作答） -->
      <el-card v-if="answered" class="score-card" shadow="never">
        <div class="score-line">
          <span class="score-num" :class="passed ? 'good' : 'bad'">{{ detail.exercise.score }}</span>
          <span class="score-total">/ {{ detail.exercise.totalScore }} 分</span>
          <el-tag :type="passed ? 'success' : 'danger'" size="large" style="margin-left: 16px">
            {{ passed ? '及格 🎉' : '未及格，复习教材后再来挑战' }}
          </el-tag>
          <span class="score-stat">答对 {{ correctCount }} / {{ detail.questions.length }} 题</span>
        </div>
      </el-card>

      <!-- 题目卡片 -->
      <el-card v-for="(q, idx) in detail.questions" :key="q.id" class="q-card" shadow="never">
        <div class="q-head">
          <el-tag size="small" :type="q.type === 1 ? 'primary' : 'warning'">
            {{ q.type === 1 ? '选择题' : '填空题' }}
          </el-tag>
          <span class="q-title">{{ idx + 1 }}. {{ q.question }}</span>
          <span class="q-score">（{{ q.score }} 分）</span>
          <el-tag v-if="answered" :type="q.isCorrect === 1 ? 'success' : 'danger'" size="small" class="q-result">
            {{ q.isCorrect === 1 ? '✓ 答对' : '✗ 答错' }}
          </el-tag>
        </div>

        <!-- 选择题：作答模式 -->
        <el-radio-group v-if="q.type === 1 && !answered" v-model="answers[q.id]" class="q-options">
          <el-radio v-for="opt in q.options" :key="opt.key" :value="opt.key" class="q-option">
            {{ opt.key }}. {{ opt.text }}
          </el-radio>
        </el-radio-group>
        <!-- 选择题：结果模式 -->
        <div v-else-if="q.type === 1 && answered" class="q-options">
          <div v-for="opt in q.options" :key="opt.key" :class="['q-option-static', optionClass(q, opt.key)]">
            {{ opt.key }}. {{ opt.text }}
            <el-tag v-if="opt.key === q.answer" type="success" size="small">正确答案</el-tag>
            <el-tag v-if="opt.key === q.userAnswer && q.userAnswer !== q.answer" type="danger" size="small">你的选择</el-tag>
          </div>
        </div>

        <!-- 填空题：作答模式 -->
        <el-input
          v-if="q.type === 2 && !answered"
          v-model="answers[q.id]"
          placeholder="输入答案"
          style="max-width: 420px; margin-top: 8px"
        />
        <!-- 填空题：结果模式 -->
        <div v-else-if="q.type === 2 && answered" class="blank-result">
          <p>你的答案：<b :class="q.isCorrect === 1 ? 'ok' : 'bad'">{{ q.userAnswer || '（未作答）' }}</b></p>
          <p>正确答案：<b class="ok">{{ q.answer }}</b></p>
        </div>

        <!-- 解析与溯源（结果模式） -->
        <div v-if="answered" class="q-explain">
          <p v-if="q.explanation">📖 解析：{{ q.explanation }}</p>
          <el-button v-if="q.sourceChunkId" size="small" text type="primary" @click="showChunk(q)">
            📄 查看教材出处
          </el-button>
        </div>
      </el-card>

      <!-- 提交栏 -->
      <div v-if="!answered" class="submit-bar">
        <el-button type="primary" size="large" :loading="submitting" @click="submit">
          提交作答（仅一次机会）
        </el-button>
      </div>
    </template>

    <!-- 教材出处弹窗 -->
    <el-dialog v-model="chunkVisible" title="出题依据（教材原文）" width="640px">
      <div v-loading="chunkLoading" class="chunk-content">{{ chunkContent }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getExercise, submitExercise } from '../api/exercise'
import { getChunk } from '../api/kb'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const detail = ref(null)
const answers = reactive({})   // questionId → 答案

const answered = computed(() => detail.value?.exercise.status === 1)
const passed = computed(() =>
  detail.value && detail.value.exercise.totalScore > 0 &&
  detail.value.exercise.score / detail.value.exercise.totalScore >= 0.6)
const correctCount = computed(() =>
  detail.value?.questions.filter(q => q.isCorrect === 1).length || 0)

async function load() {
  loading.value = true
  try {
    detail.value = await getExercise(route.params.id)
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

onMounted(load)

function optionClass(q, key) {
  if (key === q.answer) return 'correct'
  if (key === q.userAnswer && q.userAnswer !== q.answer) return 'wrong'
  return ''
}

async function submit() {
  const total = detail.value.questions.length
  const done = Object.values(answers).filter(v => v !== undefined && v !== null && String(v).trim() !== '').length
  if (done < total) {
    try {
      await ElMessageBox.confirm(
        `还有 ${total - done} 题未作答，未作答按 0 分计。确定提交吗？`,
        '提示', { type: 'warning' }
      )
    } catch (e) { return }
  } else {
    try {
      await ElMessageBox.confirm('提交后不可修改，确定提交吗？', '提示', { type: 'warning' })
    } catch (e) { return }
  }

  submitting.value = true
  try {
    const payload = Object.entries(answers)
      .filter(([, v]) => v !== undefined && v !== null && String(v).trim() !== '')
      .map(([qid, v]) => ({ questionId: Number(qid), answer: String(v).trim() }))
    detail.value = await submitExercise(route.params.id, payload)
    ElMessage.success(`判分完成：${detail.value.exercise.score} / ${detail.value.exercise.totalScore} 分`)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (e) { /* 拦截器已提示 */ } finally {
    submitting.value = false
  }
}

// ===== 教材出处 =====
const chunkVisible = ref(false)
const chunkLoading = ref(false)
const chunkContent = ref('')

async function showChunk(q) {
  chunkVisible.value = true
  chunkLoading.value = true
  chunkContent.value = ''
  try {
    const chunk = await getChunk(q.sourceChunkId)
    chunkContent.value = chunk.content
  } catch (e) {
    chunkContent.value = '（原文加载失败）'
  } finally {
    chunkLoading.value = false
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
.score-card {
  border-radius: 12px;
  margin-bottom: 16px;
  background: linear-gradient(120deg, #eef2ff, #ecfeff);
}
.score-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.score-num {
  font-size: 40px;
  font-weight: 800;
}
.score-num.good { color: #16a34a; }
.score-num.bad { color: #dc2626; }
.score-total {
  color: #909399;
  font-size: 16px;
}
.score-stat {
  margin-left: auto;
  color: #606266;
  font-size: 14px;
}
.q-card {
  border-radius: 12px;
  margin-bottom: 14px;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.q-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.q-score {
  color: #909399;
  font-size: 13px;
}
.q-result {
  margin-left: auto;
}
.q-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
  align-items: flex-start;
}
.q-option {
  height: auto;
  padding: 4px 0;
  white-space: normal;
}
.q-option-static {
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 14px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}
.q-option-static.correct {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}
.q-option-static.wrong {
  background: #fef2f2;
  border: 1px solid #fecaca;
}
.blank-result p {
  margin: 6px 0;
  font-size: 14px;
}
.ok { color: #16a34a; }
.bad { color: #dc2626; }
.q-explain {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #e4e7ed;
  color: #606266;
  font-size: 13px;
}
.q-explain p {
  margin: 0 0 6px;
}
.submit-bar {
  text-align: center;
  padding: 20px 0 40px;
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
  min-height: 80px;
}
</style>
