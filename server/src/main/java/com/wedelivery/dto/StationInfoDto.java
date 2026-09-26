package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 站点基础信息（需求 1）——名字、地址、联系方式接入到订单系统。
 * 站点编号即 id；最大容量由分类型泊位数派生。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationInfoDto {

    private Long stationId;
    /** 站点编号（字符串形式，等同于 id） */
    private String stationCode;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String contactPhone;

    private Integer totalDroneBays;
    private Integer totalRobotBays;
    /** 派生: 最大容量 = 无人机坪位 + 机器人泊位 */
    private Integer maxCapacity;
}
