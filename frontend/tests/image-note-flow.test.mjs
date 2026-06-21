import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const noteCard = readFileSync(new URL('../src/components/NoteCard.vue', import.meta.url), 'utf8')
const noteComposer = readFileSync(new URL('../src/components/NoteComposer.vue', import.meta.url), 'utf8')
const entriesApi = readFileSync(new URL('../src/api/entries.ts', import.meta.url), 'utf8')
const types = readFileSync(new URL('../src/types.ts', import.meta.url), 'utf8')

test('entry list items carry the first image id for Keep-like cards', () => {
  assert.match(entriesApi, /thumbMediaId\??:\s*string\s*\|\s*null/)
  assert.match(types, /thumbMediaId\??:\s*string\s*\|\s*null/)
})

test('note cards render an image preview instead of only empty text', () => {
  assert.match(noteCard, /thumbUrl/)
  assert.match(noteCard, /entry\.thumbMediaId/)
  assert.match(noteCard, /<img[^>]+:src="thumbUrl\(entry\.thumbMediaId\)"/s)
  assert.match(noteCard, /v-if="entry\.preview"/)
  assert.doesNotMatch(noteCard, /\|\|\s*'\(빈 기록\)'/)
})

test('composer defers image upload until completion while showing a local preview', () => {
  assert.match(noteComposer, /pendingImages/)
  assert.match(noteComposer, /URL\.createObjectURL/)
  assert.match(noteComposer, /uploadPendingImages/)
  assert.match(noteComposer, /await uploadPendingImages\([^)]*\)/)
})
