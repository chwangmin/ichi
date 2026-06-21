<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useWeather, type WeatherCategory } from '@/composables/useWeather'

const { weather } = useWeather()

const category = computed<WeatherCategory>(() => weather.value?.category ?? 'clear')
const intensity = computed(() => weather.value?.intensity ?? 0)
const isDay = computed(() => weather.value?.isDay ?? true)

// 모션 최소화 사용자: 정적 그라데이션만 (입자 애니메이션 끔)
const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

// 카테고리+낮밤 → 배경 그라데이션 클래스. 흐림(강한 구름)이면 overcast 추가.
const skyClass = computed(() => {
  const overcast = category.value === 'clouds' && intensity.value >= 0.6 ? ' overcast' : ''
  return `sky-${category.value} ${isDay.value ? 'day' : 'night'}${overcast}`
})

// 맑은 밤 별 (상단 위주로 흩뿌리기). 한 번만 계산.
const stars = Array.from({ length: 38 }, () => ({
  x: Math.random() * 100, // vw %
  y: Math.random() * 55, // 위쪽 55% 안에서
  r: 0.6 + Math.random() * 1.4,
  delay: (Math.random() * 4).toFixed(2),
  dur: (2.4 + Math.random() * 2.6).toFixed(2),
}))

// ── 몽글 흰 구름: 위치·크기·속도를 랜덤 생성 (별처럼) ───────────────
// 개수는 날씨 강도로 정한다: 맑음=2, 구름 조금≈3~4, 흐림(≥0.6)≈6~7.
// 구름은 화면 왼쪽 밖(left<0)에서 시작해 drift 로 오른쪽으로 흘러가며,
// 음수 delay 로 로드 시점에 이미 중간쯤 떠 있게 보이게 한다.
interface Cloud {
  top: number // vh %
  width: number // px
  opacity: number
  dur: number // drift 시간(s)
  delay: number // 음수 delay(s)
}

function makeClouds(count: number, overcast: boolean): Cloud[] {
  return Array.from({ length: count }, () => {
    // 흐림이면 더 크게(서로 겹치게), 상단(0~30%)에 몰아 빽빽하게 깐다.
    const width = overcast ? 180 + Math.random() * 220 : 110 + Math.random() * 170 // 흐림 180~400px / 평소 110~280px
    const dur = 70 + Math.random() * 60 // 70~130s (클수록 느리게도 가능)
    return {
      top: overcast ? Math.random() * 30 : 6 + Math.random() * 46, // 흐림은 위쪽 0~30%, 평소 6~52%
      width,
      opacity: overcast ? 0.78 + Math.random() * 0.2 : 0.62 + Math.random() * 0.33, // 흐림 0.78~0.98 / 평소 0.62~0.95
      dur,
      delay: -(Math.random() * dur), // 한 바퀴 안의 임의 지점에서 시작
    }
  })
}

// 흐림(짙은 구름) 여부: clouds + 강도 0.6 이상
const isOvercast = computed(() => category.value === 'clouds' && intensity.value >= 0.6)

// 강도 → 구름 개수
function cloudCount(): number {
  if (category.value !== 'clear' && category.value !== 'clouds') return 0
  if (category.value === 'clear') return 2 // 맑아도 옅은 구름 2개
  // 흐림이면 빽빽하게 12~14개, 그 외 clouds 는 강도(0.4=조금)에 비례해 3~7개
  if (isOvercast.value) return Math.round(12 + intensity.value * 3)
  return Math.round(3 + intensity.value * 5)
}

const clouds = ref<Cloud[]>(makeClouds(cloudCount(), isOvercast.value))

// 날씨(카테고리/강도)가 바뀌면 구름을 새 위치·개수로 다시 흩뿌린다.
watch([category, intensity], () => {
  clouds.value = makeClouds(cloudCount(), isOvercast.value)
})

