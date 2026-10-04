// ============================================================================
// Mock backend for local demo — activated with VITE_MOCK=1 (see README).
// Shapes follow api-contract.md. State lives in module memory: created orders,
// signed receipts and the courier's drifting position survive polling within
// one page session, and reset on refresh. NOT for production.
//
// Pricing mirrors the backend formula (RecommendationService):
//   DRONE  base $15.00 + $1.80/km + $2.00/kg
//   ROBOT  base $6.00  + $0.90/km + $0.80/kg
//   OFF_PEAK = BEST_VALUE − 15%, +60 min
//   VIP −10% (role from login: sign in as "vip" to see it)
// so the price in options == the price at pay == the price in history/detail.
// ============================================================================

const hourAgo = (h) => new Date(Date.now() - h * 3600_000).toISOString();
const inMin = (m) => new Date(Date.now() + m * 60_000).toISOString();

// ---- seed data ---------------------------------------------------------------

const orders = [
    { orderId: 'WD-1001', status: 'IN_TRANSIT', detailStatus: 'IN_TRANSIT', trackingCode: 'TC-8ZK2QA', createdAt: hourAgo(1), packageDescription: 'A 2kg book', estimatedCost: 6.5, vehicleType: 'ROBOT' },
    { orderId: 'WD-1002', status: 'PENDING', detailStatus: 'PICKING_UP', trackingCode: 'TC-3NB7XD', createdAt: hourAgo(3), packageDescription: 'Documents envelope', estimatedCost: 4.25, vehicleType: 'ROBOT' },
    { orderId: 'WD-1003', status: 'DELIVERED', detailStatus: 'DELIVERED', trackingCode: 'TC-5RM9PL', createdAt: hourAgo(26), packageDescription: 'Birthday cake (fragile)', estimatedCost: 12.99, vehicleType: 'DRONE' },
    { orderId: 'WD-1004', status: 'CANCELLED', detailStatus: 'CANCELLED', trackingCode: 'TC-1QT4VB', createdAt: hourAgo(30), packageDescription: 'Spare laptop charger', estimatedCost: 5.0, vehicleType: 'ROBOT' },
];
// WD-1002 exercises the internal-state mapping: the real backend reports
// PICKING_UP (vehicle en route to pickup) while the contract status is PENDING.

const details = {
    'WD-1001': {
        orderId: 'WD-1001', status: 'IN_TRANSIT', createdAt: orders[0].createdAt,
        packageDescription: 'A 2kg book', estimatedCost: 6.5,
        pickup: { addressId: null, line1: '500 Howard St', city: 'San Francisco', zip: '94105', lat: 37.7892, lng: -122.3970 },
        dropoff: { addressId: null, line1: 'Mission Dolores Park', city: 'San Francisco', zip: '94114', lat: 37.7596, lng: -122.4269 },
        candidate: { candidateId: 'CAND-BEST_VALUE', vehicleType: 'ROBOT', stationName: 'Station 1 - SF Downtown Hub' },
    },
};

// Same 3 stations as the backend seed (data.sql).
const stations = [
    { stationId: 'st1', name: 'Station 1 - SF Downtown Hub', address: '500 Howard St, San Francisco, CA 94105', lat: 37.7892, lng: -122.3970, robots: 4, drones: 3 },
    { stationId: 'st2', name: 'Station 2 - Sunset District Hub', address: '1900 Irving St, San Francisco, CA 94122', lat: 37.7638, lng: -122.4789, robots: 2, drones: 2 },
    { stationId: 'st3', name: 'Station 3 - Mission District Hub', address: '2400 Mission St, San Francisco, CA 94110', lat: 37.7589, lng: -122.4191, robots: 2, drones: 2 },
];

