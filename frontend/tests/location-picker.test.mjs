import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { test } from 'node:test'

const pickerUrl = new URL('../src/components/LocationPicker.vue', import.meta.url)
const picker = existsSync(pickerUrl) ? readFileSync(pickerUrl, 'utf8') : ''
const composer = readFileSync(new URL('../src/components/NoteComposer.vue', import.meta.url), 'utf8')
const modal = readFileSync(new URL('../src/components/EditNoteModal.vue', import.meta.url), 'utf8')

test('location picker lets writers choose current position or searched places', () => {
  assert.equal(existsSync(pickerUrl), true)
  assert.match(picker, /searchPlaces/)
  assert.match(picker, /resolvePlaceName/)
  assert.match(picker, /navigator\.geolocation\.getCurrentPosition/)
  assert.match(picker, /현재 위치 사용/)
  assert.match(picker, /주소 검색/)
  assert.match(picker, /위치 제거/)
  assert.match(picker, /emit\('update:modelValue'/)
})

test('composer and edit modal share the same location picker flow', () => {
  assert.match(composer, /import LocationPicker/)
  assert.match(composer, /<LocationPicker/)
  assert.match(composer, /v-model="location"/)
  assert.match(composer, /placement="below"/)
  assert.match(composer, /@busy-change="locationBusy = \$event"/)

  assert.match(modal, /import LocationPicker/)
  assert.match(modal, /<LocationPicker/)
  assert.match(modal, /v-model="location"/)
  assert.match(modal, /placement="above"/)
  assert.match(modal, /@busy-change="locationBusy = \$event"/)
})

test('location picker can open below the note composer controls', () => {
  assert.match(picker, /placement\?:\s*'above'\s*\|\s*'below'/)
  assert.match(picker, /class="\['lp-panel', `is-\$\{placement\}`\]"/)
  assert.match(picker, /\.lp-panel\.is-below\s*\{[^}]*top:\s*46px/s)
  assert.match(picker, /\.lp-panel\.is-above\s*\{[^}]*bottom:\s*46px/s)
})

test('current location action is visually presented as a button', () => {
  assert.match(picker, /\.lp-action\s*\{[^}]*border:\s*1px solid var\(--line-soft\)/s)
  assert.match(picker, /\.lp-action\s*\{[^}]*background:\s*var\(--seal-soft\)/s)
  assert.match(picker, /\.lp-action\s*\{[^}]*font-weight:\s*600/s)
  assert.doesNotMatch(picker, /\.lp-action\s*\{[^}]*border:\s*1px solid color-mix/s)
})

test('current location action keeps its background but uses warmer night text', () => {
  assert.match(
    picker,
    /:global\(\.night \.location-picker \.lp-action\),\s*:global\(\.night \.location-picker \.lp-action \.mi\)\s*\{/,
  )
  assert.match(
    picker,
    /:global\(\.night \.location-picker \.lp-action\),\s*:global\(\.night \.location-picker \.lp-action \.mi\)\s*\{[^}]*color:\s*color-mix\(in srgb, var\(--seal\) 52%, var\(--ink-on-color\)\)/s,
  )
  assert.doesNotMatch(
    picker,
    /:global\(\.night \.location-picker \.lp-action\)\s*\{[^}]*background:/s,
  )
})
