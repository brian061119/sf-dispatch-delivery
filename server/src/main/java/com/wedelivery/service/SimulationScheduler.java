package com.wedelivery.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时触发 {@link SimulationService#tick()}，让系统在无人打开追踪页时也持续推进：
 * 在途订单按时间推进状态、空闲返站的载具归位、充电中的载具补电后转待命。
 *
 * 此前 tick 只在管理员手动调用 POST /api/dispatch/simulate/tick 或有人轮询追踪时才发生，
 * 无人观看的订单会永远停在 PAID，其载具一直处于「配送中」，管理员看板与推荐可用量都不会变化。
 *
 * 配置：wedelivery.simulation.auto-tick（默认 true）、wedelivery.simulation.tick-ms（默认 10000）。
 * 注意 tick 内返站步长与补电量按「每次 tick = 1 模拟分钟」计算，10 秒一次即约 6 倍速，适合演示。
 * 测试环境在 src/test/resources/config/application.yml 中关闭，避免后台线程改动测试数据。
 */
@Component
@Slf4j
public class SimulationScheduler {

    private final SimulationService simulationService;
    private final boolean enabled;

    public SimulationScheduler(SimulationService simulationService,
                               @Value("${wedelivery.simulation.auto-tick:true}") boolean enabled) {
        this.simulationService = simulationService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${wedelivery.simulation.tick-ms:10000}",
            initialDelayString = "${wedelivery.simulation.tick-ms:10000}")
    public void autoTick() {
        if (!enabled) {
            return;
        }
        try {
            simulationService.tick();
        } catch (RuntimeException e) {
            // 单次失败不应终止定时任务；下一次 tick 会重新计算全部在途订单
            log.warn("Scheduled simulation tick failed: {}", e.getMessage(), e);
        }
    }
}
