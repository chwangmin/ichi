import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

test('Material Symbols font does not swap to visible ligature text during first paint', () => {
  const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8')
  const materialFontLink = html.match(/href="([^"]*Material\+Symbols\+Outlined[^"]*)"/)

  assert.ok(materialFontLink, 'Material Symbols stylesheet link should exist')
  assert.match(
    materialFontLink[1],
    /[?&]display=block(?:&|$)/,
    'Material Symbols should use display=block to avoid first-load ligature text'
  )
})
