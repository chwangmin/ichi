<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watchEffect } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import WeatherChip from '@/components/WeatherChip.vue'
import WeatherBackground from '@/components/WeatherBackground.vue'
import { useWeatherBg } from '@/composables/useWeatherBg'
import { useWeather } from '@/composables/useWeather'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const auth = useAuthStore()
const sidebarOpen = ref(false)
const { enabled: weatherBgOn } = useWeatherBg()
const { weather } = useWeather()
const { mode: themeMode } = useTheme()

// 어두운 테마(.night) 적용 여부. 사용자가 고른 모드(다크/라이트)가 날씨보다 우선.
//  - dark  : 항상 어둡게
//  - light : 항상 밝게
//  - auto  : 날씨 배경이 켜져 있고 '밤'일 때만 어둡게 (기존 자동 동작)
const isNight = computed(() => {
  if (themeMode.value === 'dark') return true
  if (themeMode.value === 'light') return false
  return weatherBgOn.value && weather.value?.isDay === false
})

// 밤 테마를 <html> 에도 반영한다. EditNoteModal 은 <Teleport to="body"> 라
// .layout 바깥(body 직속)에 렌더되므로, .layout 의 .night 만으론 토큰을 못 받는다.
// html.night 로 두면 텔레포트된 모달까지 밤 토큰을 상속한다.
watchEffect(() => {
  document.documentElement.classList.toggle('night', isNight.value)
})
onBeforeUnmount(() => {
  document.documentElement.classList.remove('night')
})

const goToSettings = () => {
  router.push({ name: 'settings' })
}

const nav = [
  { name: 'notes', label: '노트', icon: 'lightbulb' },
  { name: 'calendar', label: '캘린더', icon: 'calendar_month' },
  { name: 'media', label: '미디어', icon: 'photo_library' },
  { name: 'atlas', label: '아틀라스', icon: 'map' },
] as const

const avatarInitial = (auth.user?.name ?? '나').trim().charAt(0)
</script>

<template>
  <div class="layout" :class="{ night: isNight }">
    <WeatherBackground v-if="weatherBgOn" />
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="brand">
        <div class="seal">一</div>
        <div>
          <div class="name">이치</div>
          <div class="sub">ICHI</div>
        </div>
      </div>

      <nav class="nav">
        <RouterLink
          v-for="item in nav"
          :key="item.name"
          class="nav-item"
          :to="{ name: item.name }"
          @click="sidebarOpen = false"
        >
          <span class="mi">{{ item.icon }}</span
          >{{ item.label }}
        </RouterLink>

        <div class="nav-divider"></div>

        <RouterLink class="nav-item" :to="{ name: 'settings' }" @click="sidebarOpen = false">
          <span class="mi">settings</span>설정
        </RouterLink>

        <!-- §6-7: 비활성 "예정" placeholder. 나중에 E2E 암호화/저장소 옵션 자리. -->
        <div class="nav-item nav-future" aria-disabled="true">
          <span class="mi">cloud</span>클라우드 서비스<span class="tag">예정</span>
        </div>
      </nav>

      <div class="save-status">
        <span class="mi">cloud_done</span>
        <div><b>Google Drive</b>에 저장됨<br />연결됨</div>
      </div>
    </aside>

    <!-- 모바일 사이드바 오버레이 -->
    <div v-if="sidebarOpen" class="scrim" @click="sidebarOpen = false"></div>

    <main class="main">
      <div class="topbar">
        <button class="menu-btn" aria-label="메뉴 열기" @click="sidebarOpen = !sidebarOpen">
          <span class="mi">menu</span>
        </button>
        <WeatherChip />
        <div class="spacer"></div>
        <div class="avatar" title="Google 계정" @click="goToSettings">
          {{ avatarInitial }}
        </div>
      </div>

      <div class="scroll">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ---------- Sidebar ---------- */
