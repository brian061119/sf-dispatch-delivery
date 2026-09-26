package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * demo 模拟器一次推进的结果快照，替代真实机器/地图心跳。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationTickResponse {

    private LocalDateTime tickAt;
    /** 本次位置发生推进的载具数（配送中） */
    private Integer movedVehicles;
    /** 本次完成补电转为待命的载具数 */
    private Integer chargedVehicles;
    /** 本次无订单在途、自动返站并归位的载具数 */
    private Integer returnedVehicles;
    /** 推进后全部载具的实时信息快照 */
    private List<VehicleRealtimeDto> vehicles;
}
