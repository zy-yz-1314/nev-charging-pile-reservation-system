<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { recommendApi } from '@/api/recommend'
import { grabPileApi } from '@/api/reservation'
import { enqueueApi } from '@/api/queue'

const loading = ref(false)
const results = ref([])
const grabbingId = ref(null)
const queuingId = ref(null)

const form = reactive({
  place: '',
  lng: null,
  lat: null,
  carPowerKW: 60,
  batteryCapacity: null,
  targetEnergy: null,
  topN: 5,
  profile: 'default',
})

const locating = ref(false)
const resolvedAddr = ref('')

/** 地名 → 经纬度(OpenStreetMap Nominatim,免费无需Key,限1次/秒) */
const geocode = async () => {
  const q = form.place.trim()
  if (!q) { ElMessage.warning('请输入地名'); return }
  locating.value = true
  try {
    const resp = await fetch(
      `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=1`,
      { headers: { 'User-Agent': 'PowerQueue/1.0' } },
    )
    const arr = await resp.json()
    if (!arr.length) { ElMessage.warning('未找到该地名,请换个说法试试'); return }
    form.lat = Number(Number(arr[0].lat).toFixed(6))
    form.lng = Number(Number(arr[0].lon).toFixed(6))
    resolvedAddr.value = arr[0].display_name
    ElMessage.success(`已定位: ${resolvedAddr.value.substring(0, 40)}...`)
  } catch (_) { ElMessage.error('地理编码失败,请检查网络或使用定位按钮') }
  finally { locating.value = false }
}

/** 浏览器 GPS → 逆地理编码填地名 */
const locateGps = () => {
  if (!navigator.geolocation) { ElMessage.warning('浏览器不支持定位'); return }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    async (pos) => {
      form.lat = Number(pos.coords.latitude.toFixed(6))
      form.lng = Number(pos.coords.longitude.toFixed(6))
      // 逆地理编码:经纬度 → 地名
      try {
        const resp = await fetch(
          `https://nominatim.openstreetmap.org/reverse?lat=${form.lat}&lon=${form.lng}&format=json&zoom=16`,
          { headers: { 'User-Agent': 'PowerQueue/1.0' } },
        )
        const data = await resp.json()
        if (data.display_name) {
          form.place = data.display_name.substring(0, 50)
          resolvedAddr.value = data.display_name
        } else {
          resolvedAddr.value = `(${form.lat}, ${form.lng})`
        }
      } catch (_) {
        resolvedAddr.value = `(${form.lat}, ${form.lng})`
      }
      ElMessage.success('定位成功')
      locating.value = false
    },
    () => { ElMessage.error('定位失败,请输入地名'); locating.value = false },
    { enableHighAccuracy: true, timeout: 8000 },
  )
}

const recommend = async () => {
  if (!form.lng || !form.lat) { ElMessage.warning('请先输入地名并点击"查找位置",或点击"GPS定位"'); return }
  if (!form.carPowerKW || form.carPowerKW <= 0) { ElMessage.warning('请填写车型支持功率'); return }
  loading.value = true
  try {
    const { data } = await recommendApi({
      lng: form.lng, lat: form.lat,
      carPowerKW: form.carPowerKW,
      batteryCapacity: form.batteryCapacity || null,
      targetEnergy: form.targetEnergy || null,
      topN: form.topN,
      profile: form.profile,
    })
    results.value = data
    if (!data.length) ElMessage.info('附近暂无合适的充电桩')
  } catch (e) { /* 已全局提示 */ }
  finally { loading.value = false }
}

const grab = async (pile) => {
  grabbingId.value = pile.pileId
  try { await grabPileApi(pile.pileId); ElMessage.success('抢桩成功!') }
  catch (e) { if (e.message?.includes('等待队列')) ElMessage.warning(e.message) }
  finally { grabbingId.value = null }
}

const enqueue = async (pile) => {
  queuingId.value = pile.pileId
  try { const r = await enqueueApi(pile.pileId); ElMessage.success(`已加入排队,前面 ${r.data.aheadCount} 人,预计 ${r.data.estimateWaitMin} 分钟`) }
  catch (_) {}
  finally { queuingId.value = null }
}
</script>