.sidebar {
  width: 268px;
  flex-shrink: 0;
  background: var(--sidebar);
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  padding: calc(18px + var(--safe-top)) 14px 14px;
  transition: transform 0.22s ease;
  position: relative;
  z-index: 1;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 12px 20px;
}
.seal {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  background: var(--seal);
  border-radius: 9px;
  display: grid;
  place-items: center;
  color: #fff;
  font-family: 'Nanum Myeongjo', serif;
  font-weight: 800;
  font-size: 26px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.08);
}
.brand .name {
  font-family: 'Nanum Myeongjo', serif;
  font-weight: 800;
  font-size: 21px;
  letter-spacing: 0.5px;
  /* body 에서 내려온 색이 아니라 (밤이면 밝아지는) --ink 토큰을 따르게 명시 */
  color: var(--ink);
}
.brand .sub {
  font-size: 11px;
  color: var(--ink-faint);
  letter-spacing: 2px;
  margin-top: 1px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 46px;
  padding: 0 16px;
  border-radius: 0 24px 24px 0;
  margin-right: 6px;
  cursor: pointer;
  color: var(--ink);
  font-size: 14.5px;
  font-weight: 500;
  user-select: none;
  text-decoration: none;
  transition: background 0.14s;
}
.nav-item .mi {
  font-size: 21px;
  color: var(--ink-soft);
}
.nav-item:hover {
  background: var(--hover);
}
.nav-item.router-link-active {
  background: var(--seal-soft);
  color: var(--seal);
}
.nav-item.router-link-active .mi {
  color: var(--seal);
}
.nav-divider {
  height: 1px;
  background: var(--line);
  margin: 10px 8px;
}
.nav-future {
  margin-top: 2px;
  opacity: 0.5;
  cursor: default;
}
.nav-future:hover {
  background: none;
}
.nav-future .tag {
  margin-left: auto;
  font-size: 10px;
  background: var(--line);
  color: var(--ink-soft);
  padding: 2px 7px;
  border-radius: 20px;
  font-weight: 500;
}

.save-status {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 14px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 11px;
  font-size: 12px;
  color: var(--ink-soft);
}
.save-status .mi {
  font-size: 18px;
  color: #3a7d44;
}
.save-status b {
  color: var(--ink);
  font-weight: 500;
}

/* ---------- Main ---------- */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
  z-index: 1;
}
.topbar {
  height: calc(64px + var(--safe-top));
  padding-top: var(--safe-top);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 14px;
  padding-left: 22px;
  padding-right: 22px;
  border-bottom: 1px solid var(--line-soft);
  /* 날씨 배경 위에서 글씨가 읽히도록 살짝 반투명 종이색 깔기 */
  background: color-mix(in srgb, var(--paper) 72%, transparent);
  backdrop-filter: blur(6px);
}
.menu-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: none;
  border-radius: 50%;
  cursor: pointer;
  display: none;
  place-items: center;
  color: var(--ink-soft);
}
.menu-btn:hover {
  background: var(--hover);
}
.spacer {
  flex: 1;
}
.avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7c9cbf, #5a7a9e);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 15px;
  cursor: pointer;
  flex-shrink: 0;
  transition:
    transform 0.14s,
    box-shadow 0.14s;
}
.avatar:hover {
  transform: scale(1.08);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.scroll {
  flex: 1;
  overflow-y: auto;
  padding: 30px 22px calc(60px + var(--safe-bottom));
  /* 베일 없음 — 날씨 배경이 카드/컴포저 뒤로 직접 비친다. 가독성은 각 카드의 반투명 배경이 담당. */
  background: transparent;
}
.scroll::-webkit-scrollbar {
  width: 11px;
}
.scroll::-webkit-scrollbar-thumb {
  background: var(--line);
  border-radius: 10px;
  border: 3px solid var(--paper);
}
.scroll::-webkit-scrollbar-thumb:hover {
  background: #d8d5cc;
}

.scrim {
  display: none;
}

/* ---------- 모바일 ---------- */
@media (max-width: 760px) {
  .menu-btn {
    display: grid;
  }
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 30;
    transform: translateX(-100%);
    box-shadow: 0 0 40px rgba(0, 0, 0, 0.2);
  }
  .sidebar.open {
    transform: none;
  }
  .scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 20;
    background: rgba(0, 0, 0, 0.25);
  }
}
</style>
