<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminReservationPageApi } from '@/api/admin'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, status: '', keyword: '' })

const TAG = { PENDING: 'warning', CHARGING: 'primary', FINISHED: 'success', CANCELLED: 'info' }
const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待充电' },
  { value: 'CHARGING', label: '充电中' },
  { value: 'FINISHED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

const load = async () => {
  loading.value = true
  try {
    const { data } = await adminReservationPageApi(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.current = 1
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="pq-page-head">
      <h2>订单管理</h2>
      <div class="pq-toolbar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px" @change="search">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索订单号 / 用户"
          :prefix-icon="'Search'"
          clearable
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        />
      </div>
    </div>

    <div class="pq-card pq-glass">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="userName" label="用户" width="120" />
        <el-table-column prop="stationName" label="充电站" min-width="150" show-overflow-tooltip />
        <el-table-column prop="pileNo" label="桩号" width="100" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.pileType === 'FAST' ? '快充' : '慢充' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="TAG[row.status]" effect="dark" round size="small">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reserveTime" label="预约时间" width="180" />
      </el-table>

      <div class="pq-pager">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>
  </div>
</template>
