<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listStationsApi, stationLoadApi } from '@/api/station'

const router = useRouter()
const loading = ref(false)
const keyword = ref('')
const stations = ref([])
const loads = ref({}) // stationId → StationLoadVO

const load = async () => {
  loading.value = true
  try {
    const { data } = await listStationsApi(keyword.value)
    stations.value = data
    // 异步拉取各站负载,不阻塞渲染
    data.forEach(async (s) => {
      try {
        const r = await stationLoadApi(s.id)
        loads.value[s.id] = r.data
      } catch (_) { /* 非关键 */ }
    })
  } catch (e) {
    /* 已全局提示 */
  } finally {
    loading.value = false
  }
}

const loadBadge = (s) => {
  const l = loads.value[s.id]
  if (!l) return null
  return { level: l.loadLevel, rate: l.idleRate, coeff: l.loadCoefficient }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="head pq-fade-up">
      <div>
        <h1 class="title">充电<span class="pq-gradient-text">地图</span></h1>
        <p class="subtitle">选择充电站,或直接智能匹配最优桩位</p>
      </div>
      <div class="head-actions">
        <el-button type="primary" size="large" class="smart-btn" @click="router.push('/smart-charge')">
          <el-icon><Cpu /></el-icon>&nbsp;⚡ 我要充电
        </el-button>
        <el-input
          v-model="keyword"
          class="search"
          placeholder="搜索站点名称 / 地址"
          :prefix-icon="'Search'"
          clearable
          @keyup.enter="load"
          @clear="load"
        >
          <template #append>
            <el-button :icon="'Search'" @click="load">搜索</el-button>
          </template>
        </el-input>
      </div>
    </div>

    <div v-loading="loading" class="grid">
      <article
        v-for="(s, i) in stations"
        :key="s.id"
        class="card pq-glass pq-glass-hover pq-fade-up"
        :style="{ animationDelay: i * 0.05 + 's' }"
        @click="router.push(`/stations/${s.id}`)"
      >
        <div class="card-top">
          <div class="s-icon"><el-icon :size="22"><OfficeBuilding /></el-icon></div>
          <el-tag v-if="s.status === 1" type="success" effect="dark" round size="small">营业中</el-tag>
          <el-tag v-else type="info" effect="dark" round size="small">停业</el-tag>
        </div>
        <h3 class="s-name">{{ s.name }}</h3>
        <p class="s-addr"><el-icon><LocationFilled /></el-icon>{{ s.address }}</p>

        <div class="stats">
          <div class="stat hot">
            <span class="num">{{ s.fastIdle }}</span>
            <span class="lbl">空闲快充</span>
          </div>
          <div class="stat">
            <span class="num">{{ s.idlePiles }}</span>
            <span class="lbl">空闲总数</span>
          </div>
          <div class="stat">
            <span class="num">{{ s.totalPiles }}</span>
            <span class="lbl">充电桩</span>
          </div>
        </div>

        <div v-if="loadBadge(s)" class="load-bar">
          <span class="ldot" :class="loadBadge(s).level.toLowerCase()"></span>
          <span class="lrate">空闲率 {{ loadBadge(s).rate }}%</span>
          <span class="lcoeff">×{{ Number(loadBadge(s).coeff).toFixed(2) }}</span>
          <el-tag
            :type="loadBadge(s).level === 'GREEN' ? 'success' : loadBadge(s).level === 'RED' ? 'danger' : 'warning'"
            effect="dark" round size="small"
          >
            {{ loadBadge(s).level === 'GREEN' ? ' 🟢 宽松' : loadBadge(s).level === 'RED' ? ' 🔴 高峰' : ' 🟡 较忙' }}
          </el-tag>
        </div>

        <div class="enter">
          查看桩位 <el-icon><ArrowRightBold /></el-icon>
        </div>
      </article>

      <el-empty v-if="!loading && !stations.length" description="暂无匹配的充电站" />
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
  flex-wrap: wrap;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.smart-btn {
  font-weight: 800;
  font-size: 16px;
  padding: 14px 24px;
  border: none;
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2)) !important;
  color: #042018 !important;
  box-shadow: var(--pq-glow);
  border-radius: 14px;
  white-space: nowrap;
}
.smart-btn:hover {
  box-shadow: 0 0 30px rgba(20, 224, 160, 0.5);
  transform: translateY(-2px);
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
.search {
  width: 300px;
  max-width: 100%;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  min-height: 200px;
}
.card {
  padding: 22px;
  cursor: pointer;
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.s-icon {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 13px;
  color: var(--pq-primary);
  background: rgba(20, 224, 160, 0.1);
  border: 1px solid rgba(20, 224, 160, 0.2);
}
.s-name {
  font-size: 19px;
  font-weight: 700;
  margin: 0 0 8px;
}
.s-addr {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--pq-text-dim);
  font-size: 13px;
  margin: 0 0 18px;
  min-height: 20px;
}
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  padding: 16px 0;
  border-top: 1px solid var(--pq-border);
  border-bottom: 1px solid var(--pq-border);
}
.stat {
  text-align: center;
}
.stat .num {
  display: block;
  font-size: 24px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.stat .lbl {
  font-size: 12px;
  color: var(--pq-text-dim);
}
.stat.hot .num {
  color: var(--pq-primary);
  text-shadow: 0 0 18px rgba(20, 224, 160, 0.55);
}
.load-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  padding: 10px 14px;
  border-radius: var(--pq-radius-sm);
  background: var(--pq-surface-2);
  font-size: 12px;
}
.ldot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.ldot.green { background: var(--pq-idle); box-shadow: 0 0 8px var(--pq-idle); }
.ldot.yellow { background: var(--pq-warning); box-shadow: 0 0 8px var(--pq-warning); }
.ldot.red { background: var(--pq-danger); box-shadow: 0 0 8px var(--pq-danger); }
.lrate { font-weight: 700; color: var(--pq-text); }
.lcoeff { color: var(--pq-text-dim); }
.enter {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 16px;
  color: var(--pq-primary);
  font-weight: 600;
  font-size: 14px;
}
</style>
