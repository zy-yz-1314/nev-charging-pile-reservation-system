<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminStationPageApi,
  adminStationAddApi,
  adminStationUpdateApi,
  adminStationDeleteApi,
} from '@/api/admin'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })

const dialogVisible = ref(false)
const editId = ref(null)
const formRef = ref()
const form = reactive({
  name: '',
  address: '',
  longitude: null,
  latitude: null,
  description: '',
  status: 1,
})
const rules = {
  name: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
  address: [{ required: true, message: '请输入地址', trigger: 'blur' }],
}

const load = async () => {
  loading.value = true
  try {
    const { data } = await adminStationPageApi(query)
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
  Object.assign(form, { name: '', address: '', longitude: null, latitude: null, description: '', status: 1 })
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
    await adminStationUpdateApi(editId.value, form)
  } else {
    await adminStationAddApi(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除站点「${row.name}」?`, '警告', { type: 'warning' })
  } catch (_) {
    return
  }
  await adminStationDeleteApi(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="pq-page-head">
      <h2>充电站管理</h2>
      <div class="pq-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索站点名称"
          :prefix-icon="'Search'"
          clearable
          style="width: 240px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" :icon="'Plus'" @click="openAdd">新增站点</el-button>
      </div>
    </div>

    <div class="pq-card pq-glass">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="站点名称" min-width="160" />
        <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" round size="small">
              {{ row.status === 1 ? '营业' : '停业' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
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

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑站点' : '新增站点'" width="520px" align-center>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="充电站名称" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" placeholder="详细地址" />
        </el-form-item>
        <el-form-item label="经度">
          <el-input-number v-model="form.longitude" :precision="6" :step="0.0001" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="纬度">
          <el-input-number v-model="form.latitude" :precision="6" :step="0.0001" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="站点描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="营业" inactive-text="停业" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
