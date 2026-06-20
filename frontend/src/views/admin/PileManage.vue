<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminPilePageApi,
  adminPileAddApi,
  adminPileUpdateApi,
  adminPileDeleteApi,
} from '@/api/admin'
import { listStationsApi } from '@/api/station'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stations = ref([])
const query = reactive({ current: 1, size: 10, stationId: null, keyword: '' })

const STATUS_TAG = { IDLE: 'success', RESERVED: 'warning', CHARGING: 'primary', FAULT: 'danger' }
const STATUS_LABEL = { IDLE: '空闲', RESERVED: '已预约', CHARGING: '充电中', FAULT: '故障' }
const STATUS_OPTIONS = ['IDLE', 'RESERVED', 'CHARGING', 'FAULT']

const dialogVisible = ref(false)
const editId = ref(null)
const formRef = ref()
const form = reactive({
  stationId: null,
  pileNo: '',
  type: 'FAST',
  power: 120,
  price: 1.8,
  status: 'IDLE',
})
const rules = {
  stationId: [{ required: true, message: '请选择站点', trigger: 'change' }],
  pileNo: [{ required: true, message: '请输入桩编号', trigger: 'blur' }],
}

const stationName = (id) => stations.value.find((s) => s.id === id)?.name || id

const loadStations = async () => {
  const { data } = await listStationsApi('')
  stations.value = data
}
const load = async () => {
  loading.value = true
  try {
    const { data } = await adminPilePageApi(query)
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

const openAdd = () => {
  editId.value = null
  Object.assign(form, { stationId: query.stationId, pileNo: '', type: 'FAST', power: 120, price: 1.8, status: 'IDLE' })
  dialogVisible.value = true
}
const openEdit = (row) => {
  editId.value = row.id
  Object.assign(form, row)
  dialogVisible.value = true
}
const submit = async () => {
  await formRef.value.validate()
  if (editId.value) {
    await adminPileUpdateApi(editId.value, form)
  } else {
    await adminPileAddApi(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除充电桩「${row.pileNo}」?`, '警告', { type: 'warning' })
  } catch (_) {
    return
  }
  await adminPileDeleteApi(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  await loadStations()
  await load()
})
</script>

<template>
  <div>
    <div class="pq-page-head">
      <h2>充电桩管理</h2>
      <div class="pq-toolbar">
        <el-select v-model="query.stationId" placeholder="全部站点" clearable style="width: 180px" @change="search">
          <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索桩编号"
          :prefix-icon="'Search'"
          clearable
          style="width: 200px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" :icon="'Plus'" @click="openAdd">新增充电桩</el-button>
      </div>
    </div>

    <div class="pq-card pq-glass">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="pileNo" label="桩编号" width="120" />
        <el-table-column label="所属站点" min-width="160">
          <template #default="{ row }">{{ stationName(row.stationId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 'FAST' ? 'primary' : 'info'" effect="plain" size="small">
              {{ row.type === 'FAST' ? '快充' : '慢充' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="功率" width="100">
          <template #default="{ row }">{{ row.power }} kW</template>
        </el-table-column>
        <el-table-column label="单价" width="100">
          <template #default="{ row }">¥{{ row.price }}/度</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.status]" effect="dark" round size="small">
              {{ STATUS_LABEL[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
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

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑充电桩' : '新增充电桩'" width="520px" align-center>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="所属站点" prop="stationId">
          <el-select v-model="form.stationId" placeholder="选择站点" style="width: 100%">
            <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="桩编号" prop="pileNo">
          <el-input v-model="form.pileNo" placeholder="如 A-01" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio-button value="FAST">快充</el-radio-button>
            <el-radio-button value="SLOW">慢充</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="功率(kW)">
          <el-input-number v-model="form.power" :min="0" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单价(元/度)">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="STATUS_LABEL[s]" :value="s" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
