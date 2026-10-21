<template>
  <div class="auth-page">
    <div class="auth-deco deco-1"></div>
    <div class="auth-deco deco-2"></div>

    <el-card class="auth-card" shadow="always">
      <div class="auth-brand">
        <div class="brand-icon">🎓</div>
        <h2>注册智学问答</h2>
        <p>创建账号，开始你的智能学习之旅</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top" @keyup.enter="onSubmit">
        <el-form-item prop="username" label="用户名">
          <el-input v-model="form.username" placeholder="3-20 位字母、数字或下划线" clearable />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item prop="password" label="密码">
              <el-input v-model="form.password" type="password" placeholder="6-32 位" show-password />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="confirmPassword" label="确认密码">
              <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item prop="nickname" label="昵称（选填）">
              <el-input v-model="form.nickname" placeholder="默认同用户名" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="email" label="邮箱（选填）">
              <el-input v-model="form.email" placeholder="name@example.com" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" class="auth-submit" :loading="loading" @click="onSubmit">
            注 册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-links">
        已有账号？<router-link to="/login">直接登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: ''
})

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度需为 3-20 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为 6-32 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
      email: form.email || undefined
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    // 错误提示已由 axios 拦截器统一弹出
  } finally {
    loading.value = false
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
  padding: 40px 0;
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
  right: -80px;
}
.deco-2 {
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, #a5f3fc 0%, transparent 70%);
  bottom: -120px;
  left: -100px;
}
.auth-card {
  width: 560px;
  padding: 16px 12px;
  border-radius: 16px;
  z-index: 1;
}
.auth-brand {
  text-align: center;
  margin-bottom: 20px;
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
.auth-submit {
  width: 100%;
  letter-spacing: 8px;
}
.auth-links {
  text-align: center;
  font-size: 14px;
  color: #909399;
}
.auth-links a {
  color: var(--primary-color, #6366f1);
  text-decoration: none;
}
</style>