// 번개 플래시 (thunder)
const flash = ref(false)
let flashTimer: ReturnType<typeof setTimeout> | null = null
function scheduleFlash() {
  if (reduceMotion || category.value !== 'thunder') return
  const wait = 2500 + Math.random() * 4500
  flashTimer = setTimeout(() => {
    flash.value = true
    setTimeout(() => (flash.value = false), 180)
    // 가끔 이중 섬광
    if (Math.random() < 0.5) {
      setTimeout(() => {
        flash.value = true
        setTimeout(() => (flash.value = false), 120)
      }, 260)
    }
    scheduleFlash()
  }, wait)
}

// ── 캔버스 입자 (비/눈) ──────────────────────────────────────────
const canvas = ref<HTMLCanvasElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let raf = 0
let w = 0
let h = 0
let dpr = 1

interface Particle {
  x: number
  y: number
  len: number // 빗줄기 길이 (눈은 반지름으로 사용)
  speed: number
  drift: number // 가로 흔들림(눈)
  phase: number
}
let particles: Particle[] = []

function resize() {
  if (!canvas.value) return
  dpr = Math.min(window.devicePixelRatio || 1, 2)
  w = window.innerWidth
  h = window.innerHeight
  canvas.value.width = w * dpr
  canvas.value.height = h * dpr
  canvas.value.style.width = w + 'px'
  canvas.value.style.height = h + 'px'
  ctx = canvas.value.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)
  buildParticles()
}

function particleCount(): number {
  const cat = category.value
  if (cat !== 'rain' && cat !== 'snow') return 0
  const area = w * h
  const base = cat === 'rain' ? 0.00018 : 0.00009
  // 강도에 따라 0.4~1.4배
  return Math.round(area * base * (0.4 + intensity.value))
}

function buildParticles() {
  const n = particleCount()
  particles = []
  for (let i = 0; i < n; i++) particles.push(spawn(true))
}

function spawn(initial: boolean): Particle {
  const snow = category.value === 'snow'
  return {
    x: Math.random() * w,
    y: initial ? Math.random() * h : -10,
    len: snow ? 1.2 + Math.random() * 2.2 : 8 + Math.random() * 14 * (0.6 + intensity.value),
    speed: snow
      ? 0.4 + Math.random() * 1 + intensity.value
      : 6 + Math.random() * 6 + intensity.value * 6,
    drift: snow ? (Math.random() - 0.5) * 0.8 : (Math.random() - 0.5) * 0.6,
    phase: Math.random() * Math.PI * 2,
  }
}

function draw() {
  if (!ctx) return
  ctx.clearRect(0, 0, w, h)
  const snow = category.value === 'snow'

  if (snow) {
    ctx.fillStyle = 'rgba(255,255,255,0.9)'
    for (const p of particles) {
      p.phase += 0.02
      p.y += p.speed
      p.x += p.drift + Math.sin(p.phase) * 0.5
      if (p.y > h + 5) Object.assign(p, spawn(false))
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.len, 0, Math.PI * 2)
      ctx.fill()
    }
  } else {
    // 비: 살짝 기운 빗줄기
    ctx.strokeStyle = isDay.value ? 'rgba(180,200,225,0.5)' : 'rgba(160,180,210,0.55)'
    ctx.lineWidth = 1.1
    const slant = 1.2 + intensity.value
    for (const p of particles) {
      p.y += p.speed
      p.x += slant * 0.6
      if (p.y > h + 10) Object.assign(p, spawn(false))
      ctx.beginPath()
      ctx.moveTo(p.x, p.y)
      ctx.lineTo(p.x - slant * (p.len / 6), p.y - p.len)
      ctx.stroke()
    }
  }
  raf = requestAnimationFrame(draw)
}

function startAnimation() {
  cancelAnimationFrame(raf)
  if (reduceMotion) return
  const cat = category.value
  if (cat === 'rain' || cat === 'snow') {
    buildParticles()
    raf = requestAnimationFrame(draw)
  } else if (ctx) {
    ctx.clearRect(0, 0, w, h)
  }
  if (flashTimer) clearTimeout(flashTimer)
  scheduleFlash()
}

