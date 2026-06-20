<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// ECharts 按需注册(仅引入用到的图表与组件)
echarts.use([LineChart, PieChart, BarChart, TooltipComponent, GridComponent, LegendComponent, CanvasRenderer])
import { dashboardApi } from '@/api/admin'

const board = ref(null)
const loading = ref(false)
const trendRef = ref()
const pileRef = ref()
const orderRef = ref()
let trendChart, pileChart, orderChart

const PILE_LABEL = { IDLE: '空闲', RESERVED: '已预约', CHARGING: '充电中', FAULT: '故障' }
const ORDER_LABEL = { PENDING: '待充电', CHARGING: '充电中', FINISHED: '已完成', CANCELLED: '已取消' }

const kpis = ref([])

const buildKpis = (d) => {
  const s = d.summary || {}
  kpis.value = [
    { label: '总营收', value: '¥' + (s.totalRevenue ?? 0), icon: 'Money', tone: 'green' },
    { label: '今日营收', value: '¥' + (s.todayRevenue ?? 0), icon: 'TrendCharts', tone: 'cyan' },
    { label: '总订单', value: s.totalOrders ?? 0, icon: 'Tickets', tone: 'violet' },
    { label: '今日订单', value: s.todayOrders ?? 0, icon: 'Calendar', tone: 'amber' },
  ]
}

const axisStyle = {
  axisLine: { lineStyle: { color: 'rgba(255,255,255,0.15)' } },
  axisLabel: { color: '#8a98ab' },
}

const renderTrend = (d) => {
  const trend = d.revenueTrend || []
  trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 24, top: 24, bottom: 36 },
    xAxis: { type: 'category', boundaryGap: false, data: trend.map((t) => t.date), ...axisStyle },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } }, axisLabel: { color: '#8a98ab' } },
    series: [
      {
        name: '营收(元)',
        type: 'line',
        smooth: true,
        data: trend.map((t) => t.revenue),
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 3, color: '#14e0a0' },
        itemStyle: { color: '#14e0a0' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(20,224,160,0.42)' },
            { offset: 1, color: 'rgba(20,224,160,0)' },
          ]),
        },
      },
    ],
  })
}

const renderPile = (d) => {
  const map = d.pileStatusCount || {}
  const data = Object.keys(map).map((k) => ({ name: PILE_LABEL[k] || k, value: map[k] }))
  pileChart = echarts.init(pileRef.value)
  pileChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#8a98ab' } },
    color: ['#14e0a0', '#ffb02e', '#2ad6ff', '#ff5d6c'],
    series: [
      {
        type: 'pie',
        radius: ['54%', '74%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#0d141f', borderWidth: 3 },
        label: { color: '#e8eef5' },
        data,
      },
    ],
  })
}

const renderOrder = (d) => {
  const map = d.reservationStatusCount || {}
  const keys = Object.keys(map)
  orderChart = echarts.init(orderRef.value)
  orderChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 50, right: 24, top: 24, bottom: 36 },
    xAxis: { type: 'category', data: keys.map((k) => ORDER_LABEL[k] || k), ...axisStyle },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } }, axisLabel: { color: '#8a98ab' } },
    series: [
      {
        type: 'bar',
        barWidth: '46%',
        data: keys.map((k) => map[k]),
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#2ad6ff' },
            { offset: 1, color: 'rgba(42,214,255,0.25)' },
          ]),
        },
      },
    ],
  })
}

const resize = () => {
  trendChart?.resize()
  pileChart?.resize()
  orderChart?.resize()
}

const load = async () => {
  loading.value = true
  try {
    const { data } = await dashboardApi()
    board.value = data
    buildKpis(data)
    await nextTick()
    renderTrend(data)
    renderPile(data)
    renderOrder(data)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('resize', resize)
})
onUnmounted(() => {
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  pileChart?.dispose()
  orderChart?.dispose()
})
</script>

<template>
  <div v-loading="loading">
    <!-- KPI -->
    <div class="kpis">
      <div v-for="(k, i) in kpis" :key="k.label" class="kpi pq-glass pq-fade-up" :class="k.tone" :style="{ animationDelay: i * 0.05 + 's' }">
        <div class="kpi-icon"><el-icon :size="22"><component :is="k.icon" /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-val pq-mono">{{ k.value }}</div>
          <div class="kpi-lbl">{{ k.label }}</div>
        </div>
      </div>
    </div>

    <!-- 图表行 1 -->
    <div class="charts">
      <div class="panel pq-glass pq-fade-up wide">
        <div class="p-title">近 7 日营收趋势</div>
        <div ref="trendRef" class="chart" />
      </div>
      <div class="panel pq-glass pq-fade-up">
        <div class="p-title">充电桩状态分布</div>
        <div ref="pileRef" class="chart" />
      </div>
    </div>

    <!-- 图表行 2 -->
    <div class="charts">
      <div class="panel pq-glass pq-fade-up wide">
        <div class="p-title">订单状态分布</div>
        <div ref="orderRef" class="chart" />
      </div>
      <div class="panel pq-glass pq-fade-up summary">
        <div class="p-title">资源概览</div>
        <div class="res">
          <div class="res-item"><el-icon><OfficeBuilding /></el-icon><div><b>{{ board?.stationCount ?? 0 }}</b><span>充电站</span></div></div>
          <div class="res-item"><el-icon><Cpu /></el-icon><div><b>{{ board?.pileCount ?? 0 }}</b><span>充电桩</span></div></div>
          <div class="res-item"><el-icon><UserFilled /></el-icon><div><b>{{ board?.userCount ?? 0 }}</b><span>注册用户</span></div></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  margin-bottom: 20px;
}
.kpi {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px;
}
.kpi-icon {
  width: 50px;
  height: 50px;
  display: grid;
  place-items: center;
  border-radius: 14px;
}
.kpi.green .kpi-icon { color: #14e0a0; background: rgba(20, 224, 160, 0.12); }
.kpi.cyan .kpi-icon { color: #2ad6ff; background: rgba(42, 214, 255, 0.12); }
.kpi.violet .kpi-icon { color: #7c6bff; background: rgba(124, 107, 255, 0.14); }
.kpi.amber .kpi-icon { color: #ffb02e; background: rgba(255, 176, 46, 0.14); }
.kpi-val {
  font-size: 26px;
  font-weight: 800;
}
.kpi-lbl {
  font-size: 13px;
  color: var(--pq-text-dim);
}
.charts {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 18px;
  margin-bottom: 18px;
}
.panel {
  padding: 20px 22px;
}
.p-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 12px;
}
.chart {
  height: 300px;
  width: 100%;
}
.summary .res {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 8px;
}
.res-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: var(--pq-radius);
  background: var(--pq-surface);
  border: 1px solid var(--pq-border);
}
.res-item .el-icon {
  font-size: 24px;
  color: var(--pq-primary);
}
.res-item b {
  font-size: 24px;
  font-weight: 800;
  display: block;
}
.res-item span {
  font-size: 12px;
  color: var(--pq-text-dim);
}
@media (max-width: 980px) {
  .kpis {
    grid-template-columns: repeat(2, 1fr);
  }
  .charts {
    grid-template-columns: 1fr;
  }
}
</style>