// Courier position drifts a little on every poll so the tracking map looks live.
const PICKUP_XY = { lat: 37.7892, lng: -122.3970 };
const DROPOFF_XY = { lat: 37.7596, lng: -122.4269 };
const drift = () => {
    const t = (Date.now() / 1000) % 300; // 5-minute loop
    const k = t / 300;
    return {
        lat: PICKUP_XY.lat + (DROPOFF_XY.lat - PICKUP_XY.lat) * k,
        lng: PICKUP_XY.lng + (DROPOFF_XY.lng - PICKUP_XY.lng) * k,
    };
};

const latency = () => new Promise((r) => setTimeout(r, 120));

// ---- pricing (mirror of backend RecommendationService) -------------------------

const PRICING = {
    DRONE: { base: 15.0, perKm: 1.8, perKg: 2.0, speedKmh: 45 },
    ROBOT: { base: 6.0, perKm: 0.9, perKg: 0.8, speedKmh: 20 },
};
const OFF_PEAK_DISCOUNT = 0.15;
const VIP_DISCOUNT = 0.10;

function haversineKm(a, b) {
    const R = 6371;
    const rad = (d) => (d * Math.PI) / 180;
    const dLat = rad(b.lat - a.lat);
    const dLng = rad(b.lng - a.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(h));
}

let sessionRole = 'USER'; // set by login(), read by the pricing path
let lastCandidates = []; // remembered so createOrder charges what was shown

function quote(body) {
    const pickup = body?.pickup ?? {};
    const dropoff = body?.dropoff ?? {};
    const weight = Number(body?.package?.weightKg ?? 1.5);
    const hasCoords = [pickup.lat, pickup.lng, dropoff.lat, dropoff.lng].every((v) => Number.isFinite(Number(v)));
    const routeKm = hasCoords ? haversineKm({ lat: Number(pickup.lat), lng: Number(pickup.lng) }, { lat: Number(dropoff.lat), lng: Number(dropoff.lng) }) * 1.3 : 5.2;
    const vip = sessionRole === 'VIP';
    const nearest = stations[0];

    const build = (type, planType) => {
        const p = PRICING[type];
        const origin = p.base + routeKm * p.perKm + weight * p.perKg;
        const cost = Math.round(origin * (vip ? 1 - VIP_DISCOUNT : 1) * 100) / 100;
        const minutes = Math.max(8, Math.round((routeKm / p.speedKmh) * 60) + 6);
        return { candidateId: `CAND-${planType}`, planType, stationId: nearest.stationId, stationName: nearest.name, vehicleType: type, estimatedTimeMinutes: minutes, estimatedCost: cost, availableUnits: weight > (type === 'DRONE' ? 3 : 15) ? 0 : 3, score: 0, isFastest: false, isCheapest: false };
    };

    const list = [];
    const drone = build('DRONE', 'FASTEST');
    const robot = build('ROBOT', 'BEST_VALUE');
    if (drone.availableUnits > 0) list.push(drone);
    if (robot.availableUnits > 0) list.push(robot);
    if (robot.availableUnits > 0) {
        const eco = { ...robot, candidateId: 'CAND-OFF_PEAK', planType: 'OFF_PEAK', estimatedCost: Math.round(robot.estimatedCost * (1 - OFF_PEAK_DISCOUNT) * 100) / 100, estimatedTimeMinutes: robot.estimatedTimeMinutes + 60 };
        list.push(eco);
    }
    const minCost = Math.min(...list.map((c) => c.estimatedCost), Infinity);
    const minTime = Math.min(...list.map((c) => c.estimatedTimeMinutes), Infinity);
    return list.map((c) => ({ ...c, isCheapest: c.estimatedCost === minCost, isFastest: c.estimatedTimeMinutes === minTime }));
}

// ---- auth (Ziyuan) -------------------------------------------------------------
// Demo accounts: log in as "admin" → ADMIN console, "vip" → VIP pricing,
// anything else → regular USER. (Backend assigns roles server-side.)

