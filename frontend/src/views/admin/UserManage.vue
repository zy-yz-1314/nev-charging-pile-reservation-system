<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminUserPageApi,
  adminUserAddApi,
  adminUserUpdateApi,
  adminUserStatusApi,
  adminUserResetPwdApi,
  adminUserDeleteApi,
} from '@/api/admin'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })

const dialogVisible = ref(false)
const editId = ref(null)
const formRef = ref()
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  role: 'USER',
  phone: '',
  carPlate: '',
  status: 1,
})
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
}

const load = async () => {
  loading.value = true
  try {
    const { data } = await adminUserPageApi(query)
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
  Object.assign(form, { username: '', password: '', nickname: '', role: 'USER', phone: '', carPlate: '', status: 1 })
  dialogVisible.value = true
}
const openEdit = (row) => {
  editId.value = row.id
  Object.assign(form, { ...row, password: '' })
  dialogVisible.value = true
}
const submit = async () => {
  await formRef.value.validate()
  if (editId.value) {
    await adminUserUpdateApi(editId.value, form)
  } else {
    await adminUserAddApi(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
const toggleStatus = async (row) => {
  const next = row.status === 1 ? 0 : 1
  await adminUserStatusApi(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  load()
}
const resetPwd = async (row) => {
  try {
    await ElMessageBox.confirm(`将「${row.username}」密码重置为系统默认密码?`, '提示', { type: 'warning' })
  } catch (_) {
    return
  }
  await adminUserResetPwdApi(row.id)
  ElMessage.success('密码已重置为系统默认密码')
}
const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.username}」?`, '警告', { type: 'warning' })
  } catch (_) {
    return
  }
  await adminUserDeleteApi(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="pq-page-head">
      <h2>用户管理</h2>
      <div class="pq-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索用户名 / 昵称"
          :prefix-icon="'Search'"
          clearable
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button type="primary" :icon="'Plus'" @click="openAdd">新增用户</el-button>
      </div>
    </div>

    <div class="pq-card pq-glass">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="nickname" label="昵称" width="130" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'success'" effect="dark" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '车主' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="carPlate" label="车牌" width="120" />
        <el-table-column label="余额" width="100">
          <template #default="{ row }">¥{{ row.balance }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text size="small" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button text type="warning" size="small" @click="resetPwd(row)">重置密码</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑用户' : '新增用户'" width="500px" align-center>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!editId" placeholder="登录账号" />
        </el-form-item>
        <el-form-item v-if="!editId" label="密码">
          <el-input v-model="form.password" placeholder="留空则使用系统默认密码" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio-button value="USER">车主</el-radio-button>
            <el-radio-button value="ADMIN">管理员</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="车牌">
          <el-input v-model="form.carPlate" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
