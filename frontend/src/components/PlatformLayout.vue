<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { roleNames } from '../types'
const auth=useAuthStore(), router=useRouter(), leaving=ref(false)
const links=computed(()=>{
  const common=[['/workspace','智能工作台'],['/orders','服务订单'],['/complaints','投诉与反馈']]
  if(auth.user?.role==='OWNER')return [common[0],['/pets','宠物档案'],['/addresses','上门地址'],['/book','预约服务'],['/agent','预约助手'],...common.slice(1)]
  if(auth.user?.role==='SITTER')return [common[0],['/sitter','服务资料与时段'],...common.slice(1)]
  return [common[0],['/admin','平台管理'],...common.slice(1)]
})
async function logout(){leaving.value=true;try{await auth.logout();await router.replace('/login')}catch(e){ElMessage.error((e as Error).message)}finally{leaving.value=false}}
</script>
<template>
  <div class="workspace-shell"><header class="workspace-header"><RouterLink to="/workspace" class="brand"><span class="brand-symbol">✣</span><span>宠伴到家<small>PET CARE, AT HOME</small></span></RouterLink><div class="header-account"><span class="role-badge">{{ auth.user && roleNames[auth.user.role] }}</span><span>{{ auth.user?.displayName }}</span><el-button text :loading="leaving" @click="logout">退出登录</el-button></div></header>
  <nav class="platform-nav" aria-label="业务导航"><RouterLink v-for="link in links" :key="link[0]" :to="link[0] ?? '/workspace'">{{ link[1] }}</RouterLink></nav>
  <main class="workspace-main"><slot /></main></div>
</template>
