import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 모달이 열려 있는 동안 뒤 페이지 스크롤을 잠근다.
 *
 * 이 앱의 실제 스크롤 컨테이너는 body 가 아니라 AppShell 의 .scroll 엘리먼트라,
 * body 만 막아선 부족하다. 그래서 모달 마운트 시점에 "스크롤 가능한 조상"을
 * 모두 찾아 overflow:hidden 으로 잠그고, 언마운트 시 원래 값으로 되돌린다.
 *
 * 사용: 모달 컴포넌트 setup 에서 useScrollLock() 한 번 호출.
 */
export function useScrollLock() {
  const locked: { el: HTMLElement; prev: string }[] = []

  function isScrollable(el: HTMLElement): boolean {
    const oy = getComputedStyle(el).overflowY
    return (oy === 'auto' || oy === 'scroll') && el.scrollHeight > el.clientHeight
  }

  function lock(start: HTMLElement | null) {
    let el: HTMLElement | null = start
    while (el && el !== document.body) {
      if (isScrollable(el)) {
        locked.push({ el, prev: el.style.overflow })
        el.style.overflow = 'hidden'
      }
      el = el.parentElement
    }
    // 문서 자체 스크롤도 함께 잠근다 (모바일/짧은 화면 대비)
    const html = document.documentElement
    locked.push({ el: html, prev: html.style.overflow })
    html.style.overflow = 'hidden'
  }

  function unlock() {
    for (const { el, prev } of locked) el.style.overflow = prev
    locked.length = 0
  }

  onMounted(() => {
    // 모달이 backdrop(position:fixed) 안에 있으니, 잠가야 할 컨테이너는
    // body 의 첫 자식 트리(앱 셸)다. 활성 요소 기준으로 조상을 거슬러 올라간다.
    lock(document.querySelector('.scroll') as HTMLElement | null)
  })
  onBeforeUnmount(unlock)
}
