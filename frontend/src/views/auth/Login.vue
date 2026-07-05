<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginApi } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const features = [
  { icon: 'Lightning', text: '空闲快充桩一键「抢约」' },
  { icon: 'Refresh', text: '充电桩状态实时刷新' },
  { icon: 'Lock', text: '高并发防超卖保障' },
]

const onSubmit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const { data } = await loginApi(form)
    store.setAuth(data.token, data.user)
    ElMessage.success('登录成功')
    router.push(data.user.role === 'ADMIN' ? '/admin' : '/stations')
  } catch (e) {
    /* 已全局提示 */
  } finally {
    loading.value = false
  }
}

const fillDemo = (role) => {
  form.username = role
  form.password = ''
}
</script>

<template>
  <div class="auth">
    <!-- 左侧品牌区 -->
    <section class="hero">
      <div class="glow glow-1" />
      <div class="glow glow-2" />
      <div class="hero-inner">
        <div class="brand">
          <div class="logo"><el-icon :size="24"><Lightning /></el-icon></div>
          <span>Power<span class="pq-gradient-text">Queue</span></span>
        </div>
        <h1 class="slogan">
          新能源充电桩<br />
          <span class="pq-gradient-text">高并发预约调度</span>平台
        </h1>
        <p class="desc">解决高峰时段充电排队痛点 —— 实时查桩、极速抢约、智能调度。</p>
        <ul class="features">
          <li v-for="f in features" :key="f.text">
            <span class="fi"><el-icon><component :is="f.icon" /></el-icon></span>
            {{ f.text }}
          </li>
        </ul>
      </div>
    </section>

    <!-- 右侧登录表单 -->
    <section class="panel">
      <div class="card pq-fade-up">
        <h2>欢迎回来 👋</h2>
        <p class="sub">登录以预约充电桩</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="'User'" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="密码"
              :prefix-icon="'Lock'"
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form>

        <div class="demo">
          <span>演示账号:</span>
          <el-link type="primary" :underline="false" @click="fillDemo('admin')">管理员 admin</el-link>
          <el-divider direction="vertical" />
          <el-link type="primary" :underline="false" @click="fillDemo('user')">车主 user</el-link>
          <span class="pwd">密码见 README / 环境变量 POWERQUEUE_DEMO_PASSWORD</span>
        </div>

        <div class="to-register">
          还没有账号?<router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  min-height: 100vh;
}
.hero {
  position: relative;
  overflow: hidden;
  background: linear-gradient(160deg, #06231c 0%, #071a26 55%, #0a0e16 100%);
  display: flex;
  align-items: center;
}
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
}
.glow-1 {
  width: 440px;
  height: 440px;
  background: rgba(20, 224, 160, 0.5);
  top: -120px;
  right: -80px;
}
.glow-2 {
  width: 380px;
  height: 380px;
  background: rgba(42, 214, 255, 0.4);
  bottom: -120px;
  left: -60px;
}
.hero-inner {
  position: relative;
  z-index: 2;
  padding: 0 64px;
  max-width: 560px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 22px;
  font-weight: 800;
  margin-bottom: 48px;
}
.logo {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 13px;
  color: #042018;
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2));
  box-shadow: var(--pq-glow);
}
.slogan {
  font-size: 44px;
  line-height: 1.18;
  font-weight: 800;
  margin: 0 0 18px;
  letter-spacing: 0.5px;
}
.desc {
  color: var(--pq-text-dim);
  font-size: 15px;
  line-height: 1.7;
  margin-bottom: 36px;
}
.features {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.features li {
  display: flex;
  align-items: center;
  gap: 13px;
  font-size: 15px;
  color: var(--pq-text);
}
.fi {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: var(--pq-primary);
  background: rgba(20, 224, 160, 0.12);
  border: 1px solid rgba(20, 224, 160, 0.25);
}
.panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}
.card {
  width: 100%;
  max-width: 380px;
}
.card h2 {
  font-size: 26px;
  margin: 0 0 6px;
}
.sub {
  color: var(--pq-text-dim);
  margin: 0 0 28px;
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
}
.demo {
  margin-top: 20px;
  font-size: 13px;
  color: var(--pq-text-dim);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.demo .pwd {
  margin-left: auto;
  color: var(--pq-text-faint);
}
.to-register {
  margin-top: 22px;
  text-align: center;
  font-size: 14px;
  color: var(--pq-text-dim);
}
.to-register a {
  color: var(--pq-primary);
  font-weight: 600;
}
@media (max-width: 880px) {
  .auth {
    grid-template-columns: 1fr;
  }
  .hero {
    display: none;
  }
}
</style>
