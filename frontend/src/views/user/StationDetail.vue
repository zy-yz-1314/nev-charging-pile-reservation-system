<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getStationApi, getPilesApi } from '@/api/station'
import { grabPileApi } from '@/api/reservation'

const route = useRoute()
const router = useRouter()
const stationId = route.params.id

const station = ref(null)
const piles = ref([])
const loading = ref(false)
const grabbingId = ref(null)
const filter = ref('ALL')
let timer = null

const STATUS = {
  IDLE: { label: '空闲', cls: 'idle' },
  CHARGING: { label: '充电中', cls: 'charging' },
  RESERVED: { label: '已预约', cls: 'reserved' },
  FAULT: { label: '故障', cls: 'fault' },
}

const filters = [
  { key: 'ALL', label: '全部' },
  { key: 'IDLE', label: '仅空闲' },
  { key: 'FAST', label: '快充' },
  { key: 'SLOW', label: '慢充' },
]

const shownPiles = computed(() => {
  if (filter.value === 'ALL') return piles.value
  if (filter.value === 'IDLE') return piles.value.filter((p) => p.status === 'IDLE')
  return piles.value.filter((p) => p.type === filter.value)
})

const idleCount = computed(() => piles.value.filter((p) => p.status === 'IDLE').length)

const loadStation = async () => {
  const { data } = await getStationApi(stationId)
  station.value = data
}
const loadPiles = async () => {
  const { data } = await getPilesApi(stationId)
  piles.value = data
}

const grab = async (pile) => {
  if (pile.status !== 'IDLE') return
  try {
    await ElMessageBox.confirm(
      `确认抢约充电桩 ${pile.pileNo}(${pile.typeDesc} · ${pile.power}kW · ¥${pile.price}/度)?`,
      '抢桩确认',
      { type: 'warning', confirmButtonText: '⚡ 立即抢约', cancelButtonText: '再看看' },
    )
  } catch (_) {
    return
  }
  grabbingId.value = pile.id
  try {
    await grabPileApi(pile.id)
    ElMessage.success('抢桩成功!请前往「我的预约」')
    await loadPiles()
    await loadStation()
  } catch (e) {
    await loadPiles()
  } finally {
    grabbingId.value = null
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([loadStation(), loadPiles()])
  } finally {
    loading.value = false
  }
  // 实时轮询(后端走 Redis 缓存,降低 DB 压力)
  timer = setInterval(loadPiles, 5000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div v-loading="loading">
    <el-button text class="back" @click="router.push('/stations')">
      <el-icon><ArrowLeftBold /></el-icon>&nbsp;返回充电地图
    </el-button>

    <!-- 站点信息头 -->
    <header v-if="station" class="s-head pq-glass pq-fade-up">
      <div class="s-icon"><el-icon :size="26"><OfficeBuilding /></el-icon></div>
      <div class="s-meta">
        <h1>{{ station.name }}</h1>
        <p><el-icon><LocationFilled /></el-icon>{{ station.address }}</p>
      </div>
      <div class="s-live">
        <span class="live-dot" />实时 · 空闲 <b>{{ idleCount }}</b> / {{ station.totalPiles }}
      </div>
    </header>

    <!-- 筛选 -->
    <div class="filters pq-fade-up">
      <button
        v-for="f in filters"
        :key="f.key"
        class="f-btn"
        :class="{ on: filter === f.key }"
        @click="filter = f.key"
      >
        {{ f.label }}
      </button>
      <span class="legend">
        <i class="lg idle" />空闲
        <i class="lg charging" />充电中
        <i class="lg reserved" />已预约
        <i class="lg fault" />故障
      </span>
    </div>

    <!-- 充电桩网格 -->
    <div class="pile-grid">
      <div
        v-for="(p, i) in shownPiles"
        :key="p.id"
        class="pile"
        :class="[STATUS[p.status]?.cls, { grabbable: p.status === 'IDLE' }]"
        :style="{ animationDelay: i * 0.03 + 's' }"
      >
        <div class="pile-top">
          <span class="pno">{{ p.pileNo }}</span>
          <span class="ptype" :class="p.type === 'FAST' ? 'fast' : 'slow'">{{ p.typeDesc }}</span>
        </div>
        <div class="pile-power">
          <span class="pw">{{ p.power }}</span><span class="unit">kW</span>
        </div>
        <div class="pile-price">¥{{ p.price }}/度</div>
        <div class="pile-status">
          <span class="sdot" /><span>{{ STATUS[p.status]?.label }}</span>
        </div>
        <el-button
          v-if="p.status === 'IDLE'"
          class="grab-btn"
          :loading="grabbingId === p.id"
          @click="grab(p)"
        >
          ⚡ 抢约
        </el-button>
      </div>

      <el-empty v-if="!loading && !shownPiles.length" description="没有符合条件的充电桩" />
    </div>
  </div>
</template>

<style scoped>
.back {
  color: var(--pq-text-dim);
  margin-bottom: 14px;
}
.s-head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 24px;
  margin-bottom: 22px;
}
.s-icon {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 15px;
  color: var(--pq-primary);
  background: rgba(20, 224, 160, 0.1);
  border: 1px solid rgba(20, 224, 160, 0.22);
}
.s-meta {
  flex: 1;
}
.s-meta h1 {
  font-size: 24px;
  margin: 0 0 5px;
}
.s-meta p {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  color: var(--pq-text-dim);
  font-size: 13px;
}
.s-live {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--pq-text-dim);
}
.s-live b {
  color: var(--pq-primary);
  font-size: 16px;
}
.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--pq-primary);
  box-shadow: var(--pq-glow);
  animation: pq-pulse 1.6s infinite;
}
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.f-btn {
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid var(--pq-border);
  background: var(--pq-surface);
  color: var(--pq-text-dim);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s var(--pq-ease);
}
.f-btn:hover {
  color: var(--pq-text);
}
.f-btn.on {
  color: var(--pq-primary);
  border-color: rgba(20, 224, 160, 0.4);
  background: rgba(20, 224, 160, 0.12);
}
.legend {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--pq-text-dim);
}
.lg {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  display: inline-block;
  margin-left: 8px;
}
.lg.idle { background: var(--pq-idle); }
.lg.charging { background: var(--pq-charging); }
.lg.reserved { background: var(--pq-reserved); }
.lg.fault { background: var(--pq-fault); }

