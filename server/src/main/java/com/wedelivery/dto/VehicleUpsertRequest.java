package com.wedelivery.dto;

import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 机器基础信息导入（需求 3）。vehicleCode 为导入主键（幂等 upsert）。
 * 能力字段留空时按载具类型的出厂基线兜底（见 VehicleType）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleUpsertRequest {

    @NotBlank(message = "载具编号不能为空")
    private String vehicleCode;

    @NotNull(message = "载具类型不能为空")
    private VehicleType vehicleType;

    @NotNull(message = "所属站点不能为空")
    private Long stationId;

    /** 最大载重 kg */
    private BigDecimal maxWeight;

    /** 最大容积 m³ */
    private BigDecimal maxVolume;

    /** 默认最大速度 km/h */
    private BigDecimal cruiseSpeed;

    /** 满电续航时间 分钟 */
    private BigDecimal enduranceMinutes;

    /** 初始状态，缺省 IDLE（待命） */
    private VehicleStatus status;

    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal batteryLevel;
}
