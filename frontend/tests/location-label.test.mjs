import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { test } from 'node:test'

const helperUrl = new URL('../src/composables/usePlaceName.ts', import.meta.url)
const placeSearch = readFileSync(new URL('../src/composables/usePlaceSearch.ts', import.meta.url), 'utf8')
const picker = readFileSync(new URL('../src/components/LocationPicker.vue', import.meta.url), 'utf8')
const composer = readFileSync(new URL('../src/components/NoteComposer.vue', import.meta.url), 'utf8')
const modal = readFileSync(new URL('../src/components/EditNoteModal.vue', import.meta.url), 'utf8')
const noteCard = readFileSync(new URL('../src/components/NoteCard.vue', import.meta.url), 'utf8')
const settings = readFileSync(new URL('../src/views/SettingsView.vue', import.meta.url), 'utf8')

test('location helper resolves readable Korean place names from map geocoding', () => {
  assert.equal(existsSync(helperUrl), true)
  const helper = readFileSync(helperUrl, 'utf8')

  assert.match(helper, /loadGoogleMaps/)
  assert.match(helper, /new google\.maps\.Geocoder/)
  assert.match(helper, /administrative_area_level_1/)
  assert.match(helper, /sublocality_level_2/)
  assert.match(helper, /서울특별시.*서울시/s)
})

test('composer stores and displays place names instead of raw coordinates', () => {
  assert.match(picker, /resolvePlaceName/)
  assert.match(composer, /placeName:/)
  assert.match(composer, /location\.placeName/)
  assert.doesNotMatch(composer, /location\.lat\.toFixed\(4\).*location\.lng\.toFixed\(4\)/s)
})

test('edit modal stores and displays place names instead of raw coordinates', () => {
  assert.match(picker, /resolvePlaceName/)
  assert.match(modal, /placeName:/)
  assert.match(modal, /location\.placeName/)
  assert.doesNotMatch(modal, /\$\{location\.lat\.toFixed\(4\)\}, \$\{location\.lng\.toFixed\(4\)\}/)
})

test('note cards show saved locations as an icon only', () => {
  assert.match(noteCard, /class="loc icon-only"/)
  assert.doesNotMatch(noteCard, /\{\{\s*entry\.placeName/)
})

test('settings place search uses backend VWorld proxy for Korean addresses', () => {
  assert.match(placeSearch, /api\.get<PlaceResult\[]>/)
  assert.match(placeSearch, /\/places\/search\?q=/)
  assert.doesNotMatch(placeSearch, /geocoding-api\.open-meteo\.com/)

  assert.match(settings, /도로명 또는 지번 주소/)
  assert.match(settings, /@click="runSearch"/)
  assert.doesNotMatch(settings, /@input="onSearchInput"/)
  assert.doesNotMatch(settings, /도시명 영문 입력/)
})
