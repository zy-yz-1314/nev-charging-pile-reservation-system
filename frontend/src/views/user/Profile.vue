<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMeApi, updateProfileApi, rechargeApi } from '@/api/user'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()
const me = ref(store.user || {})
const saving = ref(false)

const form = reactive({ nickname: '', phone: '', carPlate: '' })

const rechargeVisible = ref(false)
const rechargeAmount = ref(100)
const quickAmounts = [50, 100, 200, 500]

const loadMe = async () => {
  const { data } = await getMeApi()
  me.value = data
  store.setUser(data)
  form.nickname = data.nickname || ''
  form.phone = data.phone || ''
  form.carPlate = data.carPlate || ''
}

const save = async () => {
  saving.value = true
  try {
    await updateProfileApi(form)
    ElMessage.success('资料已更新')
    await loadMe()
  } finally {
    saving.value = false
  }
}

const doRecharge = async () => {
  if (!rechargeAmount.value || rechargeAmount.value <= 0) {
    ElMessage.warning('请输入有效金额')
    return
  }
  await rechargeApi(rechargeAmount.value)
  ElMessage.success(`充值成功 ¥${rechargeAmount.value}`)
  rechargeVisible.value = false
  await loadMe()
}

const logout = async () => {
  try {
    await ElMessageBox.confirm('确认退出登录?', '提示', { type: 'warning' })
    store.logout()
    router.push('/login')
  } catch (_) {
    /* 取消 */
  }
}

onMounted(loadMe)
</script>

<template>
  <div>
    <h1 class="title pq-fade-up">个人<span class="pq-gradient-text">中心</span></h1>

    <div class="layout">
      <!-- 概览卡 -->
      <section class="overview pq-glass pq-fade-up">
        <div class="ov-top">
          <el-avatar :size="64" class="avatar">{{ (me.nickname || me.username || 'U').charAt(0) }}</el-avatar>
          <div>
            <div class="nick">{{ me.nickname || me.username }}</div>
            <div class="uname">@{{ me.username }}</div>
          </div>
          <el-tag :type="me.role === 'ADMIN' ? 'danger' : 'success'" effect="dark" round>
            {{ me.role === 'ADMIN' ? '管理员' : '车主' }}
          </el-tag>
        </div>

        <div class="balance">
          <div>
            <div class="b-lbl">账户余额</div>
            <div class="b-num">¥{{ me.balance ?? 0 }}</div>
          </div>
          <el-button type="primary" :icon="'Wallet'" @click="rechargeVisible = true">充值</el-button>
        </div>

        <div class="kv">
          <div class="row"><span><el-icon><Van /></el-icon>车牌号</span><b>{{ me.carPlate || '未绑定' }}</b></div>
          <div class="row"><span><el-icon><Phone /></el-icon>手机号</span><b>{{ me.phone || '未绑定' }}</b></div>
        </div>

        <el-button text class="logout" @click="logout">
          <el-icon><SwitchButton /></el-icon>&nbsp;退出登录
        </el-button>
      </section>

      <!-- 编辑资料 -->
      <section class="edit pq-glass pq-fade-up">
        <h3>编辑资料</h3>
        <el-form :model="form" label-position="top" size="large">
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" placeholder="昵称" :prefix-icon="'User'" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="手机号" :prefix-icon="'Phone'" />
          </el-form-item>
          <el-form-item label="车牌号">
            <el-input v-model="form.carPlate" placeholder="如 京A·12345" :prefix-icon="'Van'" />
          </el-form-item>
          <el-button type="primary" class="save" :loading="saving" @click="save">保存修改</el-button>
        </el-form>
      </section>
    </div>

    <!-- 充值弹窗 -->
    <el-dialog v-model="rechargeVisible" title="账户充值" width="380px" align-center>
      <div class="quick">
        <button
          v-for="a in quickAmounts"
          :key="a"
          class="q-btn"
          :class="{ on: rechargeAmount === a }"
          @click="rechargeAmount = a"
        >
          ¥{{ a }}
        </button>
      </div>
      <el-input-number v-model="rechargeAmount" :min="1" :max="100000" :step="50" style="width: 100%" />
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" @click="doRecharge">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.title {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 26px;
}
.layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 22px;
  align-items: start;
}
.overview {
  padding: 26px;
}
.ov-top {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}
.avatar {
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2));
  color: #042018;
  font-size: 26px;
  font-weight: 800;
}
.nick {
  font-size: 20px;
  font-weight: 700;
}
.uname {
  color: var(--pq-text-dim);
  font-size: 13px;
}
.ov-top .el-tag {
  margin-left: auto;
}
.balance {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-radius: var(--pq-radius);
  background: linear-gradient(135deg, rgba(20, 224, 160, 0.14), rgba(42, 214, 255, 0.1));
  border: 1px solid rgba(20, 224, 160, 0.25);
  margin-bottom: 20px;
}
.b-lbl {
  font-size: 13px;
  color: var(--pq-text-dim);
}
.b-num {
  font-size: 30px;
  font-weight: 800;
  color: var(--pq-primary);
}
.kv {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.kv .row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
}
.kv .row span {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--pq-text-dim);
}
.logout {
  margin-top: 20px;
  color: var(--pq-danger);
}
.edit {
  padding: 26px;
}
.edit h3 {
  margin: 0 0 18px;
  font-size: 18px;
}
.save {
  width: 100%;
}
.quick {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
.q-btn {
  padding: 10px 0;
  border-radius: 10px;
  border: 1px solid var(--pq-border);
  background: var(--pq-surface);
  color: var(--pq-text);
  font-weight: 700;
  cursor: pointer;
  transition: all 0.25s;
}
.q-btn.on {
  color: var(--pq-primary);
  border-color: rgba(20, 224, 160, 0.45);
  background: rgba(20, 224, 160, 0.12);
}
@media (max-width: 760px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
