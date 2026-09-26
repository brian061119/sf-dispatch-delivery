package com.wedelivery.dto;

import com.wedelivery.entity.enums.VehicleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * 机器接入：载具硬件心跳上报的状态与更新信息（需求 4 的「状态与更新」）。
 * 字段均可选，只更新本次实际上报的项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleTelemetryRequest {

    /** 待命 / 配送中 / 充电 / 故障 / 关机 */
    private VehicleStatus status;

    @DecimalMin(value = "0.00", message = "电量不能为负")
    @DecimalMax(value = "100.00", message = "电量不能超过 100")
    private BigDecimal batteryLevel;

    @DecimalMin(value = "0.00", message = "速度不能为负")
    private BigDecimal currentSpeed;

    @DecimalMin(value = "0.01", message = "续航时间必须大于 0")
    private BigDecimal enduranceMinutes;
}
