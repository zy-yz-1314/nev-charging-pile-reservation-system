<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  myReservationsApi,
  startChargingApi,
  finishChargingApi,
  cancelReservationApi,
} from '@/api/reservation'
import { queueConfirmApi } from '@/api/queue'
import { ws } from '@/utils/websocket'

const router = useRouter()
const list = ref([])
const loading = ref(false)
let unsubWs = null

const TAG = {
  PENDING: 'warning',
  QUEUED: '',
  WAITING_CONFIRM: 'danger',
  CHARGING: 'primary',
  FINISHED: 'success',
  CANCELLED: 'info',
}

// 倒计时(WAITING_CONFIRM 状态下 10 分钟窗口)
const now = ref(Date.now())
let ticker = null

const confirmCountdown = (r) => {
  if (r.status !== 'WAITING_CONFIRM' || !r.reserveTime) return null
  const deadline = new Date(r.reserveTime).getTime() + 10 * 60 * 1000
  const left = Math.max(0, Math.floor((deadline - now.value) / 1000))
  if (left <= 0) return '已超时'
  const m = Math.floor(left / 60)
  const s = left % 60
  return `剩余 ${m}:${String(s).padStart(2, '0')}`
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
  try { await ElMessageBox.confirm('确认取消该预约?', '提示', { type: 'warning' }) } catch (_) { return }
  await cancelReservationApi(r.id)
  ElMessage.success('已取消预约')
  load()
}
const confirmQiuyue = async (r) => {
  try { await ElMessageBox.confirm('确认立即占位充电?', '轮到你啦', { type: 'success', confirmButtonText: '⚡ 确认占位' }) } catch (_) { return }
  try { await queueConfirmApi(r.pileId); ElMessage.success('占位成功!'); load() } catch (e) { /* 已提示 */ }
}

// 监听 WebSocket: QUEUE_TURN 定向消息(仅刷新列表,toast 由 UserLayout 全局处理)
const onWsQueue = () => { load() }

onMounted(() => {
  load()
  ws.connect()
  unsubWs = ws.on('QUEUE_TURN', onWsQueue)
  ticker = setInterval(() => { now.value = Date.now() }, 1000)
})
onUnmounted(() => {
  if (unsubWs) unsubWs()
  clearInterval(ticker)
})
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
          <!-- 排队中 -->
          <div v-else-if="r.status === 'QUEUED'" class="queue-hint">
            <span class="qh">📋 排队中</span>
          </div>
          <!-- 轮到,待确认 -->
          <div v-else-if="r.status === 'WAITING_CONFIRM'" class="confirm-hint">
            <span class="ch-urgent">⚡ 轮到你了!</span>
            <span class="ch-countdown" v-if="confirmCountdown(r)">{{ confirmCountdown(r) }}</span>
          </div>
          <div class="r-actions">
            <template v-if="r.status === 'PENDING'">
              <el-button type="primary" size="small" @click="start(r)">开始充电</el-button>
              <el-button size="small" @click="cancel(r)">取消</el-button>
            </template>
            <template v-else-if="r.status === 'WAITING_CONFIRM'">
              <el-button type="success" size="small" @click="confirmQiuyue(r)">⚡ 确认占位</el-button>
              <el-button size="small" @click="cancel(r)">放弃</el-button>
            </template>
            <el-button v-else-if="r.status === 'QUEUED'" size="small" @click="cancel(r)">退出排队</el-button>
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
.r-card.queued .bar { background: var(--pq-warning); }
.r-card.waiting_confirm .bar { background: var(--pq-danger); animation: pq-pulse 1s infinite; }
.queue-hint { text-align: right; }
.queue-hint .qh { font-weight: 700; color: var(--pq-warning); font-size: 14px; }
.confirm-hint { text-align: right; }
.confirm-hint .ch-urgent { display: block; font-weight: 800; color: var(--pq-danger); font-size: 15px; }
.confirm-hint .ch-countdown { display: block; font-size: 12px; color: var(--pq-text-dim); margin-top: 2px; }
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
