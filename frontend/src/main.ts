import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElAlert, ElButton, ElEmpty, ElForm, ElFormItem, ElIcon, ElInput, ElLoading, ElTable, ElTableColumn, ElTag } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import './style.css'

const app = createApp(App).use(createPinia()).use(router)
const components = { ElAlert, ElButton, ElEmpty, ElForm, ElFormItem, ElIcon, ElInput, ElTable, ElTableColumn, ElTag }
for (const [name, component] of Object.entries(components)) app.component(name, component)
app.directive('loading', ElLoading.directive).mount('#app')
