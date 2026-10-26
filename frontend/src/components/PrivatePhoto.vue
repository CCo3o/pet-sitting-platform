<script setup lang="ts">
import { onBeforeUnmount,ref,watch } from 'vue'
import { useAuthStore } from '../stores/auth'
const props=defineProps<{id:string}>(),auth=useAuthStore(),url=ref(''),failed=ref(false)
let controller:AbortController|null=null
watch(()=>props.id,async id=>{controller?.abort();if(url.value)URL.revokeObjectURL(url.value);url.value='';failed.value=false;controller=new AbortController();const signal=controller.signal;try{const response=await fetch(`/api/photos/${id}`,{headers:{Authorization:`Bearer ${auth.token}`},signal});if(!response.ok)throw new Error();const blob=await response.blob();if(!signal.aborted)url.value=URL.createObjectURL(blob)}catch{if(!signal.aborted)failed.value=true}},{immediate:true})
onBeforeUnmount(()=>{controller?.abort();if(url.value)URL.revokeObjectURL(url.value)})
</script>
<template><img v-if="url" :src="url" alt="本次上门服务的喂养照片" class="record-photo"/><span v-else class="muted">{{ failed?'照片读取失败':'照片读取中…' }}</span></template>
