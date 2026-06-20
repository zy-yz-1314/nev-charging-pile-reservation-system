<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  myReservationsApi,
  startChargingApi,
  finishChargingApi,
  cancelReservationApi,
} from '@/api/reservation'

const router = useRouter()
const list = ref([])
const loading = ref(false)

const TAG = {
  PENDING: 'warning',
  CHARGING: 'primary',
  FINISHED: 'success',
  CANCELLED: 'info',
}

const load = async () => {
  loading.value = true
  try {
    const { data } = await myReservationsApi()
    list.value = data
  } catch (e) {
    /* 已全局提示 */
  } finally {
    loading.value = false
  }
}

const start = async (r) => {
  await startChargingApi(r.id)
  ElMessage.success('已开始充电')
  load()
}
const finish = async (r) => {
  await finishChargingApi(r.id)
  ElMessage.success('充电完成,已结算')
  load()
}
const cancel = async (r) => {
  try {
    await ElMessageBox.confirm('确认取消该预约?', '提示', { type: 'warning' })
  } catch (_) {
    return
  }
  await cancelReservationApi(r.id)
  ElMessage.success('已取消预约')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head pq-fade-up">
      <div>
        <h1 class="title">我的<span class="pq-gradient-text">预约</span></h1>
        <p class="subtitle">管理你的充电订单与充电流程</p>
      </div>
      <el-button :icon="'Refresh'" @click="load">刷新</el-button>
    </div>

    <div v-loading="loading" class="list">
      <article
        v-for="(r, i) in list"
        :key="r.id"
        class="r-card pq-glass pq-fade-up"
        :class="r.status.toLowerCase()"
        :style="{ animationDelay: i * 0.05 + 's' }"
      >
        <div class="bar" />
        <div class="r-main">
          <div class="r-row1">
            <span class="station">{{ r.stationName }}</span>
            <el-tag :type="TAG[r.status]" effect="dark" round size="small">{{ r.statusDesc }}</el-tag>
          </div>
          <div class="r-info">
            <span><el-icon><Cpu /></el-icon>{{ r.pileNo }} · {{ r.pileType === 'FAST' ? '快充' : '慢充' }} {{ r.power }}kW</span>
            <span><el-icon><Money /></el-icon>¥{{ r.price }}/度</span>
            <span class="ono">订单 {{ r.orderNo }}</span>
          </div>
          <div class="r-time">
            <span>预约 {{ r.reserveTime || '—' }}</span>
            <span v-if="r.startTime">开始 {{ r.startTime }}</span>
            <span v-if="r.endTime">结束 {{ r.endTime }}</span>
          </div>
        </div>

        <div class="r-side">
          <div v-if="r.status === 'FINISHED'" class="amount">
            <span class="a-num">¥{{ r.amount }}</span>
            <span class="a-lbl">{{ r.powerUsed }} 度 · {{ r.duration }} 分钟</span>
          </div>
          <div class="r-actions">
            <template v-if="r.status === 'PENDING'">
              <el-button type="primary" size="small" @click="start(r)">开始充电</el-button>
              <el-button size="small" @click="cancel(r)">取消</el-button>
            </template>
            <el-button v-else-if="r.status === 'CHARGING'" type="success" size="small" @click="finish(r)">
              结束充电
            </el-button>
          </div>
        </div>
      </article>

      <el-empty v-if="!loading && !list.length" description="还没有预约,去抢一个充电桩吧">
        <el-button type="primary" @click="router.push('/stations')">前往充电地图</el-button>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 26px;
}
.title {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 6px;
}
.subtitle {
  color: var(--pq-text-dim);
  margin: 0;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}
.r-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 22px 20px 26px;
  overflow: hidden;
}
.bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: var(--pq-text-faint);
}
.r-card.pending .bar { background: var(--pq-reserved); }
.r-card.charging .bar { background: var(--pq-charging); }
.r-card.finished .bar { background: var(--pq-idle); }
.r-card.cancelled .bar { background: var(--pq-text-faint); }
.r-main {
  flex: 1;
  min-width: 0;
}
.r-row1 {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.station {
  font-size: 17px;
  font-weight: 700;
}
.r-info {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  color: var(--pq-text-dim);
  font-size: 13px;
  margin-bottom: 8px;
}
.r-info span {
  display: flex;
  align-items: center;
  gap: 5px;
}
.r-info .ono {
  font-variant-numeric: tabular-nums;
}
.r-time {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--pq-text-faint);
  font-size: 12px;
}
.r-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.amount {
  text-align: right;
}
.a-num {
  display: block;
  font-size: 22px;
  font-weight: 800;
  color: var(--pq-primary);
}
.a-lbl {
  font-size: 11px;
  color: var(--pq-text-dim);
}
.r-actions {
  display: flex;
  gap: 8px;
}
@media (max-width: 680px) {
  .r-card {
    flex-direction: column;
    align-items: stretch;
  }
  .r-side {
    align-items: stretch;
  }
}
</style>
