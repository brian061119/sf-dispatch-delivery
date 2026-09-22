# WeDelivery Web — FlagCamp 前端脚手架

**React 19 + JavaScript + Vite + antd v6 + React-Leaflet + Zustand + axios**

本仓库是前端 lead（Yiting Qi）的**基建交付**：脚手架 + 设计系统 + `api.js` + 后端对齐 + 路由集成。
4 位同学在各自页面填业务即可，公共部分已在 `components/`、`api/`、`store/` 备好。

> 2026-09 从 TypeScript 转为纯 JavaScript（对齐 social-ai 课程栈）。
> 原来的 `types/api.ts` 接口契约改写为 `src/types/api.js` 里的 **JSDoc @typedef** —— 契约不变，只是表达形式换了。
>
> **API 的 URL + method 以团队仓库根目录 `api-contract.md`（2026-09-21 确认版）为准**，`src/types/api.js` 是它的前端镜像。

## 快速开始

```bash
npm install        # 首次
npm run dev        # http://localhost:3000
npm run build      # 生产构建
```

后端默认代理到 `http://localhost:8080`（见 `vite.config.js`）。
改后端地址：`VITE_API_PROXY_TARGET=http://<后端host:port> npm run dev`。

## 目录

| 路径 | 作用 |
|---|---|
| `src/types/api.js` | 全部接口契约（JSDoc @typedef，镜像 api-contract.md）—— 改动需全组+后端同步 |
| `src/lib/http.js` | axios 实例：baseURL + JWT 拦截器 + 401 跳登录 |
| `src/lib/auth.js` | token 存取（localStorage） |
| `src/api/*` | 按契约分组的接口函数（见下表） |
| `src/store/*` | Zustand：`auth` / `wizard` / `orders` |
| `src/components/*` | 设计系统公共组件 |
| `src/pages/*` | 7 个页面，已按 owner 标注 TODO |

## 分工 ↔ 路由 ↔ 接口

| owner | 页面 / 路由 | 用的接口（全部 `/api` 前缀） | 状态 |
|---|---|---|---|
| **Ziyuan Xu** | Login `/login`、Register `/register` | `POST /auth/register`、`POST /auth/login`（+ `logout`/`me`） | 骨架已通 |
| **Zihang Cao** | Dashboard `/dashboard`、History `/orders`、Wizard `/order/new` | `POST /recommendations`、`POST /orders`、`GET /orders`、`GET /stations` | 骨架已通 |
| **Y** | OrderDetail `/order/:orderId` | `GET /orders/:id`、`PATCH /orders/:id/confirm-receipt`、`POST /orders/:id/review` | 骨架已通 |
| **Yuning Zhang** | Tracking `/tracking/:orderId` | `GET /orders/:id/tracking`（5s 轮询） | 骨架已通 |
| **Yiting Qi** | 基建（本文件全部） | 全部 | ✅ |

> **路由↔线框图映射**：线框 04–07（下单四步）合并为单路由 `/order/new` 的内部 4 步；
> 03 Dashboard / 09 History 共用 `OrderCard`；08 Tracking 与详情页通过 `Live tracking →` 互跳，
> **不要各做一版订单页**（详情=静态信息+签收+评价，追踪=实时地图+时间线）。

## 后端要对齐的 3 件事

1. 基础路径 `/api`，返回 JSON，鉴权用 `Authorization: Bearer <token>`。
2. **新增「签收」接口** `POST /api/orders/{id}/confirm`（原 10 端点没有，为 Y 的签收补的）。
3. 枚举字符串保持一致：`VehicleType = ROBOT | DRONE`；`OrderStatus = CREATED | ASSIGNED | PICKED_UP | IN_TRANSIT | DELIVERED | CANCELLED`。

## 公共组件速查

| 组件 | 用途 |
|---|---|
| `<AppHeader/>` | 顶栏（logo/Dashboard/Orders/新建/登出），App.jsx 已挂 |
| `<OrderCard order mini/>` | 订单卡（Dashboard/History 复用） |
| `<StatusBadge status/>` | 状态彩色标签（契约 4 态） |
| `<StatusTimeline status/>` | 三步进度条 + 取消特判（Tracking 用） |
| `<VehicleIcon vehicle/>` | ROBOT/DRONE 图标（向导候选卡用） |
| `<MapView pickup destination vehicle route/>` | 共享 Leaflet 地图（Zihang 选点、Yuning 追踪都用它） |

## 设计系统换肤

所有 antd 组件的配色/圆角集中在 `src/theme.js`。改 `colorPrimary` 一处即全站换肤
（线框图的黑按钮可在此调）。

## 状态怎么用

- `useAuth()` — 登录/注册/登出，刷新不掉线（localStorage 水合）。
- `useWizard()` — 下单草稿；**AI 解析结果用 `prefill()` 灌进来**，向导照原样确认（"AI drafts, wizard verifies"）。
- `useOrders()` — 订单列表，`refresh()` 拉取，Dashboard/History 共用。
