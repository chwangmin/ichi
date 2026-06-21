import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { test } from 'node:test'

const noteCard = readFileSync(new URL('../src/components/NoteCard.vue', import.meta.url), 'utf8')
const notesView = readFileSync(new URL('../src/views/NotesView.vue', import.meta.url), 'utf8')
const entriesApi = readFileSync(new URL('../src/api/entries.ts', import.meta.url), 'utf8')
const modalUrl = new URL('../src/components/EditNoteModal.vue', import.meta.url)

test('note cards can open the edit modal without hijacking tool buttons', () => {
  assert.match(noteCard, /\(e:\s*'open',\s*id:\s*string\):\s*void/)
  assert.match(noteCard, /@click="emit\('open', entry\.id\)"/)
  assert.match(noteCard, /@click\.stop="emit\('toggle-pin', entry\.id\)"/)
  assert.match(noteCard, /@click\.stop="emit\('remove', entry\.id\)"/)
})

test('notes view loads details and renders the edit modal', () => {
  assert.match(notesView, /import EditNoteModal/)
  assert.match(notesView, /entriesApi\.get\(id\)/)
  assert.match(notesView, /<EditNoteModal/)
  assert.match(notesView, /@save="updateEntry"/)
})

test('update API can save body, location, and newly attached media', () => {
  assert.match(entriesApi, /interface UpdateEntryPayload/)
  assert.match(entriesApi, /lat\??:\s*number\s*\|\s*null/)
  assert.match(entriesApi, /mediaIds\??:\s*string\[\]/)
  assert.match(entriesApi, /updateContent:\s*\(id:\s*string,\s*payload:\s*UpdateEntryPayload\)/)
})

test('edit modal supports rich body editing, image upload on save, and location changes', () => {
  assert.equal(existsSync(modalUrl), true)
  const modal = readFileSync(modalUrl, 'utf8')

  assert.match(modal, /toDisplayHtml/)
  assert.match(modal, /contenteditable="true"/)
  assert.match(modal, /pendingImages/)
  assert.match(modal, /await uploadPendingImages\([^)]*\)/)
  assert.match(modal, /LocationPicker/)
  assert.match(modal, /v-model="location"/)
  assert.match(modal, /emit\('save'/)
})

test('edit modal does not clip the location picker panel', () => {
  const modal = readFileSync(modalUrl, 'utf8')

  assert.match(modal, /\.modal\s*\{[^}]*overflow:\s*visible/s)
  assert.doesNotMatch(modal, /\.modal\s*\{[^}]*overflow:\s*hidden/s)
})