<template>
  <div>
    <div class="head pq-fade-up">
      <h1 class="title">⚡ 智能<span class="pq-gradient-text">充电</span></h1>
      <p class="subtitle">说出你在哪,系统自动匹配最优充电桩,告别手动翻列表</p>
    </div>

    <!-- 参数表单 -->
    <section class="form-card pq-glass pq-fade-up">
      <div class="form-row">
        <div class="field">
          <label>车型支持功率 (kW)</label>
          <el-input-number v-model="form.carPowerKW" :min="1" :max="500" :step="10" />
        </div>
        <div class="field">
          <label>电池容量 (kWh,可选)</label>
          <el-input-number v-model="form.batteryCapacity" :min="0" :max="200" :step="5" placeholder="可不填" />
        </div>
        <div class="field">
          <label>目标充电电量 (度,可选)</label>
          <el-input-number v-model="form.targetEnergy" :min="0" :max="200" :step="1" placeholder="可不填" />
        </div>
      </div>
      <div class="form-row">
        <div class="field">
          <label>策略</label>
          <el-select v-model="form.profile" style="width: 160px">
            <el-option label="默认均衡" value="default" />
            <el-option label="着急赶时间" value="urgent" />
            <el-option label="省钱优先" value="economy" />
          </el-select>
        </div>
        <div class="field">
          <label>返回数量</label>
          <el-input-number v-model="form.topN" :min="1" :max="20" style="width: 130px" />
        </div>
        <div class="field loc-field">
          <label>我的位置</label>
          <div class="loc-inline">
            <el-input
              v-model="form.place"
              placeholder="例如:国贸、中关村、望京SOHO"
              clearable
              @keyup.enter="geocode"
              style="flex:1; min-width: 200px"
            />
            <el-button class="loc-btn" :loading="locating" @click="geocode">
              <el-icon><Search /></el-icon>&nbsp;查找位置
            </el-button>
            <el-button class="gps-btn" :loading="locating" @click="locateGps">
              <el-icon><Aim /></el-icon>&nbsp;定位
            </el-button>
          </div>
          <span v-if="resolvedAddr" class="resolved">{{ resolvedAddr }}</span>
        </div>
      </div>
      <el-button type="primary" size="large" class="go-btn" :loading="loading" @click="recommend">
        <el-icon><Cpu /></el-icon>&nbsp;⚡ 我要充电
      </el-button>
    </section>

    <!-- 结果排行 -->
    <section v-if="results.length" class="results">
      <h2 class="sec-title pq-fade-up">
        综合得分 Top{{ results.length }} &nbsp;
        <span class="pq-gradient-text">score = w1×(1/距离) + w2×(1/等待) + w3×(1/价格) + w4×功率匹配度</span>
      </h2>
      <div class="result-grid">
        <article
          v-for="(r, i) in results"
          :key="r.pileId"
          class="r-card pq-glass pq-fade-up"
          :style="{ animationDelay: i * 0.08 + 's' }"
        >
          <div class="rank-badge" :class="i === 0 ? 'first' : ''">{{ i + 1 }}</div>
          <div class="r-top">
            <span class="r-station">{{ r.stationName }}</span>
            <span class="r-type" :class="r.type === 'FAST' ? 'fast' : 'slow'">{{ r.type === 'FAST' ? '快充' : '慢充' }}</span>
            <span class="r-no">{{ r.pileNo }}</span>
          </div>
          <div class="r-factors">
            <span class="rf">📏 {{ Number(r.distanceKm).toFixed(1) }} km</span>
            <span class="rf">⏱ {{ r.waitMin }} min</span>
            <span class="rf">💰 ¥{{ Number(r.finalPrice).toFixed(2) }}</span>
            <span class="rf">🔌 {{ Number(r.powerMatch).toFixed(0) }}% 匹配</span>
          </div>
          <div class="bars">
            <div class="b-row"><span class="b-lbl">距离</span><div class="b-track"><div class="b-fill" :style="{ width: (Number(r.distanceScore) * 100).toFixed(0) + '%', background: 'var(--pq-primary)' }" /></div></div>
            <div class="b-row"><span class="b-lbl">等待</span><div class="b-track"><div class="b-fill" :style="{ width: (Number(r.waitScore) * 100).toFixed(0) + '%', background: 'var(--pq-charging)' }" /></div></div>
            <div class="b-row"><span class="b-lbl">价格</span><div class="b-track"><div class="b-fill" :style="{ width: (Number(r.priceScore) * 100).toFixed(0) + '%', background: 'var(--pq-accent)' }" /></div></div>
            <div class="b-row"><span class="b-lbl">功率</span><div class="b-track"><div class="b-fill" :style="{ width: (Number(r.powerScore) * 100).toFixed(0) + '%', background: 'var(--pq-warning)' }" /></div></div>
          </div>
          <div class="r-score">综合得分 <b>{{ Number(r.score).toFixed(0) }}</b> / 100</div>
          <div class="r-acts">
            <el-button type="primary" size="small" :loading="grabbingId === r.pileId" @click="grab(r)">⚡ 抢约</el-button>
            <el-button size="small" :loading="queuingId === r.pileId" @click="enqueue(r)">📋 加入排队</el-button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.head { margin-bottom: 24px; }
