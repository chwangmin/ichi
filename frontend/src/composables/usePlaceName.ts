import { loadGoogleMaps } from '@/composables/useGoogleMaps'

type AddressComponent = {
  long_name: string
  types: string[]
}

function firstOf(components: AddressComponent[], type: string): string | null {
  return components.find((component) => component.types.includes(type))?.long_name ?? null
}

function normalizeRegion(name: string | null): string | null {
  if (!name) return null
  const aliases: Record<string, string> = {
    서울특별시: '서울시',
    부산광역시: '부산시',
    대구광역시: '대구시',
    인천광역시: '인천시',
    광주광역시: '광주시',
    대전광역시: '대전시',
    울산광역시: '울산시',
  }
  return aliases[name] ?? name
}

export function formatApproxPlaceName(components: AddressComponent[]): string | null {
  const region = normalizeRegion(firstOf(components, 'administrative_area_level_1'))
  const district =
    firstOf(components, 'sublocality_level_1') ??
    firstOf(components, 'administrative_area_level_2') ??
    firstOf(components, 'locality')
  const neighborhood =
    firstOf(components, 'sublocality_level_2') ??
    firstOf(components, 'sublocality_level_3') ??
    firstOf(components, 'neighborhood')

  const parts = [region, district, neighborhood]
    .filter((part): part is string => !!part)
    .filter((part, index, all) => all.indexOf(part) === index)

  return parts.length ? parts.join(' ') : null
}

export async function resolvePlaceName(lat: number, lng: number): Promise<string | null> {
  try {
    await loadGoogleMaps()
    const geocoder = new google.maps.Geocoder()
    const response = await geocoder.geocode({
      location: { lat, lng },
      language: 'ko',
    })
    const result = response.results?.[0]
    if (!result) return null
    return (
      formatApproxPlaceName(result.address_components ?? []) ?? result.formatted_address ?? null
    )
  } catch {
    return null
  }
}
