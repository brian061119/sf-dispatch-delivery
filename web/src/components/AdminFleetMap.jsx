import L from "leaflet";
import { useEffect, useMemo, useState } from "react";
import { MapContainer, Marker, Polyline, Popup, TileLayer, Tooltip, useMap } from "react-leaflet";
import { Badge, Button, Card, Col, Divider, Empty, Progress, Radio, Row, Select, Space, Tag, Typography } from "antd";
import { AimOutlined, ClearOutlined, EyeOutlined, ReloadOutlined, ThunderboltOutlined } from "@ant-design/icons";

const { Title, Text } = Typography;

const STATUS_CONFIG = {
  IDLE: { label: "Idle", color: "#52c41a", antdTag: "success" },
  IN_DELIVERY: { label: "Delivering", color: "#1890ff", antdTag: "processing" },
  CHARGING: { label: "Charging", color: "#faad14", antdTag: "warning" },
  FAULT: { label: "Fault", color: "#ff4d4f", antdTag: "error" },
  OFFLINE: { label: "Offline", color: "#8c8c8c", antdTag: "default" },
};

const VEHICLE_EMOJIS = {
  DRONE: "🚁",
  ROBOT: "🤖",
};

const batteryColor = (pct) => (pct < 30 ? "#ff4d4f" : pct < 60 ? "#faad14" : "#52c41a");

function makeStationIcon(name, vehicleCount, bayCount) {
  return L.divIcon({
    className: "admin-station-marker",
    html: `
      <div style="background:#1971c2;color:#fff;border-radius:18px;padding:4px 10px;display:inline-flex;align-items:center;gap:6px;box-shadow:0 3px 10px rgba(25,113,194,0.35);border:2px solid #fff;white-space:nowrap;font-weight:600;font-size:12px;cursor:pointer;">
        <span style="font-size:14px;">🏠</span>
        <span>${name}</span>
        <span style="background:rgba(255,255,255,0.28);border-radius:10px;padding:1px 6px;font-size:11px;">${vehicleCount}/${bayCount}</span>
      </div>
    `,
    iconSize: [150, 32],
    iconAnchor: [75, 16],
  });
}

function makeVehicleIcon(v, isSelected) {
  const color = STATUS_CONFIG[v.status]?.color || "#1890ff";
  const emoji = VEHICLE_EMOJIS[v.vehicleType] || "●";
  const isDelivering = v.status === "IN_DELIVERY";
  const borderSize = isSelected ? "3.5px" : "2.5px";
  const scale = isSelected ? "transform:scale(1.22);z-index:999;" : "";
  const shadow = isSelected ? `0 0 14px ${color}` : "0 2px 7px rgba(0,0,0,0.35)";
  const pct = Math.round(Number(v.batteryLevel ?? 0));

  return L.divIcon({
    className: "admin-vehicle-marker",
    html: `
      <div style="position:relative;display:inline-flex;flex-direction:column;align-items:center;cursor:pointer;${scale}transition:all 0.2s ease;">
        <div style="width:36px;height:36px;border-radius:50%;background:#fff;border:${borderSize} solid ${color};display:flex;align-items:center;justify-content:center;font-size:18px;box-shadow:${shadow};position:relative;">
          ${emoji}
          ${isDelivering ? `<span style="position:absolute;top:-2px;right:-2px;width:10px;height:10px;background:#1890ff;border-radius:50%;border:2px solid #fff;"></span>` : ""}
        </div>
        <div style="background:rgba(0,0,0,0.8);color:#fff;border-radius:4px;padding:1px 5px;font-size:10px;font-weight:700;margin-top:2px;white-space:nowrap;box-shadow:0 1px 3px rgba(0,0,0,0.25);">
          ${pct}%
        </div>
      </div>
    `,
    iconSize: [36, 52],
    iconAnchor: [18, 26],
  });
}

