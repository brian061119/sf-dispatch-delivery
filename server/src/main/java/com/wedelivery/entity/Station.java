package com.wedelivery.entity;

import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "stations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Station {

    /** 站点编号即主键，由导入方与种子数据显式指定；不要改回 IDENTITY —— 那会丢弃赋入的编号，并让重复导入不断新增行 */
    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "total_drone_bays", nullable = false)
    private Integer totalDroneBays;

    @Column(name = "total_robot_bays", nullable = false)
    private Integer totalRobotBays;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;
}
