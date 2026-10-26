<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Lock, User as UserIcon } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PetScene from '../components/PetScene.vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute(), router = useRouter(), auth = useAuthStore()
const demo = import.meta.env.VITE_APP_MODE !== 'production'
const registering = computed(() => route.path === '/register')
const formRef = ref<FormInstance>()
const busy = ref(false), error = ref('')
const form = reactive({ username: '', password: '', displayName: '', role: 'OWNER' as 'OWNER' | 'SITTER' })
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{3,32}$/, message: '使用 3–32 位英文字母、数字或下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度为 8–64 个字符', trigger: 'blur' },
    { validator: (_rule, value: string, callback) => new TextEncoder().encode(value).length > 72 ? callback(new Error('密码最多占用 72 个 UTF-8 字节')) : callback(), trigger: 'blur' },
  ],
  displayName: [
    { required: true, whitespace: true, message: '请输入昵称', trigger: 'blur' },
    { max: 40, message: '昵称最多 40 个字符', trigger: 'blur' },
  ],
}
watch(registering, () => { error.value = ''; form.password = ''; formRef.value?.clearValidate() })
async function submit() {
  if (busy.value || !(await formRef.value?.validate().catch(() => false))) return
  busy.value = true; error.value = ''
  try {
    if (registering.value) await auth.register(form)
    else await auth.login(form.username, form.password)
    form.password = ''
    await router.replace('/workspace')
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '操作失败，请重试' }
  finally { busy.value = false }
}
function fillDemo(username: string) {
  form.username = username; form.password = 'PetDemo123!'; error.value = ''; formRef.value?.clearValidate()
}
</script>

<template>
  <main class="auth-shell">
    <section class="story-panel">
      <RouterLink to="/login" class="brand" aria-label="宠伴到家首页">
        <span class="brand-symbol">✣</span><span>宠伴到家<small>PET CARE, AT HOME</small></span>
      </RouterLink>
      <div class="story-copy">
        <span class="eyebrow"><span class="small-dot"></span> 熟悉的家，安心的陪伴</span>
        <h1>你安心出门，<br />我们用心照顾。</h1>
        <p>让每一餐、每一次陪伴都有着落。<br />为你的毛孩子，找到细心的上门喂养员。</p>
      </div>
      <PetScene />
      <div class="story-benefits"><span>预约上门</span><i></i><span>喂养记录</span><i></i><span>贴心陪伴</span></div>
      <p class="story-footer">每一个小生命，都值得被温柔照顾。</p>
    </section>

    <section class="form-panel">
      <div class="form-top"><span>宠物上门喂养服务平台</span><span v-if="demo" class="version-badge">本地开发版</span></div>
      <div class="auth-card" :class="{ 'register-card': registering }">
        <nav class="auth-tabs" aria-label="账户操作">
          <RouterLink to="/login" :class="{ active: !registering }">登录账号</RouterLink>
          <RouterLink to="/register" :class="{ active: registering }">创建账号</RouterLink>
        </nav>
        <div class="auth-heading">
          <span class="eyebrow">{{ registering ? 'NICE TO MEET YOU' : 'WELCOME BACK' }}</span>
          <h2>{{ registering ? '从一次安心的照顾开始' : '很高兴，再次见到你' }}</h2>
          <p>{{ registering ? '选择你的身份，加入宠伴到家。' : '登录后，继续照顾你牵挂的小伙伴。' }}</p>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="submit">
          <fieldset :disabled="busy" class="form-fields">
            <div v-if="registering" class="role-picker" role="group" aria-label="注册身份">
              <button type="button" :class="{ selected: form.role === 'OWNER' }" @click="form.role = 'OWNER'"><span>宠物主人</span><small>为毛孩子预约照顾</small></button>
              <button type="button" :class="{ selected: form.role === 'SITTER' }" @click="form.role = 'SITTER'"><span>喂养员</span><small>提供细心的上门服务</small></button>
            </div>
            <el-form-item v-if="registering" label="昵称" prop="displayName">
              <el-input v-model="form.displayName" name="displayName" placeholder="大家怎么称呼你？" maxlength="40" autocomplete="nickname" />
            </el-form-item>
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" name="username" placeholder="请输入用户名" autocomplete="username" maxlength="32" :prefix-icon="UserIcon" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="form.password" name="password" type="password" placeholder="请输入至少 8 位密码" :autocomplete="registering ? 'new-password' : 'current-password'" maxlength="64" show-password :prefix-icon="Lock" />
            </el-form-item>
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <el-button class="submit-button" type="primary" native-type="submit" :loading="busy">
              {{ registering ? '创建账号并进入' : '登录并进入' }}<el-icon v-if="!busy"><ArrowRight /></el-icon>
            </el-button>
          </fieldset>
        </el-form>
        <p class="account-hint">{{ registering ? '已经有账号？' : '第一次来这里？' }} <RouterLink :to="registering ? '/login' : '/register'">{{ registering ? '去登录' : '创建你的账号' }}</RouterLink></p>
        <div v-if="!registering && demo" class="demo-panel">
          <div class="demo-title"><span>先体验一下</span><small>点击填入演示账号</small></div>
          <div class="demo-buttons">
            <button type="button" :disabled="busy" @click="fillDemo('owner')">宠物主人 <span>↗</span></button>
            <button type="button" :disabled="busy" @click="fillDemo('sitter')">喂养员 <span>↗</span></button>
            <button type="button" :disabled="busy" @click="fillDemo('admin')">管理员 <span>↗</span></button>
          </div>
          <p>演示账号仅供本地开发体验。</p>
        </div>
        <p v-else-if="registering" class="registration-note">管理员账号由平台配置，不开放自行注册。</p>
      </div>
      <footer class="form-footer">{{ demo ? '基于 Spring Boot 和 Vue3 的宠物上门喂养服务平台' : '宠伴到家 · 用心照顾每一次牵挂' }}</footer>
    </section>
  </main>
</template>
