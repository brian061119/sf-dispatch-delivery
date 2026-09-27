import { MapContainer, Marker, Polyline, Popup, TileLayer } from 'react-leaflet';
// Shared map. Zihang uses it as the pick-point view in step 04; Yuning uses it
// as the live tracking map in 08. Markers/route are prop-driven so both reuse
// one component. Center defaults to San Francisco (the course scenario).
export function MapView({ pickup, destination, vehicle, route, height = 420, }) {
    const center = pickup ? [pickup.lat, pickup.lng] : [37.7749, -122.4194];
    const toXY = (p) => [p.lat, p.lng];
    return (<MapContainer center={center} zoom={13} style={{ height, width: '100%', borderRadius: 8 }}>
      <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"/>
      {pickup && (<Marker position={toXY(pickup)}>
          <Popup>Pickup</Popup>
        </Marker>)}
      {destination && (<Marker position={toXY(destination)}>
          <Popup>Destination</Popup>
        </Marker>)}
      {vehicle && (<Marker position={toXY(vehicle)}>
          <Popup>Courier</Popup>
        </Marker>)}
      {route && route.length > 1 && <Polyline positions={route.map(toXY)}/>}
    </MapContainer>);
}
