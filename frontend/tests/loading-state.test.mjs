import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const loadingState = readFileSync(new URL('../src/components/LoadingState.vue', import.meta.url), 'utf8')
const views = [
  '../src/views/NotesView.vue',
  '../src/views/MediaView.vue',
  '../src/views/CalendarView.vue',
  '../src/views/AtlasView.vue',
  '../src/views/SettingsView.vue',
].map((path) => readFileSync(new URL(path, import.meta.url), 'utf8'))

test('shared loading state uses the existing spinner treatment', () => {
  assert.match(loadingState, /class="loading-state"/)
  assert.match(loadingState, /class="loading-spinner"/)
  assert.match(loadingState, /로딩 중…/)
  assert.match(loadingState, /animation:\s*loading-spin/)
})

test('data loading screens use spinner loading instead of prose loading copy', () => {
  for (const view of views) {
    assert.match(view, /LoadingState/)
    assert.doesNotMatch(view, /불러오는 중…|지도 불러오는 중…|기록 여는 중…/)
  }
})