.title { font-size: 32px; font-weight: 800; margin: 0 0 6px; }
.subtitle { color: var(--pq-text-dim); margin: 0; }

.form-card { padding: 28px; margin-bottom: 32px; }
.form-row { display: flex; gap: 20px; margin-bottom: 18px; flex-wrap: wrap; }
.field { display: flex; flex-direction: column; gap: 5px; min-width: 140px; }
.field label { font-size: 12px; font-weight: 600; color: var(--pq-text-dim); text-transform: uppercase; letter-spacing: 0.5px; }
.loc-field { flex: 1; min-width: 360px; }
.loc-inline { display: flex; gap: 8px; align-items: center; }
.loc-btn { font-weight: 600; }
.gps-btn { font-weight: 600; color: var(--pq-text-dim); }
.resolved { display: block; margin-top: 4px; font-size: 11px; color: var(--pq-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 420px; }
.go-btn {
  font-weight: 800; font-size: 17px; padding: 16px 36px;
  border: none; background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2)) !important;
  color: #042018 !important; box-shadow: var(--pq-glow); border-radius: 14px;
  width: 100%; margin-top: 6px;
}
.sec-title { font-size: 14px; font-weight: 600; color: var(--pq-text-dim); margin: 0 0 16px; }

.result-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 16px; }
.r-card { position: relative; padding: 22px 22px 18px 52px; overflow: hidden; }
.rank-badge {
  position: absolute; left: 10px; top: 18px;
  width: 30px; height: 30px; border-radius: 50%;
  display: grid; place-items: center;
  background: var(--pq-surface-2); color: var(--pq-text-dim);
  font-size: 14px; font-weight: 800;
}
.rank-badge.first { background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2)); color: #042018; box-shadow: var(--pq-glow); }
.r-top { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
.r-station { font-size: 16px; font-weight: 700; }
.r-type { font-size: 10px; padding: 2px 8px; border-radius: 999px; font-weight: 700; }
.r-type.fast { color: var(--pq-charging); background: rgba(42,214,255,0.14); }
.r-type.slow { color: var(--pq-text-dim); background: var(--pq-surface-2); }
.r-no { font-size: 13px; color: var(--pq-primary); font-weight: 700; font-variant-numeric: tabular-nums; }

.r-factors { display: flex; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.rf { font-size: 12px; font-weight: 600; color: var(--pq-text-dim); }

.bars { margin-bottom: 14px; }
.b-row { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; }
.b-lbl { width: 32px; font-size: 11px; color: var(--pq-text-dim); text-align: right; flex-shrink: 0; }
.b-track { flex: 1; height: 6px; border-radius: 3px; background: var(--pq-surface-2); overflow: hidden; }
.b-fill { height: 100%; border-radius: 3px; transition: width 0.6s var(--pq-ease); }

.r-score { font-size: 14px; margin-bottom: 12px; color: var(--pq-text-dim); }
.r-score b { font-size: 22px; color: var(--pq-primary); margin-left: 4px; }
.r-acts { display: flex; gap: 8px; }
</style>
