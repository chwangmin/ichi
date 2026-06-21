import type { AtlasPin } from '@/api/entries'

export const CLUSTER_RADIUS_METERS = 5

export type AtlasCluster = {
  id: string
  lat: number
  lng: number
  items: AtlasPin[]
}

function toRad(value: number) {
  return (value * Math.PI) / 180
}

function distanceMeters(a: Pick<AtlasPin, 'lat' | 'lng'>, b: Pick<AtlasPin, 'lat' | 'lng'>) {
  const earthMeters = 6371000
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const lat1 = toRad(a.lat)
  const lat2 = toRad(b.lat)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2

  return 2 * earthMeters * Math.asin(Math.sqrt(h))
}

function clusterCenter(items: AtlasPin[]) {
  return {
    lat: items.reduce((sum, pin) => sum + pin.lat, 0) / items.length,
    lng: items.reduce((sum, pin) => sum + pin.lng, 0) / items.length,
  }
}

function clusterId(items: AtlasPin[]) {
  return items
    .map((pin) => pin.id)
    .sort()
    .join(':')
}

export function clusterAtlasPins(pins: AtlasPin[]): AtlasCluster[] {
  const clusters: AtlasCluster[] = []

  for (const pin of pins) {
    const cluster = clusters.find((candidate) =>
      candidate.items.some((item) => distanceMeters(item, pin) <= CLUSTER_RADIUS_METERS),
    )

    if (cluster) {
      cluster.items.push(pin)
      const center = clusterCenter(cluster.items)
      cluster.lat = center.lat
      cluster.lng = center.lng
      cluster.id = clusterId(cluster.items)
    } else {
      clusters.push({
        id: pin.id,
        lat: pin.lat,
        lng: pin.lng,
        items: [pin],
      })
    }
  }

  return clusters
}
