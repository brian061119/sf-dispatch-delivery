package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 地图接入：上报载具实时经纬度（需求 4 的「位置」）。
 * 位置编码（0=不在任何站点 / 1、2、3=位于对应站点）由后端按与站点的距离推导，不由地图方传入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleLocationRequest {

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    @DecimalMin(value = "0.00", message = "速度不能为负")
    private BigDecimal currentSpeed;
}
