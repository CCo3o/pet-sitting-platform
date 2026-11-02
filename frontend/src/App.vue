<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import PointerCompanion from './components/PointerCompanion.vue'
</script>
<template><ElConfigProvider :locale="zhCn"><RouterView /><PointerCompanion /></ElConfigProvider></template>