export async function login(body) {
    await latency();
    if (!body.username || !body.password) throw new Error('missing credentials');
    const name = body.username.toLowerCase();
    const role = name === 'admin' ? 'ADMIN' : name === 'vip' ? 'VIP' : 'USER';
    sessionRole = role;
    return { token: 'mock-token-' + Date.now(), user: { id: 'u1', username: body.username, email: body.username + '@demo.dev', role } };
}

export async function register(body) {
    await latency();
    sessionRole = 'USER';
    return { token: 'mock-token-' + Date.now(), user: { id: 'u2', username: body.username, email: body.email ?? '', role: 'USER' } };
}

export async function logout() {
    await latency();
    sessionRole = 'USER';
    return {};
}

export async function getMe() {
    await latency();
    return { id: 'u1', username: 'demo', email: 'demo@dispatchdelivery.dev', role: sessionRole };
}

// ---- recommendations + orders (Zihang) ------------------------------------------

export async function getRecommendations(body) {
    await latency();
    const candidates = quote(body);
    lastCandidates = candidates;
    return { candidates };
}

export async function createOrder(body) {
    await latency();
    const orderId = 'WD-' + Math.floor(1000 + Math.random() * 9000);
    const trackingCode = 'TC-' + Math.random().toString(36).slice(2, 8).toUpperCase();
    // Charge the price of the option the user actually selected — never a constant.
    const chosen = lastCandidates.find((c) => c.candidateId === body.candidateId) ?? lastCandidates[0];
    const cost = chosen?.estimatedCost ?? 6.5;
    const summary = {
        orderId, status: 'PENDING', detailStatus: 'PENDING_PAYMENT', trackingCode,
        createdAt: new Date().toISOString(),
        packageDescription: body.package.description, estimatedCost: cost,
        vehicleType: chosen?.vehicleType ?? 'ROBOT',
    };
    orders.unshift(summary);
    details[orderId] = {
        ...summary,
        pickup: body.pickup, dropoff: body.dropoff, package: body.package,
        candidate: { candidateId: body.candidateId, vehicleType: chosen?.vehicleType ?? 'ROBOT', stationName: chosen?.stationName ?? stations[0].name },
    };
    return { orderId, trackingCode, status: 'PENDING', estimatedTimeMinutes: chosen?.estimatedTimeMinutes ?? 35, estimatedCost: cost };
}

export async function getOrders() {
    await latency();
    return { orders: orders.map((o) => ({ ...o })) }; // copies — see getOrder note
}

// ---- detail + receipt + review + cancel (Y) --------------------------------------

export async function getOrder(orderId) {
    await latency();
    // Return COPIES, not the stored references: real HTTP gives you a fresh JSON
    // object every call. Returning the same mutated reference makes React's
    // setState bail out (Object.is equality → no re-render).
    if (details[orderId]) return { ...details[orderId] };
    const s = orders.find((o) => o.orderId === orderId);
    return s ? { ...s } : { orderId, status: 'PENDING' };
}

export async function confirmReceipt(orderId) {
    await latency();
    const s = orders.find((o) => o.orderId === orderId);
    if (s) s.status = 'DELIVERED';
    if (details[orderId]) details[orderId].status = 'DELIVERED';
    return { orderId, status: 'DELIVERED' };
}

export async function submitReview(orderId) {
    await latency();
    return { orderId, reviewId: 'rv-' + Date.now() };
}

// Cancel is CONTRACT-EXTERNAL until the backend ships PATCH /orders/:id/cancel.
export async function cancelOrder(orderId) {
    await latency();
    const s = orders.find((o) => o.orderId === orderId);
    if (!s) throw Object.assign(new Error('order not found'), { status: 404 });
    if (!['PENDING', 'IN_TRANSIT'].includes(s.status)) {
        throw Object.assign(new Error('Only pending or in-transit orders can be cancelled.'), { status: 409 });
    }
    s.status = 'CANCELLED';
    s.detailStatus = 'CANCELLED';
    if (details[orderId]) details[orderId].status = 'CANCELLED';
    return { orderId, status: 'CANCELLED' };
}

