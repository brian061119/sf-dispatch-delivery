package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 站点基础信息导入（需求 1）。站点编号即 id，故 id 为导入主键（幂等 upsert）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationUpsertRequest {

    /** 站点编号（主键，用于幂等 upsert） */
    @NotNull(message = "站点编号(id)不能为空")
    private Long id;

    @NotBlank(message = "站点名称不能为空")
    private String name;

    @NotBlank(message = "站点地址不能为空")
    private String address;

    @NotNull(message = "纬度不能为空")
    private BigDecimal latitude;

    @NotNull(message = "经度不能为空")
    private BigDecimal longitude;

    /** 无人机坪位总数，缺省 10 */
    private Integer totalDroneBays;

    /** 机器人泊位总数，缺省 15 */
    private Integer totalRobotBays;

    /** 联系方式（电话） */
    private String contactPhone;
}