.pile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}
.pile {
  position: relative;
  padding: 18px;
  border-radius: var(--pq-radius);
  background: var(--pq-surface);
  border: 1px solid var(--pq-border);
  overflow: hidden;
  animation: pq-fade-up 0.45s var(--pq-ease) both;
  transition: transform 0.3s var(--pq-ease), border-color 0.3s;
}
.pile::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: var(--bar, var(--pq-text-faint));
}
.pile.idle {
  --bar: var(--pq-idle);
  border-color: rgba(20, 224, 160, 0.3);
}
.pile.charging {
  --bar: var(--pq-charging);
}
.pile.reserved {
  --bar: var(--pq-reserved);
}
.pile.fault {
  --bar: var(--pq-fault);
  opacity: 0.65;
}
.pile.grabbable:hover {
  transform: translateY(-4px);
  border-color: rgba(20, 224, 160, 0.55);
  box-shadow: var(--pq-glow);
}
.pile-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.pno {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0.5px;
}
.ptype {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 700;
}
.ptype.fast {
  color: var(--pq-charging);
  background: rgba(42, 214, 255, 0.14);
}
.ptype.slow {
  color: var(--pq-text-dim);
  background: var(--pq-surface-2);
}
.pile-power {
  display: flex;
  align-items: baseline;
  gap: 3px;
}
.pile-power .pw {
  font-size: 26px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.pile-power .unit {
  font-size: 12px;
  color: var(--pq-text-dim);
}
.pile-price {
  font-size: 12px;
  color: var(--pq-text-dim);
  margin: 2px 0 12px;
}
.pile-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}
.sdot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--bar);
}
.idle .sdot {
  box-shadow: 0 0 10px var(--pq-idle);
}
.grab-btn {
  width: 100%;
  margin-top: 14px;
  border: none;
  font-weight: 700;
  color: #042018;
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2));
}
</style>
