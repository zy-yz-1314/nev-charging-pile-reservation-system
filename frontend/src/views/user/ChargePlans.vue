<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createPlanApi, myPlansApi, updatePlanApi, deletePlanApi } from '@/api/chargeplan'

const plans = ref([])
const loading = ref(false)
const generating = ref(false)

const form = reactive({
  commuteDays: [1, 3, 5],
  commuteKm: 30,
  hasHomeCharger: false,
  consumptionKwhPer100km: 15,
  lng: null,
  lat: null,
})

const weekLabels = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']

const load = async () => {
  loading.value = true
  try { const { data } = await myPlansApi(); plans.value = data } catch (_) {}
  finally { loading.value = false }
}

const generate = async () => {
  if (!form.commuteDays.length) { ElMessage.warning('请选择通勤日'); return }
  generating.value = true
  try {
    if (navigator.geolocation) {
      try {
        const pos = await new Promise((resolve, reject) =>
          navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: true, timeout: 6000 }))
        form.lng = Number(pos.coords.longitude.toFixed(6))
        form.lat = Number(pos.coords.latitude.toFixed(6))
      } catch (_) { /* 定位失败允许手动填 */ }
    }
    const { data } = await createPlanApi({
      commuteDays: form.commuteDays,
      commuteKm: form.commuteKm,
      hasHomeCharger: form.hasHomeCharger,
      consumptionKwhPer100km: form.consumptionKwhPer100km,
      lng: form.lng,
      lat: form.lat,
    })
    if (data.enabled === 0) { ElMessage.info(data.reason); return }
    ElMessage.success(data.reason || '计划已生成')
    await load()
  } catch (e) { /* 全局提示 */ }
  finally { generating.value = false }
}

const toggle = async (plan) => {
  const next = plan.enabled ? 0 : 1
  await updatePlanApi(plan.id, next, plan.chargeTime)
  ElMessage.success(next ? '计划已启用' : '计划已暂停')
  load()
}

const remove = async (plan) => {
  try { await ElMessageBox.confirm('确认删除该计划?', '提示', { type: 'warning' }) } catch (_) { return }
  await deletePlanApi(plan.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head pq-fade-up">
      <h1 class="title">📅 充电<span class="pq-gradient-text">计划</span></h1>
      <p class="subtitle">智能向导:输入通勤习惯,系统自动推荐固定站点并到点预约</p>
    </div>

    <!-- 向导表单 -->
    <section class="form-card pq-glass pq-fade-up">
      <h3>📝 充电计划向导</h3>
      <div class="form-row">
        <div class="field">
          <label>通勤日</label>
          <el-checkbox-group v-model="form.commuteDays">
            <el-checkbox v-for="(lb, i) in weekLabels" v-show="i>0" :key="i" :value="i" :label="lb" />
          </el-checkbox-group>
        </div>
      </div>
      <div class="form-row">
        <div class="field">
          <label>单程里程 (km)</label>
          <el-input-number v-model="form.commuteKm" :min="1" :max="200" :step="1" />
        </div>
        <div class="field">
          <label>百公里电耗 (kWh)</label>
          <el-input-number v-model="form.consumptionKwhPer100km" :min="5" :max="50" :step="1" />
        </div>
        <div class="field">
          <label>是否有家充</label>
          <el-checkbox v-model="form.hasHomeCharger" />
        </div>
      </div>
      <el-button type="primary" size="large" :loading="generating" @click="generate" class="gen-btn">
        <el-icon><Cpu /></el-icon>&nbsp;生成计划
      </el-button>
    </section>

    <!-- 计划列表 -->
    <section class="plan-list">
      <h2 class="sec-title">我的计划</h2>
      <div v-loading="loading" class="list">
        <article v-for="p in plans" :key="p.id" class="p-card pq-glass pq-fade-up" :class="{ disabled: !p.enabled }">
          <div class="p-main">
            <div class="p-row1">
              <b>{{ p.stationName }}</b>
              <el-tag :type="p.enabled ? 'success' : 'info'" effect="dark" round size="small">{{ p.enabled ? '启用中' : '已暂停' }}</el-tag>
            </div>
            <div class="p-meta">
              <span>📅 每周 {{ p.cronDays }}</span>
              <span>⏰ {{ p.chargeTime?.substring(0, 5) }}</span>
              <span v-if="p.commuteKm">🛣 {{ p.commuteKm }}km 通勤</span>
              <span>⚡ 目标 {{ p.targetEnergy }} 度</span>
            </div>
          </div>
          <div class="p-actions">
            <el-button size="small" @click="toggle(p)">{{ p.enabled ? '暂停' : '启用' }}</el-button>
            <el-button size="small" type="danger" plain @click="remove(p)">删除</el-button>
          </div>
        </article>
        <el-empty v-if="!loading && !plans.length" description="还没有充电计划,用上方的向导生成一个" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.head { margin-bottom: 24px; }
.title { font-size: 32px; font-weight: 800; margin: 0 0 6px; }
.subtitle { color: var(--pq-text-dim); margin: 0; }

.form-card { padding: 28px; margin-bottom: 28px; }
.form-card h3 { margin: 0 0 18px; font-size: 16px; }
.form-row { display: flex; gap: 20px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.field { display: flex; flex-direction: column; gap: 6px; min-width: 150px; }
.field label { font-size: 12px; font-weight: 600; color: var(--pq-text-dim); text-transform: uppercase; letter-spacing: 0.5px; }
.gen-btn { font-weight: 700; font-size: 15px; padding: 12px 28px; }

.sec-title { font-size: 18px; font-weight: 700; margin: 0 0 14px; }
.list { display: flex; flex-direction: column; gap: 12px; min-height: 150px; }
.p-card { display: flex; align-items: center; gap: 16px; padding: 18px 22px; }
.p-card.disabled { opacity: 0.55; }
.p-main { flex: 1; }
.p-row1 { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.p-row1 b { font-size: 16px; }
.p-meta { display: flex; flex-wrap: wrap; gap: 14px; font-size: 12px; color: var(--pq-text-dim); }
.p-actions { display: flex; gap: 8px; flex-shrink: 0; }
</style>