// ---- tracking (Yuning) ------------------------------------------------------------

export async function getTracking(orderId) {
    await latency();
    const pos = drift();
    const s = details[orderId] ?? orders.find((o) => o.orderId === orderId);
    const status = s?.status ?? 'IN_TRANSIT';
    return {
        orderId,
        status: status === 'PENDING' ? 'IN_TRANSIT' : status,
        vehicleType: s?.vehicleType ?? 'ROBOT',
        currentLat: pos.lat,
        currentLng: pos.lng,
        estimatedArrival: inMin(12),
        progressPercent: Math.round(((Date.now() / 1000) % 300) / 3 * 10) / 10,
        currentStageDescription: status === 'DELIVERED' ? 'Package delivered' : status === 'CANCELLED' ? 'Delivery cancelled' : 'Vehicle is on the way',
        events: [],
    };
}

// PUBLIC guest lookup by tracking code (mirrors GET /api/tracking/:code).
export async function getTrackingByCode(trackingCode) {
    await latency();
    const hit = orders.find((o) => o.trackingCode === trackingCode);
    if (!hit) throw Object.assign(new Error('tracking code not found'), { status: 404 });
    return getTracking(hit.orderId);
}

// ---- stations / ai / admin -----------------------------------------------------------

export async function getStations() {
    await latency();
    return stations.map((s) => ({ ...s }));
}

// Naive demo parser: pulls "Nkg" as weight, the word "fragile", and treats the
// rest as the item name. The real backend can be an LLM — signature is all we need.
export async function parseOrderText(text) {
    await latency();
    const weightMatch = text.match(/(\d+(?:\.\d+)?)\s*kg/i);
    const fragile = /fragile|易碎/i.test(text);
    const itemName = text
        .replace(/send|from|to|express|standard|please/gi, ' ')
        .replace(/(\d+(?:\.\d+)?)\s*kg/i, ' ')
        .replace(/fragile|易碎/gi, ' ')
        .trim()
        .split(/\s+/)
        .slice(0, 4)
        .join(' ') || text.slice(0, 30);
    return { itemName, weight: weightMatch ? Number(weightMatch[1]) : undefined, fragile };
}

// AI assistant (contract-external POST /api/ai/chat — demo brain lives here).
// Read-only intents only: track by code, quote a price, explain status,
// prefill an order draft. The assistant never writes to the order store.
export async function sendChatMessage({ message }) {
    await latency();
    const text = (message ?? '').trim();
    const lower = text.toLowerCase();

    const codeMatch = text.match(/TC-[A-Z0-9-]{4,}/i);
    if (codeMatch || /where|track|status|arrive|eta/.test(lower)) {
        const code = codeMatch ? codeMatch[0].toUpperCase() : orders[0]?.trackingCode;
        const hit = orders.find((o) => o.trackingCode === code);
        if (hit) {
            return {
                reply: `Order ${hit.orderId} is ${hit.status.replace(/_/g, ' ').toLowerCase()}. Here is the live card — tap it for the full map.`,
                cards: [{ type: 'order', orderId: hit.orderId, trackingCode: hit.trackingCode, status: hit.status, packageDescription: hit.packageDescription, estimatedCost: hit.estimatedCost }],
            };
        }
        return { reply: `I could not find a delivery for that code. Check the 16-character tracking code from the confirmation — or tell me your order number and I will explain how to find it.`, cards: [] };
    }
    if (/price|cost|how much|quote/.test(lower)) {
        const weightMatch = lower.match(/(\d+(?:\.\d+)?)\s*kg/);
        const weight = weightMatch ? Number(weightMatch[1]) : 2;
        const body = { pickup: { lat: 37.7892, lng: -122.3970 }, dropoff: { lat: 37.7596, lng: -122.4269 }, package: { weightKg: weight } };
        const candidates = quote(body);
        return {
            reply: `For a ${weight} kg package across town, current quotes are:`,
            cards: [{ type: 'quote', candidates }],
        };
    }
    if (/cancel/.test(lower)) {
        return { reply: 'You can cancel an order while it is PENDING or IN_TRANSIT from the order detail page (Cancel button). I cannot cancel it for you — writes always need your confirmation in the app.', cards: [] };
    }
    if (/send|deliver|ship|order/.test(lower)) {
        const draft = await parseOrderText(text);
        return {
            reply: 'I drafted the package details from your description. Review and finish it in the order wizard:',
            cards: [{ type: 'prefill', itemName: draft.itemName, weightKg: draft.weight, fragile: draft.fragile }],
            prefill: { pkg: { description: draft.itemName || '', weightKg: draft.weight, fragile: !!draft.fragile } },
        };
    }
    return {
        reply: 'I can help you: ① track a delivery ("where is TC-8ZK2QA?"), ② quote a price ("how much for a 2kg package?"), ③ draft a new order ("send a 3kg box to Mission"), ④ explain cancel/receipt rules. What would you like?',
        cards: [],
    };
}

