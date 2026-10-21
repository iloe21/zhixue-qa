<template>
  <div class="auth-page">
    <div class="auth-deco deco-1"></div>
    <div class="auth-deco deco-2"></div>

    <el-card class="auth-card" shadow="always">
      <div class="auth-brand">
        <div class="brand-icon">🎓</div>
        <h2>智学问答</h2>
        <p>基于检索增强生成（RAG）的智能学习问答系统</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="图形验证码" maxlength="4" />
            <img
              v-if="captchaImg"
              :src="captchaImg"
              class="captcha-img"
              title="看不清？点击刷新"
              alt="验证码"
              @click="loadCaptcha"
            />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="auth-submit" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-links row-between">
        <a href="javascript:;" class="forgot-link" @click="openForgot">忘记密码？</a>
        <span>还没有账号？<router-link to="/register">立即注册</router-link></span>
      </div>
    </el-card>

    <!-- 忘记密码弹窗（两步：验证身份 → 重置密码） -->
    <el-dialog v-model="forgotVisible" title="找回密码" width="460px" :close-on-click-modal="false">
      <el-steps :active="forgotStep" align-center finish-status="success" class="forgot-steps">
        <el-step title="验证身份" />
        <el-step title="重置密码" />
      </el-steps>

      <!-- 第一步：用户名 + 注册邮箱 → 发送验证码 -->
      <el-form v-if="forgotStep === 0" ref="forgotFormRef" :model="forgotForm" :rules="forgotRules" label-width="70px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="forgotForm.username" placeholder="注册时的用户名" clearable />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="forgotForm.email" placeholder="注册时绑定的邮箱" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="sending" @click="sendCode">
            {{ countdown > 0 ? `重新发送（${countdown}s）` : '获取验证码' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 第二步：验证码 + 新密码 -->
      <el-form v-else ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="80px">
        <el-alert
          :title="`验证码已发送至 ${maskEmail(forgotForm.email)}，10 分钟内有效`"
          type="success"
          :closable="false"
          style="margin-bottom: 16px"
        />
        <el-form-item label="验证码" prop="code">
          <el-input v-model="resetForm.code" placeholder="6 位数字验证码" maxlength="6" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" placeholder="6-32 位" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="resetForm.confirmPassword" type="password" placeholder="再次输入新密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="resetting" @click="submitReset">
            重置密码
          </el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import { getCaptcha, forgotPassword, resetPassword } from '../api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// ===== 登录表单 =====
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '', captchaId: '', captchaCode: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }]
}

// ===== 图形验证码 =====
const captchaImg = ref('')

async function loadCaptcha() {
  try {
    const data = await getCaptcha()
    form.captchaId = data.captchaId
    captchaImg.value = data.imageBase64
    form.captchaCode = ''
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(loadCaptcha)

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/home')
  } catch (e) {
    // 登录失败（含验证码错误）：刷新验证码（旧验证码已被后端销毁）
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

// ===== 忘记密码 =====
const forgotVisible = ref(false)
const forgotStep = ref(0)
const sending = ref(false)
const resetting = ref(false)
const countdown = ref(0)
let timer = null

const forgotFormRef = ref()
const resetFormRef = ref()
const forgotForm = reactive({ username: '', email: '' })
const resetForm = reactive({ code: '', newPassword: '', confirmPassword: '' })

const forgotRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

const validateConfirm = (rule, value, callback) => {
  if (value !== resetForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const resetRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为 6-32 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

function maskEmail(email) {
  if (!email || !email.includes('@')) return email
  const [name, domain] = email.split('@')
  const shown = name.length <= 2 ? name[0] + '*' : name.slice(0, 2) + '***'
  return `${shown}@${domain}`
}

function openForgot() {
  forgotStep.value = 0
  forgotForm.username = form.username || ''
  forgotForm.email = ''
  resetForm.code = ''
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  forgotVisible.value = true
}

function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

onUnmounted(() => { if (timer) clearInterval(timer) })

async function sendCode() {
  const valid = await forgotFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (countdown.value > 0) return

  sending.value = true
  try {
    await forgotPassword({ username: forgotForm.username, email: forgotForm.email })
    ElMessage.success('验证码已发送至邮箱，请注意查收')
    forgotStep.value = 1
    startCountdown()
  } catch (e) {
    // 拦截器已提示
  } finally {
    sending.value = false
  }
}

async function submitReset() {
  const valid = await resetFormRef.value.validate().catch(() => false)
  if (!valid) return

  resetting.value = true
  try {
    await resetPassword({
      username: forgotForm.username,
      code: resetForm.code,
      newPassword: resetForm.newPassword
    })
    ElMessage.success('密码重置成功，请用新密码登录')
    forgotVisible.value = false
    form.password = ''
    loadCaptcha()
  } catch (e) {
    // 拦截器已提示
  } finally {
    resetting.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f7fa 50%, #ecfeff 100%);
  position: relative;
  overflow: hidden;
}
.auth-deco {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  opacity: 0.5;
}
.deco-1 {
  width: 340px;
  height: 340px;
  background: radial-gradient(circle, #c7d2fe 0%, transparent 70%);
  top: -80px;
  left: -80px;
}
.deco-2 {
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, #a5f3fc 0%, transparent 70%);
  bottom: -120px;
  right: -100px;
}
.auth-card {
  width: 440px;
  padding: 16px 12px;
  border-radius: 16px;
  z-index: 1;
}
.auth-brand {
  text-align: center;
  margin-bottom: 28px;
}
.brand-icon {
  font-size: 44px;
}
.auth-brand h2 {
  margin: 8px 0 4px;
  font-size: 24px;
  color: #303133;
}
.auth-brand p {
  margin: 0;
  font-size: 13px;
  color: #909399;
}
.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.captcha-img {
  width: 130px;
  height: 40px;
  border-radius: 6px;
  border: 1px solid #dcdfe6;
  cursor: pointer;
  flex-shrink: 0;
  object-fit: cover;
}
.auth-submit {
  width: 100%;
  letter-spacing: 8px;
}
.auth-links {
  font-size: 14px;
  color: #909399;
}
.row-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.forgot-link {
  color: #909399;
  text-decoration: none;
}
.forgot-link:hover {
  color: var(--primary-color, #6366f1);
}
.auth-links a:not(.forgot-link) {
  color: var(--primary-color, #6366f1);
  text-decoration: none;
}
.forgot-steps {
  margin-bottom: 24px;
}
</style>
