package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 站点实时信息（需求 2）。
 * 数量的统计口径：按 station_id 归属统计；可调度数额外要求载具处于 IDLE。
 * 同时把「续航 × 最大速度」得出的最大可配送路程做站点级汇总，
 * 供调用方判断「本站能否覆盖到某个送货点」（需求 3 的续航接入）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationAvailabilityDto {

    private Long stationId;
    private String stationCode;
    private String name;

    private Integer maxCapacity;
    /** 物理停驻在本站的载具数（含各状态） */
    private Integer onSiteCount;
    /** 剩余可用容量（可再停泊几位） */
    private Integer capacityRemaining;

    /** 归属本站的无人机总数 */
    private Integer droneCount;
    /** 归属本站的机器人总数 */
    private Integer robotCount;
    /** 可调度无人机数（IDLE 且停驻本站） */
    private Integer droneUnitsAvailable;
    /** 可调度机器人数（IDLE 且停驻本站） */
    private Integer robotUnitsAvailable;

    /** 是否可接单：存在任一类型可调度载具 */
    private Boolean available;

    /** 本站可用无人机的最大可配送路程 km（取本站 IDLE 无人机的最大值） */
    private BigDecimal maxDroneRangeKm;
    /** 本站可用机器人的最大可配送路程 km（取本站 IDLE 机器人的最大值） */
    private BigDecimal maxRobotRangeKm;
}
