# 前端 → 后端需求清单（2026-09-30，Melody）

> 前端已在 `YitingQi` 分支实现的部分均已标注。以下是**需要后端配合**的改动，按优先级排列。前端在 API 未就绪前都会优雅降级（提示 "not supported yet"，不会报错炸页）。

## P0 — 阻塞演示

### 1. 取消订单端点
- **请求**：`PATCH /api/orders/{orderNumber}/cancel`（需 JWT + 订单归属校验；ADMIN 可代客取消）
- **行为**：仅当内部状态为 `PENDING_PAYMENT / PAID / PICKING_UP / IN_TRANSIT` 时允许取消 → 置 `CANCELLED`，释放已锁定的载具（状态回 IDLE），触发退款（demo 可仅记录）。已在配送后段或 DELIVERED 拒绝。
- **响应**：`{ orderId, status: "CANCELLED" }`；不可取消 → `409`；无权限 → `403`
- **前端现状**：OrderDetail 已有 Cancel 卡片（Popconfirm 二次确认），OrderHistory 每行有 Cancel 链接；404 时提示 "not supported yet"。

### 2. 订单列表返回 `vehicleType`
- **现状**：`GET /api/orders` 只返回 `{orderId, trackingCode, status, detailStatus, createdAt, packageDescription, estimatedCost}`，没有载具类型。
- **问题**：前端历史列表的 Robot/Drone 列无法展示真实载具。
- **请求**：列表项加 `vehicleType: "ROBOT" | "DRONE"`（下单时已锁定载具，数据在 order 记录里）。

## P1 — AI 功能（前端已完成 mock，可随时联调）

### 3. AI 对话端点（建议合并为单端点）
- **请求**：`POST /api/ai/chat`（需 JWT）
- **请求体**：`{ message: string, history: [{ role: "user"|"assistant", content }] }`
- **响应体**：`{ reply: string, cards?: Array, prefill?: { pkg: {...} } }`
- **设计约束**（前端已按此实现）：
  - LLM 通过 function calling 调用**既有端点**（`/api/stations/{id}/availability`、`/api/orders/{no}/tracking`、`/api/recommendations`），不新写查询逻辑
  - 工具执行必须透传当前用户 JWT、按 userId 过滤（防越权读他人订单）
  - 价格永远走 `/api/recommendations`，模型不定价
  - 写操作（如改配送时间）模型只能"提议"，须用户在 UI 二次确认
  - 工具循环上限 5 轮，防死循环
- **前端现状**：全站右下角 AI 浮窗（`components/AiAssistant.jsx`）已实现订单卡/报价卡/预填卡三种结构化回复，mock 可直接演示。原先的 `POST /api/ai/parse` 若被此端点覆盖可废弃（Dashboard 一句话下单可改走 `/ai/chat`）。

### 4. VIP 定价在响应中体现
- **现状**：`POST /api/recommendations` 的 QuoteResponse 有 `isVipUser / vipDiscountRate`，但契约 CandidateDto 只有 `estimatedCost`（已折后价）。
- **请求**：确认契约响应是否保留 `isVipUser` 字段（前端想显示 "VIP −10% applied" 标签）；另外 VIP 如何开通？（付费升级端点 or 后台设置）——前端需要一个 VIP 入口的接口约定。

## P2 — 可延后

### 5. 改配送时间（AI 提议的写操作）
- `PATCH /api/orders/{orderNumber}/reschedule`，仅 PENDING 可改；需动订单模型，建议 Final 之后再做。

### 6. 订单详情契约形状统一（沿用协作文档 B4）
- `GET /api/orders/{id}` 目前返回原始记录（orderNumber / 6 态 status / finalPrice），前端已做 toView 兼容层；统一成契约形状后可删掉兼容层（breaking change，需提前通知）。

---

## 端口说明（对齐用）

| 角色 | 端口 | 说明 |
|---|---|---|
| 后端 Spring Boot | **8080** | `server/src/main/resources/application.yml` |
| 前端 Vite dev | **3000** | `web/vite.config.js`，**不是** CRA 的默认 3000=后端直连模式，也不是 Vite 默认 5173 |
| 联调方式 | Vite proxy | 前端 `/api/**` 由 dev server 代理到 `http://localhost:8080`（`VITE_API_PROXY_TARGET` 可覆盖），所以**前端代码里没有任何硬编码端口**，也不会触发 CORS |
| 后端 CORS | 放开 `*` | `SecurityConfig` 已 `allowedOriginPatterns("*")`，即使不用代理直连 8080 也通 |

如果你们那边文档写的是 5173 或其他端口，以 `web/vite.config.js` 的 3000 为准；前端启动命令：`cd web && npm run dev`（mock 模式 `VITE_MOCK=1 npm run dev`，Windows PowerShell 用 `$env:VITE_MOCK=1; npm run dev`）。
