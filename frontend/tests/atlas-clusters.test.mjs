import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { test } from 'node:test'

const clusterUrl = new URL('../src/composables/useAtlasClusters.ts', import.meta.url)
const atlas = readFileSync(new URL('../src/views/AtlasView.vue', import.meta.url), 'utf8')

test('atlas clustering groups pins within five meters', () => {
  assert.equal(existsSync(clusterUrl), true)
  const clusters = readFileSync(clusterUrl, 'utf8')

  assert.match(clusters, /CLUSTER_RADIUS_METERS\s*=\s*5/)
  assert.match(clusters, /function distanceMeters/)
  assert.match(clusters, /export function clusterAtlasPins/)
  assert.match(clusters, /items:\s*AtlasPin\[\]/)
})

test('atlas renders cluster markers with count badges and grouped preview lists', () => {
  assert.match(atlas, /clusterAtlasPins/)
  assert.match(atlas, /type AtlasCluster/)
  assert.match(atlas, /selectedCluster/)
  assert.match(atlas, /buildClusterPinElement/)
  assert.match(atlas, /cluster\.items\.length/)
  assert.match(atlas, /cluster-count/)
  assert.match(atlas, /v-for="pin in selectedCluster\.items"/)
})

test('atlas recomputes clusters after moving a pin', () => {
  assert.match(atlas, /atlasPins/)
  assert.match(atlas, /renderClusters/)
  assert.match(atlas, /clusterAtlasPins\(atlasPins\.value\)/)
  assert.match(atlas, /atlasPins\.value\.splice/)
  assert.match(atlas, /await renderClusters\(\)/)
})

test('atlas can search Korean addresses through the shared place search proxy', () => {
  assert.match(atlas, /searchPlaces/)
  assert.match(atlas, /atlasSearchQuery/)
  assert.match(atlas, /runAtlasSearch/)
  assert.match(atlas, /chooseAtlasSearchResult/)
  assert.match(atlas, /placeholder="주소 검색"/)
  assert.match(atlas, /map\.value\.panTo\(\{ lat: result\.lat, lng: result\.lng \}\)/)
  assert.match(atlas, /map\.value\.setZoom\(18\)/)
  assert.match(atlas, /searchMarker/)
  assert.match(atlas, /showSearchMarker/)
  assert.match(atlas, /className = 'search-pin'/)
})

test('atlas shows the selected preview above the selected map marker', () => {
  assert.match(atlas, /previewPoint/)
  assert.match(atlas, /previewStyle/)
  assert.match(atlas, /updatePreviewPoint/)
  assert.match(atlas, /new g\.maps\.OverlayView\(\)/)
  assert.match(atlas, /fromLatLngToContainerPixel/)
  assert.doesNotMatch(atlas, /fromLatLngToDivPixel/)
  assert.match(atlas, /:style="previewStyle"/)
  assert.doesNotMatch(atlas, /\.preview\s*\{[^}]*bottom:\s*16px/s)
  assert.match(atlas, /--preview-gap:\s*44px/)
  assert.match(atlas, /transform:\s*translate\(-50%, calc\(-100% - var\(--preview-gap\)\)\)/)
})

test('atlas move preview follows the dragged pin position', () => {
  assert.match(atlas, /movePreviewPos/)
  assert.match(atlas, /function updateMovePreviewPosition/)
  assert.match(atlas, /marker\.addListener\('drag'/)
  assert.match(atlas, /marker\.addListener\('dragend'/)
  assert.match(atlas, /moveMode\.value && movePreviewPos\.value/)
  assert.match(atlas, /new google\.maps\.LatLng\(latLng\.lat, latLng\.lng\)/)
})