onMounted(() => {
  resize()
  window.addEventListener('resize', resize)
  startAnimation()
})
onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  if (flashTimer) clearTimeout(flashTimer)
  window.removeEventListener('resize', resize)
})

// 날씨 카테고리가 바뀌면 입자/효과 갱신
watch(category, () => {
  resize()
  startAnimation()
})
</script>

<template>
  <div class="weather-bg" :class="skyClass" aria-hidden="true">
    <!-- 흐림: 상단을 덮는 구름 띠 (떠다니는 구름 사이 빈틈을 메워 '꽉 찬' 느낌) -->
    <div v-if="isOvercast" class="overcast-band"></div>
    <!-- 흐림/안개: 흐릿한 구름 글로우 (분위기용 바탕, 흐릴수록 짙게) -->
    <template v-if="!reduceMotion && (category === 'clouds' || category === 'fog')">
      <div class="cloud c1"></div>
      <div class="cloud c2"></div>
      <div class="cloud c3"></div>
    </template>
    <!-- 몽글몽글 흰 구름: 개수=강도, 위치·크기·속도는 랜덤 (makeClouds) -->
    <template v-if="!reduceMotion && (category === 'clear' || category === 'clouds')">
      <div
        v-for="(c, i) in clouds"
        :key="i"
        class="cloud puff"
        :style="{
          top: c.top + 'vh',
          width: c.width + 'px',
          height: c.width * 0.3 + 'px',
          opacity: c.opacity,
          animationDuration: c.dur + 's',
          animationDelay: c.delay + 's',
        }"
      ></div>
    </template>
    <!-- 안개 오버레이 -->
    <div v-if="category === 'fog'" class="fog"></div>
    <!-- 맑음(낮): 태양 원반 + 햇살 -->
    <div v-if="category === 'clear' && isDay" class="sun">
      <div class="sun-glow"></div>
      <div class="sun-disc"></div>
    </div>

    <!-- 맑음(밤): 달 + 별 -->
    <template v-if="category === 'clear' && !isDay">
      <div class="moon">
        <div class="moon-glow"></div>
        <div class="moon-disc"></div>
      </div>
      <div class="stars">
        <span
          v-for="(s, i) in stars"
          :key="i"
          class="star"
          :style="{
            left: s.x + 'vw',
            top: s.y + 'vh',
            width: s.r * 2 + 'px',
            height: s.r * 2 + 'px',
            animationDelay: s.delay + 's',
            animationDuration: s.dur + 's',
          }"
        ></span>
      </div>
    </template>

    <!-- 비/눈 입자 캔버스 -->
    <canvas ref="canvas" class="particles"></canvas>

    <!-- 번개 섬광 -->
    <div v-if="category === 'thunder'" class="lightning" :class="{ on: flash }"></div>

    <!-- 하단 풍경: 잔디 언덕 + 나무 한 그루 -->
    <svg
      class="scenery"
      :class="{ snowy: category === 'snow' }"
      viewBox="0 0 1440 320"
      preserveAspectRatio="xMidYMax slice"
      aria-hidden="true"
    >
      <!-- 뒤쪽 언덕 -->
      <path
        class="hill-back"
        d="M0 220 C 240 170 480 250 720 215 S 1200 175 1440 220 L1440 320 L0 320 Z"
      />
      <!-- 앞쪽 잔디 -->
      <path
        class="hill-front"
        d="M0 260 C 300 225 560 285 820 255 S 1240 230 1440 262 L1440 320 L0 320 Z"
      />

      <!-- 나무 한 그루 -->
      <g class="tree" transform="translate(1120 268)">
        <rect class="trunk" x="-7" y="-46" width="14" height="50" rx="4" />
        <g class="crown">
          <circle cx="0" cy="-70" r="40" />
          <circle cx="-28" cy="-52" r="30" />
          <circle cx="28" cy="-52" r="30" />
          <circle cx="0" cy="-44" r="34" />
        </g>
        <!-- 눈 올 때 나무 위 눈 쌓임 -->
        <circle v-if="category === 'snow'" class="tree-snow" cx="0" cy="-92" r="22" />
      </g>

      <!-- 잔디 포기 몇 개 (앞쪽) -->
      <g class="blades">
        <path d="M120 300 q -4 -26 6 -40 q 6 16 0 40 Z" />
        <path d="M134 300 q 2 -22 12 -34 q 2 18 -4 34 Z" />
        <path d="M300 302 q -5 -24 5 -38 q 7 14 1 38 Z" />
        <path d="M520 300 q -3 -28 7 -42 q 6 18 -1 42 Z" />
        <path d="M900 302 q -4 -22 6 -34 q 5 14 0 34 Z" />
        <path d="M1300 300 q -4 -26 7 -40 q 6 16 -1 40 Z" />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.weather-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  transition: background 1.2s ease;
}