function makeWaypointIcon(label, color) {
  return L.divIcon({
    className: "admin-waypoint-marker",
    html: `
      <div style="width:28px;height:28px;border-radius:50%;background:${color};color:#fff;display:flex;align-items:center;justify-content:center;font-weight:bold;font-size:13px;border:2.5px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,0.35);">
        ${label}
      </div>
    `,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
}

function MapViewController({ flyToCoords }) {
  const map = useMap();
  useEffect(() => {
    if (flyToCoords) {
      map.flyTo(flyToCoords, 15, { duration: 1.2 });
    }
  }, [flyToCoords, map]);
  return null;
}

export function AdminFleetMap({ stations = [], onRefresh, refreshing }) {
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [selectedStationId, setSelectedStationId] = useState("ALL");
  const [selectedVehicle, setSelectedVehicle] = useState(null);
  const [flyToCoords, setFlyToCoords] = useState(null);

  // Flatten all vehicles across stations
  const allVehicles = useMemo(() => {
    return stations.flatMap((s) => s.vehicles ?? []);
  }, [stations]);

  // Filtered vehicles for map display
  const filteredVehicles = useMemo(() => {
    return allVehicles.filter((v) => {
      if (statusFilter !== "ALL" && v.status !== statusFilter) return false;
      if (typeFilter !== "ALL" && v.vehicleType !== typeFilter) return false;
      if (selectedStationId !== "ALL" && v.stationId !== selectedStationId) return false;
      return true;
    });
  }, [allVehicles, statusFilter, typeFilter, selectedStationId]);

  // Keep selected vehicle fresh when parent polls new data
  useEffect(() => {
    if (selectedVehicle) {
      const updated = allVehicles.find((v) => v.id === selectedVehicle.id);
      if (updated) setSelectedVehicle(updated);
    }
  }, [allVehicles, selectedVehicle]);

  // Counts for filters
  const counts = useMemo(() => {
    return {
      total: allVehicles.length,
      delivering: allVehicles.filter((v) => v.status === "IN_DELIVERY").length,
      idle: allVehicles.filter((v) => v.status === "IDLE").length,
      charging: allVehicles.filter((v) => v.status === "CHARGING").length,
      fault: allVehicles.filter((v) => v.status === "FAULT").length,
      offline: allVehicles.filter((v) => v.status === "OFFLINE").length,
      drones: allVehicles.filter((v) => v.vehicleType === "DRONE").length,
      robots: allVehicles.filter((v) => v.vehicleType === "ROBOT").length,
    };
  }, [allVehicles]);

  const activeOrder = selectedVehicle?.activeOrder;
  const hasRoute =
    selectedVehicle?.status === "IN_DELIVERY" &&
    activeOrder &&
    activeOrder.pickupLat &&
    activeOrder.pickupLng &&
    activeOrder.dropoffLat &&
    activeOrder.dropoffLng &&
    selectedVehicle.currentLat &&
    selectedVehicle.currentLng;

  const handleSelectVehicle = (vehicle) => {
    setSelectedVehicle(vehicle);
    if (vehicle.currentLat && vehicle.currentLng) {
      setFlyToCoords([Number(vehicle.currentLat), Number(vehicle.currentLng)]);
    }
  };

  const handleFocusStation = (stationId) => {
    setSelectedStationId(stationId);
    if (stationId === "ALL") {
      setFlyToCoords([37.7749, -122.4194]);
    } else {
      const s = stations.find((st) => st.stationId === stationId);
      if (s && s.latitude && s.longitude) {
        setFlyToCoords([Number(s.latitude), Number(s.longitude)]);
      }
    }
  };

  return (
    <div style={{ position: "relative" }}>
      {/* 1. Control & Filter Toolbar */}
      <Card
        size="small"
        style={{
          borderRadius: 12,
          marginBottom: 16,
          background: "#ffffff",
          boxShadow: "0 2px 8px rgba(0,0,0,0.06)",
        }}
      >
        <Row justify="space-between" align="middle" gutter={[16, 12]}>
          <Col xs={24} lg={16}>
            <Space wrap size={[16, 8]}>
              <Space size={6}>
                <Text strong style={{ fontSize: 13 }}>Status:</Text>
                <Radio.Group
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  size="small"
                  buttonStyle="solid"
                >
                  <Radio.Button value="ALL">All ({counts.total})</Radio.Button>
                  <Radio.Button value="IN_DELIVERY">
                    <span style={{ color: STATUS_CONFIG.IN_DELIVERY.color }}>●</span> Delivering ({counts.delivering})
                  </Radio.Button>
                  <Radio.Button value="IDLE">
                    <span style={{ color: STATUS_CONFIG.IDLE.color }}>●</span> Idle ({counts.idle})
                  </Radio.Button>
                  <Radio.Button value="CHARGING">
                    <span style={{ color: STATUS_CONFIG.CHARGING.color }}>●</span> Charging ({counts.charging})
                  </Radio.Button>
                  <Radio.Button value="FAULT">
                    <span style={{ color: STATUS_CONFIG.FAULT.color }}>●</span> Fault ({counts.fault})
                  </Radio.Button>
                  <Radio.Button value="OFFLINE">Offline ({counts.offline})</Radio.Button>
                </Radio.Group>
              </Space>

              <Space size={6}>
                <Text strong style={{ fontSize: 13 }}>Vehicle:</Text>
                <Radio.Group
                  value={typeFilter}
                  onChange={(e) => setTypeFilter(e.target.value)}
                  size="small"
                  buttonStyle="solid"
                >
                  <Radio.Button value="ALL">All</Radio.Button>
                  <Radio.Button value="DRONE">🚁 Drone ({counts.drones})</Radio.Button>
                  <Radio.Button value="ROBOT">🤖 Robot ({counts.robots})</Radio.Button>
                </Radio.Group>
              </Space>
            </Space>
          </Col>

          <Col xs={24} lg={8} style={{ textAlign: "right" }}>
            <Space wrap>
              <Select
                value={selectedStationId}
                onChange={handleFocusStation}
                size="small"
                style={{ width: 190 }}
                options={[
                  { label: "📍 View All SF Hubs", value: "ALL" },
                  ...stations.map((s) => ({
                    label: `🏠 ${s.name.replace(/^Station \d+ - /, "")}`,
                    value: s.stationId,
                  })),
                ]}
              />
              {selectedVehicle && (
                <Button
                  size="small"
                  icon={<ClearOutlined />}
                  onClick={() => setSelectedVehicle(null)}
                >
                  Clear Selection
                </Button>
              )}
              {onRefresh && (
                <Button
                  size="small"
                  icon={<ReloadOutlined />}
                  loading={refreshing}
                  onClick={onRefresh}
                >
                  Refresh
                </Button>
              )}
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 2. Interactive Map Container with Overlay Panels */}
      <div
        style={{
          position: "relative",
          height: 640,
          borderRadius: 14,
          overflow: "hidden",
          border: "1px solid #e8e8e8",
          boxShadow: "0 4px 16px rgba(0,0,0,0.08)",
        }}
      >
        <MapContainer
          center={[37.7749, -122.4194]}
          zoom={13}
          style={{ width: "100%", height: "100%" }}
          scrollWheelZoom={true}
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          <MapViewController flyToCoords={flyToCoords} />

          {/* Station Markers */}
          {stations.map((s) => {
            if (!s.latitude || !s.longitude) return null;
            const sVehicles = s.vehicles ?? [];
            const bayCount = s.maxCapacity ?? (s.totalDroneBays ?? 0) + (s.totalRobotBays ?? 0);
            return (
              <Marker
                key={`station-${s.stationId}`}
                position={[Number(s.latitude), Number(s.longitude)]}
                icon={makeStationIcon(s.name.replace(/^Station \d+ - /, ""), sVehicles.length, bayCount)}
                eventHandlers={{
                  click: () => handleFocusStation(s.stationId),
                }}
              >
                <Tooltip direction="top" offset={[0, -10]}>
                  <div>
                    <b>{s.name}</b>
                    <br />
                    <span>Address: {s.address}</span>
                    <br />
                    <span>Phone: {s.contactPhone || "N/A"}</span>
                    <br />
                    <span>Bay Occupancy: {sVehicles.length} / {bayCount}</span>
                  </div>
                </Tooltip>
              </Marker>
            );
          })}

          {/* Vehicle Markers */}
          {filteredVehicles.map((v) => {
            if (!v.currentLat || !v.currentLng) return null;
            const isSelected = selectedVehicle?.id === v.id;
            const pct = Math.round(Number(v.batteryLevel ?? 0));
            const statusCfg = STATUS_CONFIG[v.status] || STATUS_CONFIG.IDLE;

            return (
              <Marker
                key={`vehicle-${v.id}`}
                position={[Number(v.currentLat), Number(v.currentLng)]}
                icon={makeVehicleIcon(v, isSelected)}
                eventHandlers={{
                  click: () => handleSelectVehicle(v),
                }}
              >
                <Tooltip direction="top" offset={[0, -14]}>
                  <div style={{ fontSize: 12, minWidth: 140 }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
                      <b>{VEHICLE_EMOJIS[v.vehicleType]} {v.vehicleCode}</b>
                      <Tag color={statusCfg.antdTag} style={{ marginRight: 0 }}>{statusCfg.label}</Tag>
                    </div>
                    <div>🔋 Battery: <b>{pct}%</b></div>
                    {Number(v.currentSpeed) > 0 && <div>⚡ Speed: <b>{Number(v.currentSpeed).toFixed(0)} km/h</b></div>}
                    <div style={{ color: "#8c8c8c", fontSize: 11, marginTop: 2 }}>Click to inspect & track route</div>
                  </div>
                </Tooltip>
              </Marker>
            );
          })}

          {/* Active Delivery Route Polyline and Waypoints (Only shown when vehicle is clicked) */}
          {hasRoute && (
            <>
              {/* Pickup Waypoint */}
              <Marker
                position={[Number(activeOrder.pickupLat), Number(activeOrder.pickupLng)]}
                icon={makeWaypointIcon("P", "#2f9e44")}
              >
                <Popup>
                  <b>🟢 Pickup Location</b>
                  <div>{activeOrder.pickupAddress}</div>
                </Popup>
              </Marker>

              {/* Dropoff Waypoint */}
              <Marker
                position={[Number(activeOrder.dropoffLat), Number(activeOrder.dropoffLng)]}
                icon={makeWaypointIcon("D", "#e03131")}
              >
                <Popup>
                  <b>🔴 Dropoff Destination</b>
                  <div>{activeOrder.dropoffAddress}</div>
                </Popup>
              </Marker>

              {/* Covered segment: Pickup -> Current Vehicle */}
              <Polyline
                positions={[
                  [Number(activeOrder.pickupLat), Number(activeOrder.pickupLng)],
                  [Number(selectedVehicle.currentLat), Number(selectedVehicle.currentLng)],
                ]}
                color="#52c41a"
                weight={4}
                opacity={0.85}
              />

              {/* Remaining segment: Current Vehicle -> Dropoff Destination */}
              <Polyline
                positions={[
                  [Number(selectedVehicle.currentLat), Number(selectedVehicle.currentLng)],
                  [Number(activeOrder.dropoffLat), Number(activeOrder.dropoffLng)],
                ]}
                color="#1890ff"
                weight={4}
                dashArray="8, 8"
                opacity={0.9}
              />
            </>
          )}
        </MapContainer>

        {/* 3. Floating Telemetry Inspector Card (Pinned on right side when a vehicle is selected) */}
        {selectedVehicle && (
          <Card
            title={
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <Space size={8}>
                  <span style={{ fontSize: 20 }}>{VEHICLE_EMOJIS[selectedVehicle.vehicleType]}</span>
                  <div>
                    <div style={{ fontWeight: "bold", fontSize: 15 }}>{selectedVehicle.vehicleCode}</div>
                    <div style={{ fontSize: 11, color: "#8c8c8c" }}>{selectedVehicle.vehicleType}</div>
                  </div>
                </Space>
                <Tag color={STATUS_CONFIG[selectedVehicle.status]?.antdTag}>
                  {STATUS_CONFIG[selectedVehicle.status]?.label ?? selectedVehicle.status}
                </Tag>
              </div>
            }
            extra={
              <Button
                type="text"
                size="small"
                icon={<ClearOutlined />}
                onClick={() => setSelectedVehicle(null)}
              />
            }
            style={{
              position: "absolute",
              top: 16,
              right: 16,
              width: 340,
              zIndex: 1000,
              borderRadius: 12,
              background: "rgba(255, 255, 255, 0.96)",
              backdropFilter: "blur(8px)",
              boxShadow: "0 6px 20px rgba(0,0,0,0.18)",
            }}
            bodyStyle={{ padding: 14 }}
          >
            {/* Battery Level */}
            <div style={{ marginBottom: 12 }}>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>Battery Telemetry</Text>
                <Text strong style={{ color: batteryColor(Math.round(Number(selectedVehicle.batteryLevel ?? 0))) }}>
                  {Math.round(Number(selectedVehicle.batteryLevel ?? 0))}%
                </Text>
              </div>
              <Progress
                percent={Math.round(Number(selectedVehicle.batteryLevel ?? 0))}
                size="small"
                strokeColor={batteryColor(Math.round(Number(selectedVehicle.batteryLevel ?? 0)))}
              />
            </div>

            {/* Quick Specs Grid */}
            <Row gutter={[8, 8]} style={{ marginBottom: 12, background: "#f8f9fa", padding: 8, borderRadius: 8 }}>
              <Col span={12}>
                <div style={{ fontSize: 11, color: "#8c8c8c" }}>Current Speed</div>
                <div style={{ fontWeight: 600 }}>{Number(selectedVehicle.currentSpeed ?? 0).toFixed(0)} km/h</div>
              </Col>
              <Col span={12}>
                <div style={{ fontSize: 11, color: "#8c8c8c" }}>Cruise Speed</div>
                <div style={{ fontWeight: 600 }}>{Number(selectedVehicle.cruiseSpeed ?? 0).toFixed(0)} km/h</div>
              </Col>
              <Col span={12}>
                <div style={{ fontSize: 11, color: "#8c8c8c" }}>Max Payload</div>
                <div style={{ fontWeight: 600 }}>{Number(selectedVehicle.maxWeight ?? 0)} kg</div>
              </Col>
              <Col span={12}>
                <div style={{ fontSize: 11, color: "#8c8c8c" }}>Station</div>
                <div style={{ fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  {selectedVehicle.stationName ? selectedVehicle.stationName.replace(/^Station \d+ - /, "") : "At Station"}
                </div>
              </Col>
            </Row>

            {/* Active Delivery Order Section (If In Delivery) */}
            {activeOrder ? (
              <div style={{ background: "#e6f7ff", border: "1px solid #91d5ff", borderRadius: 8, padding: 10 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
                  <Text strong style={{ color: "#096dd9", fontSize: 13 }}>🚚 Active Shipment</Text>
                  <Tag color="blue" style={{ marginRight: 0 }}>{activeOrder.status}</Tag>
                </div>
                <div style={{ fontSize: 12, marginBottom: 4 }}>
                  <span style={{ color: "#8c8c8c" }}>Order: </span>
                  <b>{activeOrder.orderNumber}</b>
                </div>
                <div style={{ fontSize: 12, marginBottom: 4 }}>
                  <span style={{ color: "#8c8c8c" }}>Customer: </span>
                  <span>{activeOrder.customerUsername}</span>
                </div>
                <div style={{ fontSize: 11, margin: "6px 0", paddingLeft: 6, borderLeft: "2px solid #1890ff" }}>
                  <div>🟢 <b>From:</b> {activeOrder.pickupAddress}</div>
                  <div style={{ marginTop: 2 }}>🔴 <b>To:</b> {activeOrder.dropoffAddress}</div>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "#595959", marginTop: 6 }}>
                  <span>Weight: {activeOrder.packageWeight ? `${activeOrder.packageWeight} kg` : "—"}</span>
                  <span>Fare: ${Number(activeOrder.finalPrice ?? 0).toFixed(2)}</span>
                </div>
              </div>
            ) : (
              <div style={{ textAlign: "center", padding: "8px 0", color: "#8c8c8c", fontSize: 12 }}>
                {selectedVehicle.status === "CHARGING" ? "⚡ Recharging battery at dock bay" : "💤 Vehicle idle & ready for dispatch"}
              </div>
            )}

            <div style={{ marginTop: 12, display: "flex", justifyContent: "flex-end" }}>
              <Button
                size="small"
                type="link"
                icon={<AimOutlined />}
                onClick={() => setFlyToCoords([Number(selectedVehicle.currentLat), Number(selectedVehicle.currentLng)])}
              >
                Center Vehicle
              </Button>
            </div>
          </Card>
        )}

        {/* 4. Bottom Quick-Stats Bar on Map */}
        <div
          style={{
            position: "absolute",
            bottom: 14,
            left: 14,
            zIndex: 1000,
            background: "rgba(255, 255, 255, 0.94)",
            backdropFilter: "blur(6px)",
            padding: "6px 14px",
            borderRadius: 8,
            boxShadow: "0 2px 8px rgba(0,0,0,0.12)",
            fontSize: 12,
            display: "flex",
            gap: 14,
            alignItems: "center",
          }}
        >
          <span style={{ color: "#8c8c8c" }}>Live Fleet:</span>
          <span><b>{filteredVehicles.length}</b> visible</span>
          <span>🟢 <b>{counts.idle}</b> Idle</span>
          <span>🔵 <b>{counts.delivering}</b> Delivering</span>
          <span>🟡 <b>{counts.charging}</b> Charging</span>
        </div>
      </div>
    </div>
  );
}
