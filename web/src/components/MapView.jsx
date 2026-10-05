import L from 'leaflet';
import { useEffect } from 'react';
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap, useMapEvents } from 'react-leaflet';

// Shared map — wizard address picker, route preview, live tracking.
//
// Markers are color-coded divIcons (no image assets needed):
//   pickup 🟢 green "P" · destination 🔴 red "D" · stations 🏠 blue "S" · courier 🟠 orange "●"
// fitBounds: when both pickup+destination exist the map reframes so both are
// visible. NO default pin: an invalid/absent address renders no marker at all.

const COLORS = { pickup: '#2f9e44', destination: '#e03131', station: '#1971c2', vehicle: '#f08c00' };

function makeIcon(color, label) {
    return L.divIcon({
        className: '',
        html: `<div style="width:26px;height:26px;border-radius:50% 50% 50% 4px;background:${color};color:#fff;display:flex;align-items:center;justify-content:center;font:700 13px/1 sans-serif;transform:rotate(-45deg);border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.35)"><span style="transform:rotate(45deg)">${label}</span></div>`,
        iconSize: [26, 26],
        iconAnchor: [13, 26],
    });
}

// Live courier marker: emoji per vehicle type (robot/drone) on a white disc,
// falling back to the neutral dot for unknown types.
const VEHICLE_EMOJI = { ROBOT: '🤖', DRONE: '🚁' };
function makeVehicleIcon(type) {
    const emoji = VEHICLE_EMOJI[type] ?? '●';
    return L.divIcon({
        className: '',
        html: `<div style="width:34px;height:34px;border-radius:50%;background:#fff;border:2.5px solid ${COLORS.vehicle};display:flex;align-items:center;justify-content:center;font-size:17px;line-height:1;box-shadow:0 1px 4px rgba(0,0,0,.35)">${emoji}</div>`,
        iconSize: [34, 34],
        iconAnchor: [17, 17],
    });
}
const ICONS = {
    pickup: makeIcon(COLORS.pickup, 'P'),
    destination: makeIcon(COLORS.destination, 'D'),
    station: makeIcon(COLORS.station, 'S'),
};

const toXY = (p) => [Number(p.lat), Number(p.lng)];
const isValidPoint = (p) => p && Number.isFinite(Number(p.lat)) && Number.isFinite(Number(p.lng));

/** Reframe the viewport whenever the visible point set changes. */
function FitBounds({ points }) {
    const map = useMap();
    const key = JSON.stringify(points.filter(isValidPoint).map(toXY));
    useEffect(() => {
        const valid = points.filter(isValidPoint).map(toXY);
        if (!valid.length) return;
        if (valid.length === 1) {
            map.setView(valid[0], 14);
        } else {
            map.fitBounds(L.latLngBounds(valid), { padding: [48, 48] });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [key, map]);
    return null;
}

/** Forward map clicks to the parent (address picking). */
function ClickHandler({ onMapClick }) {
    useMapEvents({ click: (e) => onMapClick?.(e.latlng) });
    return null;
}

export function MapView({
    pickup,
    destination,
    vehicle,
    stations = [],
    route,
    traveled,
    height = 420,
    onMapClick,
}) {
    // Empty-state backdrop is the SF overview — a neutral camera, not a fake pin.
    const center = isValidPoint(pickup) ? toXY(pickup) : [37.7749, -122.4194];
    const fitPoints = [
        ...(isValidPoint(pickup) ? [pickup] : []),
        ...(isValidPoint(destination) ? [destination] : []),
    ];
    return (
        <MapContainer center={center} zoom={12} style={{ height, width: '100%', borderRadius: 8 }}>
            <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <FitBounds points={fitPoints} />
            {onMapClick && <ClickHandler onMapClick={onMapClick} />}
            {stations.filter(isValidPoint).map((s) => (
                <Marker key={s.stationId ?? s.id ?? s.name} position={toXY(s)} icon={ICONS.station}>
                    <Popup>{s.name ?? 'Station'}{s.address ? ` — ${s.address}` : ''}</Popup>
                </Marker>
            ))}
            {isValidPoint(pickup) && (
                <Marker position={toXY(pickup)} icon={ICONS.pickup}>
                    <Popup>Pickup{pickup.line1 ? ` — ${pickup.line1}` : ''}</Popup>
                </Marker>
            )}
            {isValidPoint(destination) && (
                <Marker position={toXY(destination)} icon={ICONS.destination}>
                    <Popup>Destination{destination.line1 ? ` — ${destination.line1}` : ''}</Popup>
                </Marker>
            )}
            {isValidPoint(vehicle) && (
                <Marker position={toXY(vehicle)} icon={makeVehicleIcon(vehicle.type)}>
                    <Popup>{vehicle.code ? `${vehicle.type ?? 'Courier'} · ${vehicle.code}` : 'Courier'}</Popup>
                </Marker>
            )}
            {route && route.length > 1 && (
                <Polyline
                    positions={route.filter(isValidPoint).map(toXY)}
                    pathOptions={{ color: '#74c0fc', weight: 3, dashArray: '6 8' }}
                />
            )}
            {traveled && traveled.length > 1 && (
                <Polyline
                    positions={traveled.filter(isValidPoint).map(toXY)}
                    pathOptions={{ color: '#1971c2', weight: 4 }}
                />
            )}
        </MapContainer>
    );
}