// Admin console (mirrors GET /api/admin/dashboard → AdminDashboardDto: stations
// carry their vehicles; recentOrders use orderNumber / customerUsername / finalPrice
// and the raw 6-state status, NOT the customer order-list shape).
export async function getAdminDashboard() {
    await latency();
    const now = new Date().toISOString();
    const vehicleStatuses = ['IDLE', 'IN_DELIVERY', 'IDLE', 'CHARGING', 'IDLE', 'FAULT', 'OFFLINE'];
    let nextId = 1;
    const stationDtos = stations.map((s, i) => {
        const vehicles = [];
        for (const [type, count] of [['DRONE', s.drones], ['ROBOT', s.robots]]) {
            for (let n = 1; n <= count; n++) {
                const status = vehicleStatuses[(nextId + i) % vehicleStatuses.length];
                vehicles.push({
                    id: nextId++, vehicleCode: `${type}-${s.stationId.toUpperCase()}-0${n}`, vehicleType: type, status,
                    batteryLevel: status === 'CHARGING' ? 35 : 60 + ((nextId * 7) % 40), maxWeight: type === 'DRONE' ? 3 : 15,
                    currentSpeed: status === 'IN_DELIVERY' ? (type === 'DRONE' ? 45 : 15) : 0,
                    locationCode: status === 'IN_DELIVERY' ? 0 : i + 1, updatedAt: now,
                });
            }
        }
        return {
            stationId: i + 1, stationCode: String(i + 1), name: s.name, address: s.address,
            contactPhone: `(415) 555-010${i + 1}`, latitude: s.lat, longitude: s.lng,
            maxCapacity: 25, totalDroneBays: 8 + i, totalRobotBays: 12 + i, vehicles,
        };
    });
    const all = stationDtos.flatMap((st) => st.vehicles);
    const count = (status) => all.filter((v) => v.status === status).length;
    return {
        stations: stationDtos,
        totalVehicles: all.length, idleVehicles: count('IDLE'), busyVehicles: count('IN_DELIVERY'),
        chargingVehicles: count('CHARGING'), faultVehicles: count('FAULT'), offlineVehicles: count('OFFLINE'),
        recentOrders: orders.slice(0, 5).map((o, i) => ({
            orderNumber: o.orderId, customerUsername: i % 2 ? 'vip_user' : 'normal_user',
            stationName: stations[i % stations.length].name, vehicleCode: `${o.vehicleType}-ST${(i % stations.length) + 1}-01`,
            vehicleType: o.vehicleType, status: o.detailStatus, finalPrice: o.estimatedCost, createdAt: o.createdAt,
        })),
    };
}
