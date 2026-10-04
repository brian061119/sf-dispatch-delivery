package com.wedelivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // SimulationScheduler: 定时推进在途订单与载具状态
public class WeDeliveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeDeliveryApplication.class, args);
    }
}
