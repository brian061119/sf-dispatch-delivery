// ============================================================================
// WeDelivery — 前后端契约 (frontend <-> backend contract)
//
// ⚠️ URL + method 以团队共享仓库里的 api-contract.md（2026-09-21 确认版）为准。
//    本文件是它的前端镜像：字段形状用 JSDoc @typedef 表达，TBD 部分原样标注，
//    不要在代码里发明契约里还没有的字段。
//
// 在别的文件里引用：  /** @type {import('../types/api.js').OrderDetail} */
// ============================================================================

// ---- 枚举 --------------------------------------------------------------------

/** 配送载具。@typedef {'ROBOT'|'DRONE'} VehicleType */

/** 订单状态机（4 态，契约版）。PENDING -> IN_TRANSIT -> DELIVERED；前置态可 -> CANCELLED。
 * 注意：confirm-receipt 会把状态置为 DELIVERED —— "DELIVERED 是已送达还是已签收"
 * 已在交接文档列为待确认项。
 * @typedef {'PENDING'|'IN_TRANSIT'|'DELIVERED'|'CANCELLED'} OrderStatus */

/** 配送优先级。@typedef {'STANDARD'|'EXPRESS'} Priority */

// ---- 基础结构（契约版）---------------------------------------------------------

/**
 * 地址。addressId 非空时后端以它为准（保存的地址）；否则用明细字段。
 * city 固定 San Francisco（课程场景）。lat/lng 可空（地图选点落地前为 null）。
 * @typedef {{ addressId: string|null, line1: string, city: string, zip: string, lat: number|null, lng: number|null }} ContractAddress
 */

/**
 * 包裹。单位契约：重量 kg、尺寸 cm。
 * @typedef {{ description: string, weightKg: number, lengthCm: number|null, widthCm: number|null, heightCm: number|null, fragile: boolean }} ContractPackage
 */

// ---- Auth（Ziyuan Xu）----------------------------------------------------------

/** POST /api/auth/login 请求体。@typedef {{ username: string, password: string }} LoginRequest */

/** 登录响应里的用户对象。@typedef {{ id: string, username: string, email: string }} UserInfo */

/** POST /api/auth/login 响应（200）。@typedef {{ token: string, user: UserInfo }} AuthResponse */

/** POST /api/auth/register 请求体。
 * ⚠️ 契约 TBD：body 未最终定义，先按 {username,password,email} 发，密码策略待团队确认。
 * @typedef {{ username: string, password: string, email?: string }} RegisterRequest */

// ---- Recommendations（Zihang Cao — 推荐）---------------------------------------

/** POST /api/recommendations 请求体。
 * @typedef {{ pickup: ContractAddress, dropoff: ContractAddress, package: ContractPackage, priority: Priority }} RecommendationsRequest */

/** 一个候选配送方案。isFastest/isCheapest 由后端 RecommendationService 算好，前端只展示。
 * availableUnits=0 的前端禁用选择。
 * @typedef {{ candidateId: string, stationId: string, stationName: string, vehicleType: VehicleType, estimatedTimeMinutes: number, estimatedCost: number, availableUnits: number, score: number, isFastest: boolean, isCheapest: boolean }} Candidate */

/** POST /api/recommendations 响应（200）。@typedef {{ candidates: Candidate[] }} RecommendationsResponse */

// ---- Orders（Zihang 创建+列表 / Y 详情+签收+评价）-------------------------------

/** POST /api/orders 请求体（内嵌支付，一次调用完成支付+创建，无回调页）。
 * paymentMethodId：真实实现应是 Stripe Elements 之类的 token；课程 mock 直接发占位串。
 * @typedef {{ candidateId: string, pickup: ContractAddress, dropoff: ContractAddress, package: ContractPackage, priority: Priority, paymentMethodId: string }} CreateOrderRequest */

/** POST /api/orders 响应（201）。
 * ⚠️ 契约 TBD：支付失败 vs 订单本身失败的错误形状未定义，前端需要区分来展示文案。
 * @typedef {{ orderId: string, status: OrderStatus, estimatedTimeMinutes: number, estimatedCost: number }} CreateOrderResponse */

/** GET /api/orders 列表项。@typedef {{ orderId: string, status: OrderStatus, createdAt: string, packageDescription: string, estimatedCost: number }} OrderSummary */

/** GET /api/orders 响应（200）。@typedef {{ orders: OrderSummary[] }} OrderListResponse */

/** GET /api/orders/:orderId 响应。
 * ⚠️ 契约 TBD：只确认是列表项的超集（完整地址/包裹/候选/时间戳），字段未定义。
 * 前端 OrderDetail 页用可选链防御式渲染，契约定了再补全。
 * @typedef {Object} OrderDetail
 * @property {string} orderId
 * @property {OrderStatus} status
 * @property {string} [createdAt]
 * @property {string} [packageDescription]
 * @property {number} [estimatedCost]
 * @property {ContractAddress} [pickup]
 * @property {ContractAddress} [dropoff]
 * @property {ContractPackage} [package]
 * @property {Candidate} [candidate]
 */

/** PATCH /api/orders/:orderId/confirm-receipt 响应（200）。
 * @typedef {{ orderId: string, status: 'DELIVERED' }} ConfirmReceiptResponse */

/** POST /api/orders/:orderId/review 请求体。damageReported=true 即损坏报备（细节写进 comment）。
 * @typedef {{ rating: number, comment: string|null, damageReported: boolean }} ReviewRequest */

/** POST /api/orders/:orderId/review 响应（200）。@typedef {{ orderId: string, reviewId: string }} ReviewResponse */

// ---- Tracking（Yuning Zhang）----------------------------------------------------

/** GET /api/orders/:orderId/tracking 响应（200）— 追踪页 5s 轮询的就是它。
 * 契约无历史轨迹数组；如需 route 折线要向 backend 提需求（待确认项）。
 * @typedef {{ orderId: string, status: OrderStatus, vehicleType: VehicleType, currentLat: number, currentLng: number, estimatedArrival: string }} TrackingResponse */

// ---- Stations -------------------------------------------------------------------

/** GET /api/stations 响应项。⚠️ 契约 TBD（等 StationRepository owner 确认）。
 * @typedef {{ stationId: string, name: string, address?: string, lat?: number, lng?: number }} Station */

// ---- AI 自然语言下单（P1，未入契约）----------------------------------------------

/**
 * ⚠️ 该端点是前端为 P1 功能提议的，不在 api-contract.md 里，需后端认领后才算数。
 * 解析结果 = 未完成的下单草稿，倒进 wizard 由用户核实（"AI drafts, wizard verifies"）。
 * @typedef {{ itemName?: string, weight?: number, fragile?: boolean }} AiParseResponse
 */

export {};
