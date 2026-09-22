// ============================================================================
// Mock backend for local demo — activated with VITE_MOCK=1 (see README).
// Shapes follow api-contract.md. State lives in module memory: created orders,
// signed receipts and the courier's drifting position survive polling within
// one page session, and reset on refresh. NOT for production.
// ============================================================================

const hourAgo = (h) => new Date(Date.now() - h * 3600_000).toISOString();
const inMin = (m) => new Date(Date.now() + m * 60_000).toISOString();

// ---- seed data ---------------------------------------------------------------

const orders = [
    { orderId: 'WD-1001', status: 'IN_TRANSIT', createdAt: hourAgo(1), packageDescription: 'A 2kg book', estimatedCost: 6.5 },
    { orderId: 'WD-1002', status: 'PENDING', createdAt: hourAgo(3), packageDescription: 'Documents envelope', estimatedCost: 4.25 },
    { orderId: 'WD-1003', status: 'DELIVERED', createdAt: hourAgo(26), packageDescription: 'Birthday cake (fragile)', estimatedCost: 12.99 },
    { orderId: 'WD-1004', status: 'CANCELLED', createdAt: hourAgo(30), packageDescription: 'Spare laptop charger', estimatedCost: 5.0 },
];

const details = {
    'WD-1001': {
        orderId: 'WD-1001', status: 'IN_TRANSIT', createdAt: orders[0].createdAt,
        packageDescription: 'A 2kg book', estimatedCost: 6.5,
        pickup: { addressId: null, line1: '123 Market St', city: 'San Francisco', zip: '94103', lat: 37.7749, lng: -122.4194 },
        dropoff: { addressId: null, line1: '456 Mission St', city: 'San Francisco', zip: '94105', lat: 37.788, lng: -122.398 },
        candidate: { candidateId: 'c1', vehicleType: 'ROBOT', stationName: 'SoMa Station' },
    },
};

const stations = [
    { stationId: 'st1', name: 'SoMa Station', address: '800 Harrison St', lat: 37.774, lng: -122.403, robots: 6, drones: 2 },
    { stationId: 'st2', name: 'Mission Station', address: '2200 Mission St', lat: 37.761, lng: -122.419, robots: 4, drones: 1 },
    { stationId: 'st3', name: 'Embarcadero Station', address: '1 Ferry Building', lat: 37.7955, lng: -122.3937, robots: 3, drones: 3 },
];

// Courier position drifts a little on every poll so the tracking map looks live.
const PICKUP_XY = { lat: 37.7749, lng: -122.4194 };
const DROPOFF_XY = { lat: 37.788, lng: -122.398 };
const drift = () => {
    const t = (Date.now() / 1000) % 300; // 5-minute loop
    const k = t / 300;
    return {
        lat: PICKUP_XY.lat + (DROPOFF_XY.lat - PICKUP_XY.lat) * k,
        lng: PICKUP_XY.lng + (DROPOFF_XY.lng - PICKUP_XY.lng) * k,
    };
};

const latency = () => new Promise((r) => setTimeout(r, 120));

// ---- auth (Ziyuan) -------------------------------------------------------------

export async function login(body) {
    await latency();
    if (!body.username || !body.password) throw new Error('missing credentials');
    return { token: 'mock-token-' + Date.now(), user: { id: 'u1', username: body.username, email: body.username + '@demo.dev' } };
}

export async function register(body) {
    await latency();
    return { token: 'mock-token-' + Date.now(), user: { id: 'u2', username: body.username, email: body.email ?? '' } };
}

export async function logout() {
    await latency();
    return {};
}

export async function getMe() {
    await latency();
    return { id: 'u1', username: 'demo', email: 'demo@wedelivery.dev' };
}

// ---- recommendations + orders (Zihang) ------------------------------------------

export async function getRecommendations() {
    await latency();
    return {
        candidates: [
            { candidateId: 'c-drone', stationId: 'st3', stationName: 'Embarcadero Station', vehicleType: 'DRONE', estimatedTimeMinutes: 18, estimatedCost: 9.8, availableUnits: 3, score: 0.91, isFastest: true, isCheapest: false },
            { candidateId: 'c-robot', stationId: 'st1', stationName: 'SoMa Station', vehicleType: 'ROBOT', estimatedTimeMinutes: 35, estimatedCost: 6.5, availableUnits: 6, score: 0.84, isFastest: false, isCheapest: true },
            { candidateId: 'c-robot2', stationId: 'st2', stationName: 'Mission Station', vehicleType: 'ROBOT', estimatedTimeMinutes: 42, estimatedCost: 7.1, availableUnits: 0, score: 0.6, isFastest: false, isCheapest: false },
        ],
    };
}

export async function createOrder(body) {
    await latency();
    const orderId = 'WD-' + Math.floor(1000 + Math.random() * 9000);
    const summary = {
        orderId, status: 'PENDING', createdAt: new Date().toISOString(),
        packageDescription: body.package.description, estimatedCost: 6.5,
    };
    orders.unshift(summary);
    details[orderId] = {
        ...summary,
        pickup: body.pickup, dropoff: body.dropoff, package: body.package,
        candidate: { candidateId: body.candidateId, vehicleType: 'ROBOT', stationName: 'SoMa Station' },
    };
    return { orderId, status: 'PENDING', estimatedTimeMinutes: 35, estimatedCost: 6.5 };
}

export async function getOrders() {
    await latency();
    return { orders: orders.map((o) => ({ ...o })) }; // copies — see getOrder note
}

// ---- detail + receipt + review (Y) ----------------------------------------------

export async function getOrder(orderId) {
    await latency();
    // Return COPIES, not the stored references: real HTTP gives you a fresh JSON
    // object every call. Returning the same mutated reference makes React's
    // setState bail out (Object.is equality → no re-render) — the receipt button
    // stayed "Confirm receipt" after signing until this was fixed.
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

// ---- tracking (Yuning) ------------------------------------------------------------

export async function getTracking(orderId) {
    await latency();
    const pos = drift();
    const status = details[orderId]?.status ?? orders.find((o) => o.orderId === orderId)?.status ?? 'IN_TRANSIT';
    return {
        orderId,
        status: status === 'PENDING' ? 'IN_TRANSIT' : status,
        vehicleType: 'ROBOT',
        currentLat: pos.lat,
        currentLng: pos.lng,
        estimatedArrival: inMin(12),
    };
}

// ---- stations / ai ------------------------------------------------------------------

export async function getStations() {
    await latency();
    return stations;
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
