<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { registerApi } from '@/api/auth'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirm: '',
  nickname: '',
  phone: '',
  carPlate: '',
})
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度 3-20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度 6-20 位', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) =>
        v === form.password ? cb() : cb(new Error('两次密码不一致')),
      trigger: 'blur',
    },
  ],
}

const onSubmit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await registerApi({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      phone: form.phone,
      carPlate: form.carPlate,
    })
    ElMessage.success('注册成功,请登录')
    router.push('/login')
  } catch (e) {
    /* 已全局提示 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-register">
    <div class="glow glow-1" />
    <div class="glow glow-2" />
    <div class="card pq-glass pq-fade-up">
      <div class="brand">
        <div class="logo"><el-icon :size="22"><Lightning /></el-icon></div>
        <span>Power<span class="pq-gradient-text">Queue</span></span>
      </div>
      <h2>创建账号</h2>
      <p class="sub">注册即送 ¥100 体验金</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top">
        <el-form-item prop="username" label="用户名">
          <el-input v-model="form.username" placeholder="3-20 位" :prefix-icon="'User'" />
        </el-form-item>
        <div class="grid2">
          <el-form-item prop="password" label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="6-20 位" />
          </el-form-item>
          <el-form-item prop="confirm" label="确认密码">
            <el-input v-model="form.confirm" type="password" show-password placeholder="再次输入" />
          </el-form-item>
        </div>
        <div class="grid2">
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" placeholder="选填" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="选填" />
          </el-form-item>
        </div>
        <el-form-item label="车牌号">
          <el-input v-model="form.carPlate" placeholder="选填,如 京A·12345" :prefix-icon="'Van'" />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">
          注 册
        </el-button>
      </el-form>

      <div class="to-login">
        已有账号?<router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-register {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 40px 20px;
}
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.4;
}
.glow-1 {
  width: 460px;
  height: 460px;
  background: rgba(20, 224, 160, 0.5);
  top: -160px;
  left: -100px;
}
.glow-2 {
  width: 420px;
  height: 420px;
  background: rgba(42, 214, 255, 0.45);
  bottom: -180px;
  right: -120px;
}
.card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 460px;
  padding: 36px 38px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 20px;
  font-weight: 800;
  margin-bottom: 22px;
}
.logo {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: #042018;
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2));
  box-shadow: var(--pq-glow);
}
h2 {
  font-size: 24px;
  margin: 0 0 6px;
}
.sub {
  color: var(--pq-primary);
  margin: 0 0 22px;
  font-size: 14px;
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.submit {
  width: 100%;
  height: 46px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 4px;
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2));
  border: none;
  color: #042018;
  margin-top: 4px;
}
.to-login {
  margin-top: 20px;
  text-align: center;
  font-size: 14px;
  color: var(--pq-text-dim);
}
.to-login a {
  color: var(--pq-primary);
  font-weight: 600;
}
@media (max-width: 520px) {
  .grid2 {
    grid-template-columns: 1fr;
  }
}
</style>
