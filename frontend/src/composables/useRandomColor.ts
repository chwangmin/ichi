import type { CardColor } from '@/types'

/**
 * 새 일기 카드에 줄 색을 무작위로 고른다 (캘린더가 알록달록하게).
 * 노트/캘린더/아틀라스 어디서 작성하든 같은 규칙을 쓰도록 한곳에 둔다.
 * 사용자는 작성 후 팔레트로 색을 바꿀 수 있다.
 */
export function randomColor(): CardColor {
  const palette: CardColor[] = ['y', 'g', 'b', 'p']
  return palette[Math.floor(Math.random() * palette.length)]
}
