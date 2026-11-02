<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const companion = ref<HTMLElement | null>(null)
const enabled = ref(false)
const visible = ref(false)
let pointerMedia: MediaQueryList
let motionMedia: MediaQueryList
let frame = 0
let x = 0, y = 0, targetX = 0, targetY = 0

function stop() {
  visible.value = false
  if (frame) cancelAnimationFrame(frame)
  frame = 0
}

function updateAvailability() {
  enabled.value = pointerMedia.matches && !motionMedia.matches
  if (!enabled.value) stop()
}

function animate() {
  x += (targetX - x) * .22
  y += (targetY - y) * .22
  if (companion.value) companion.value.style.transform = `translate3d(${x}px, ${y}px, 0)`
  frame = Math.abs(targetX - x) + Math.abs(targetY - y) > .5
    ? requestAnimationFrame(animate) : 0
}

function move(event: PointerEvent) {
  if (!enabled.value || event.pointerType !== 'mouse') return
  targetX = event.clientX + 18
  targetY = event.clientY + 20
  if (!visible.value) {
    x = targetX; y = targetY
    visible.value = true
  }
  if (!frame) frame = requestAnimationFrame(animate)

  const panel = (event.target as Element)?.closest?.('.story-panel, .page-heading') as HTMLElement | null
  if (panel) {
    const bounds = panel.getBoundingClientRect()
    panel.style.setProperty('--pointer-x', `${event.clientX - bounds.left}px`)
    panel.style.setProperty('--pointer-y', `${event.clientY - bounds.top}px`)
    if (panel.classList.contains('story-panel')) {
      panel.style.setProperty('--pet-shift-x', `${((event.clientX - bounds.left) / bounds.width - .5) * 14}px`)
      panel.style.setProperty('--pet-shift-y', `${((event.clientY - bounds.top) / bounds.height - .5) * 10}px`)
    }
  }
}

onMounted(() => {
  pointerMedia = window.matchMedia('(hover: hover) and (pointer: fine)')
  motionMedia = window.matchMedia('(prefers-reduced-motion: reduce)')
  updateAvailability()
  pointerMedia.addEventListener('change', updateAvailability)
  motionMedia.addEventListener('change', updateAvailability)
  window.addEventListener('pointermove', move, { passive: true })
  window.addEventListener('blur', stop)
  document.addEventListener('mouseleave', stop)
})

onUnmounted(() => {
  pointerMedia?.removeEventListener('change', updateAvailability)
  motionMedia?.removeEventListener('change', updateAvailability)
  window.removeEventListener('pointermove', move)
  window.removeEventListener('blur', stop)
  document.removeEventListener('mouseleave', stop)
  stop()
})
</script>

<template>
  <div ref="companion" class="pointer-companion" :class="{ visible: enabled && visible }" aria-hidden="true">🐾</div>
</template>
