import { useRouter } from 'vue-router'

/**
 * 기록을 아틀라스에서 그 위치로 보여주기 위한 라우팅 헬퍼.
 * EditNoteModal 의 위치 칩(@view-on-map)에서 호출한다.
 * 아틀라스로 ?entry=<id> 를 달고 이동하면, AtlasView 가 그 기록이 속한
 * 위치로 지도를 옮기고 미리보기를 연다.
 */
export function useViewOnMap() {
  const router = useRouter()
  function viewOnMap(id: string) {
    router.push({ name: 'atlas', query: { entry: id } })
  }
  return { viewOnMap }
}
