import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { request, ApiError } from '../api'
import { useAuthStore } from '../stores/auth'
export type Row=Record<string, any>
export function useApi(){
  const auth=useAuthStore(),router=useRouter()
  async function api<T=Row>(path:string,method='GET',body?:unknown,headers?:Record<string,string>):Promise<T>{
    try{return await request<T>(path,{method,body:body===undefined?undefined:JSON.stringify(body),headers},auth.token)}
    catch(e){if(e instanceof ApiError&&e.status===401){auth.clear();await router.replace('/login')}throw e}
  }
  async function act(task:()=>Promise<unknown>,message?:string){try{await task();if(message)ElMessage.success(message);return true}catch(e){ElMessage.error((e as Error).message);return false}}
  return {api,act,auth}
}
export const money=(cents:unknown)=>`¥${(Number(cents)/100).toFixed(2)}`
export const date=(value:unknown)=>new Date(typeof value==='number'?value:String(value)).toLocaleString('zh-CN',{hour12:false})
export const states:Record<string,string>={PENDING:'待接单',ACCEPTED:'已接单',IN_PROGRESS:'服务中',WAITING_CONFIRM:'待主人确认',COMPLETED:'已完成',CANCELLED:'已取消',REJECTED:'已拒单',EXPIRED:'接单超时'}
