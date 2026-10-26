<script setup lang="ts">
import { ref } from 'vue'
import { useApi, type Row } from '../composables/useApi'
type ToolTrace = { id:string,sequence:number,toolName:string,argumentsJson:string,resultJson:string,outcome:string,errorCode:string,durationMs:number }
const props=defineProps<{ runId:string }>()
const {api}=useApi(),opened=ref(false),busy=ref(false),error=ref(''),run=ref<Row|null>(null),tools=ref<ToolTrace[]>([])
async function toggle(){opened.value=!opened.value;if(!opened.value)return;busy.value=true;error.value='';try{run.value=await api(`/agent/runs/${props.runId}`);tools.value=run.value!.TOOLS}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
function pretty(value:string){try{return JSON.stringify(JSON.parse(value),null,2)}catch{return value}}
function duration(value:number){return value===0?'<1 ms':value>=1000?`${(value/1000).toFixed(2)} 秒`:`${value} ms`}
</script>
<template>
  <div class="run-trace">
    <el-button text @click="toggle" :loading="busy">{{ opened?'收起调用详情':'查看调用详情' }}</el-button>
    <div v-if="opened" class="trace-content">
      <el-alert v-if="error" :title="error" type="error" :closable="false"/>
      <template v-if="run"><p>调用编号：{{ runId }} · {{ run.OUTCOME }}</p>
      <p><strong>整次助手请求耗时：{{ duration(Number(run.DURATION_MS)) }}</strong></p>
      <p class="muted">整次耗时包含模型响应、网络等待和工具执行；下方显示每个本地工具的执行时间，不代表模型思考时间。低于 1 毫秒的工具显示为 &lt;1 ms。</p>
      <p v-if="run.ERROR_CODE">失败类型：{{ run.ERROR_CODE }}</p>
      <p v-if="!tools.length">本次没有执行业务工具。</p>
      <article v-for="tool in tools" :key="tool.id" class="trace-step">
        <strong>{{ tool.sequence }}. {{ tool.toolName }}</strong>
        <span> · {{ tool.outcome }} · 工具执行：{{ duration(tool.durationMs) }}</span>
        <p v-if="tool.errorCode">失败类型：{{ tool.errorCode }}</p>
        <details><summary>查询参数</summary><pre>{{ pretty(tool.argumentsJson) }}</pre></details>
        <details><summary>结果摘要</summary><pre>{{ pretty(tool.resultJson) }}</pre></details>
      </article><p class="muted">结果摘要保留 ID、报价和时间，省略联系方式、具体位置与宠物备注。</p></template>
    </div>
  </div>
</template>
<style scoped>
.run-trace{margin-top:12px}.trace-content{border:1px solid #dbe3d7;border-radius:12px;padding:16px;overflow-wrap:anywhere}.trace-step{padding:12px 0;border-top:1px solid #dbe3d7}.trace-step pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px}.trace-step details{margin-top:8px}.trace-content p{font-size:13px}
</style>