/* ── 하늘 그라데이션 (카테고리 × 낮밤) ───────────────────── */
.sky-clear.day {
  background: linear-gradient(180deg, #cfe8ff 0%, #eaf5ff 55%, var(--paper, #fbfaf6) 100%);
}
.sky-clear.night {
  background: linear-gradient(180deg, #1b2740 0%, #2c3a59 60%, #46537a 100%);
}
.sky-clouds.day {
  background: linear-gradient(180deg, #c4ccd6 0%, #dfe5ec 55%, var(--paper, #fbfaf6) 100%);
}
.sky-clouds.night {
  background: linear-gradient(180deg, #20242e 0%, #2e333f 60%, #3a4150 100%);
}
/* 흐림: 더 짙은 잿빛 하늘 */
.sky-clouds.overcast.day {
  background: linear-gradient(180deg, #9aa1ab 0%, #b7bdc6 55%, #d2d6dc 100%);
}
.sky-clouds.overcast.night {
  background: linear-gradient(180deg, #171a21 0%, #23272f 60%, #2d323c 100%);
}
.sky-rain.day {
  background: linear-gradient(180deg, #9aa7b6 0%, #b9c3cf 55%, #d6dde4 100%);
}
.sky-rain.night {
  background: linear-gradient(180deg, #161b24 0%, #232b38 60%, #2f3947 100%);
}
.sky-snow.day {
  background: linear-gradient(180deg, #d3dae2 0%, #e8edf2 55%, #f5f8fb 100%);
}
.sky-snow.night {
  background: linear-gradient(180deg, #232936 0%, #313a4a 60%, #404a5c 100%);
}
.sky-fog.day {
  background: linear-gradient(180deg, #c2c6cc 0%, #d8dbe0 55%, #e9ebee 100%);
}
.sky-fog.night {
  background: linear-gradient(180deg, #232730 0%, #313640 60%, #3c424d 100%);
}
.sky-thunder.day {
  background: linear-gradient(180deg, #6f7682 0%, #8a93a0 55%, #aab2bd 100%);
}
.sky-thunder.night {
  background: linear-gradient(180deg, #12151c 0%, #1c222d 60%, #272e3b 100%);
}

.particles {
  position: absolute;
  inset: 0;
}

/* ── 구름 (흘러가는) ─────────────────────────────────────── */
.cloud {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.55), rgba(255, 255, 255, 0) 70%);
  filter: blur(8px);
  opacity: 0.7;
  will-change: transform;
}
.night .cloud {
  background: radial-gradient(circle, rgba(200, 210, 230, 0.22), rgba(200, 210, 230, 0) 70%);
}
/* 흐림: 바탕 글로우도 더 짙고 넓게 */
.overcast .cloud {
  opacity: 0.95;
  filter: blur(14px);
}
.c1 {
  width: 460px;
  height: 220px;
  top: 8%;
  left: -30%;
  animation: drift 48s linear infinite;
}
.c2 {
  width: 620px;
  height: 280px;
  top: 28%;
  left: -45%;
  animation: drift 70s linear infinite;
  animation-delay: -20s;
}
.c3 {
  width: 380px;
  height: 180px;
  top: 50%;
  left: -25%;
  animation: drift 58s linear infinite;
  animation-delay: -38s;
}
@keyframes drift {
  to {
    transform: translateX(180vw);
  }
}

/* 몽글몽글 흰 구름 (본체 + 둥근 덩어리 두 개) */
.cloud.puff {
  filter: blur(1px) drop-shadow(0 6px 10px rgba(90, 100, 120, 0.18));
  border-radius: 100px;
  /* 화면 왼쪽 밖에서 시작해 drift 로 오른쪽으로 흐른다. 크기/위치/속도/delay 는 인라인(랜덤). */
  left: -30%;
  animation: drift linear infinite;
}
.cloud.puff,
.cloud.puff::before,
.cloud.puff::after {
  background: #ffffff;
}
.cloud.puff::before,
.cloud.puff::after {
  content: '';
  position: absolute;
  border-radius: 50%;
}
.cloud.puff::before {
  width: 55%;
  height: 130%;
  top: -55%;
  left: 14%;
}
.cloud.puff::after {
  width: 42%;
  height: 105%;
  top: -38%;
  right: 16%;
}
.night .cloud.puff,
.night .cloud.puff::before,
.night .cloud.puff::after {
  background: #aeb9d0;
  opacity: 0.85;
}
/* 흐림일 땐 구름을 살짝 잿빛으로 (맑은 흰 구름과 구분) */
.overcast .cloud.puff,
.overcast .cloud.puff::before,
.overcast .cloud.puff::after {
  background: #e3e7ee;
}

/* ── 흐림 구름 띠: 상단을 덮어 빈틈을 메우는 정적 구름층 ───────── */
.overcast-band {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 42%;
  /* 위는 짙고 아래로 갈수록 투명 → 떠다니는 구름과 자연스레 섞임 */
  background: linear-gradient(
    180deg,
    #dfe3ea 0%,
    rgba(223, 227, 234, 0.85) 40%,
    rgba(223, 227, 234, 0) 100%
  );
  filter: blur(6px);
  animation: bandShift 22s ease-in-out infinite alternate;
}
.night .overcast-band {
  background: linear-gradient(
    180deg,
    #2b303b 0%,
    rgba(43, 48, 59, 0.8) 40%,
    rgba(43, 48, 59, 0) 100%
  );
}
@keyframes bandShift {
  from {
    opacity: 0.85;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ── 안개 ────────────────────────────────────────────────── */
.fog {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.35), rgba(255, 255, 255, 0.12));
  backdrop-filter: blur(1.5px);
  animation: fogShift 16s ease-in-out infinite alternate;
}
.night .fog {
  background: linear-gradient(180deg, rgba(180, 190, 205, 0.18), rgba(180, 190, 205, 0.06));
}
@keyframes fogShift {
  from {
    opacity: 0.55;
  }
  to {
    opacity: 0.9;
  }
}

/* ── 햇살 ────────────────────────────────────────────────── */
/* ── 태양 (맑은 낮) ──────────────────────────────────────── */
.sun {
  position: absolute;
  top: 60px;
  right: 90px;
  width: 96px;
  height: 96px;
}
.sun-glow {
  position: absolute;
  inset: -180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 236, 170, 0.75), rgba(255, 236, 170, 0) 62%);
  animation: glow 7s ease-in-out infinite alternate;
}
.sun-disc {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 38% 36%, #fff6d2, #ffd95e 62%, #ffc83a 100%);
  box-shadow: 0 0 40px rgba(255, 207, 74, 0.65);
  animation: bob 9s ease-in-out infinite alternate;
}
@keyframes glow {
  from {
    opacity: 0.55;
    transform: scale(1);
  }
  to {
    opacity: 0.9;
    transform: scale(1.1);
  }
}
@keyframes bob {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(10px);
  }
}

/* ── 달 + 별 (맑은 밤) ───────────────────────────────────── */
.moon {
  position: absolute;
  top: 64px;
  right: 100px;
  width: 84px;
  height: 84px;
}
.moon-glow {
  position: absolute;
  inset: -120px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(214, 226, 255, 0.4), rgba(214, 226, 255, 0) 60%);
  animation: glow 9s ease-in-out infinite alternate;
}
.moon-disc {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 36% 34%, #fdfdf4, #dfe6f2 70%, #c4cee0 100%);
  box-shadow: 0 0 30px rgba(210, 224, 255, 0.5);
  /* 초승달 느낌: 오른쪽에 살짝 그림자 */
  overflow: hidden;
  animation: bob 11s ease-in-out infinite alternate;
}
.moon-disc::after {
  content: '';
  position: absolute;
  top: -12%;
  right: -34%;
  width: 92%;
  height: 124%;
  border-radius: 50%;
  background: var(--moon-shadow, #1b2740);
  opacity: 0.55;
}
.stars {
  position: absolute;
  inset: 0;
}
.star {
  position: absolute;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 4px rgba(255, 255, 255, 0.8);
  animation: twinkle 3s ease-in-out infinite;
}
@keyframes twinkle {
  0%,
  100% {
    opacity: 0.2;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

/* ── 번개 ────────────────────────────────────────────────── */
.lightning {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.7);
  opacity: 0;
  transition: opacity 90ms ease;
}
.lightning.on {
  opacity: 0.85;
}

/* ── 하단 풍경 (잔디 + 나무) ─────────────────────────────── */
.scenery {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: clamp(140px, 24vh, 260px);
  display: block;
}
.hill-back {
  fill: #a9cf8e;
}
.hill-front {
  fill: #8bbf6e;
}
.blades path {
  fill: #76ad5c;
  transform-box: fill-box;
  transform-origin: bottom center;
  animation: sway 4.5s ease-in-out infinite alternate;
}
.blades path:nth-child(2n) {
  animation-duration: 5.6s;
  animation-delay: -1.2s;
}
.blades path:nth-child(3n) {
  animation-duration: 3.8s;
  animation-delay: -0.6s;
}

.trunk {
  fill: #8a6240;
}
.crown circle {
  fill: #6fae57;
}
.crown circle:first-child {
  fill: #7cba61;
}
.tree-snow {
  fill: rgba(255, 255, 255, 0.9);
}
/* 잎(crown)만 바람에 살랑 — 밑동은 고정, 잎 아래쪽을 축으로 */
.crown {
  transform-box: fill-box;
  transform-origin: 50% 90%;
  animation: treeSway 5s ease-in-out infinite alternate;
}

/* 밤이면 풍경도 어둡게 */
.night .hill-back {
  fill: #2f4a36;
}
.night .hill-front {
  fill: #28432f;
}
.night .blades path {
  fill: #22392a;
}
.night .crown circle {
  fill: #2c4733;
}
.night .crown circle:first-child {
  fill: #335239;
}
.night .trunk {
  fill: #4a3526;
}

/* 눈 오면 잔디에 눈 덮인 느낌으로 밝게 */
.scenery.snowy .hill-back {
  fill: #d6e2d2;
}
.scenery.snowy .hill-front {
  fill: #e6eee2;
}
.scenery.snowy .blades path {
  fill: #cdddc6;
}

@keyframes sway {
  from {
    transform: rotate(-7deg);
  }
  to {
    transform: rotate(7deg);
  }
}
@keyframes treeSway {
  from {
    transform: rotate(-2.4deg);
  }
  to {
    transform: rotate(2.4deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .cloud,
  .overcast-band,
  .fog,
  .sun-glow,
  .sun-disc,
  .moon-glow,
  .moon-disc,
  .star,
  .blades path,
  .crown {
    animation: none;
  }
}
</style>
