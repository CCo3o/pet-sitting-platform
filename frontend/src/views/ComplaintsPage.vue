<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import PlatformLayout from '../components/PlatformLayout.vue'
import { useApi,date,type Row } from '../composables/useApi'
const {api,act,auth}=useApi(),rows=ref<Row[]>([]),error=ref(''),busy=ref(false)
async function load(){error.value='';try{rows.value=await api<Row[]>('/complaints')}catch(e){error.value=(e as Error).message}}
async function resolve(row:Row){try{const result=await ElMessageBox.prompt('填写处理结果，提交后用户可查看。','处理投诉',{inputType:'textarea',inputPattern:/\S/,inputErrorMessage:'请输入处理说明'});busy.value=true;if(await act(()=>api(`/admin/complaints/${row.ID}/resolve`,'POST',{note:result.value}),'处理结果已保存'))await load()}catch{/* 取消 */}finally{busy.value=false}}
onMounted(load)
</script>
<template><PlatformLayout><div class="page-heading"><div><span class="eyebrow">WE ARE HERE TO HELP</span><h1>投诉与反馈</h1><p>在订单详情中提交问题，查看管理员的处理结果。</p></div><el-button @click="load">刷新</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false"/><section class="business-card"><el-empty v-if="!rows.length" description="暂无投诉或反馈"/><article v-for="row in rows" :key="row.ID" class="record-card"><div class="section-title"><h3>订单 {{ String(row.ORDER_ID).slice(0,8) }}</h3><el-tag>{{ row.STATUS==='OPEN'?'待处理':'已处理' }}</el-tag></div><p>{{ row.CONTENT }}</p><p class="muted">{{ date(row.CREATED_AT) }}</p><p v-if="row.RESOLUTION" class="resolution">处理结果：{{ row.RESOLUTION }}</p><el-button v-if="auth.user?.role==='ADMIN'&&row.STATUS==='OPEN'" type="primary" :loading="busy" @click="resolve(row)">填写处理结果</el-button></article></section></PlatformLayout></template>
