package com.wedelivery;

import com.wedelivery.service.SimulationScheduler;
import com.wedelivery.service.SimulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 定时模拟：开关生效，单次失败不影响后续 tick。tick 本身的行为见 DispatchSimulationApiTest。 */
@DisplayName("模拟器 · 定时推进")
class SimulationSchedulerTest {

    @Test
    @DisplayName("开启时每次定时触发都会执行一次 tick")
    void enabledSchedulerTicks() {
        SimulationService simulation = mock(SimulationService.class);
        SimulationScheduler scheduler = new SimulationScheduler(simulation, true);

        scheduler.autoTick();
        scheduler.autoTick();

        verify(simulation, times(2)).tick();
    }

    @Test
    @DisplayName("关闭时不执行 tick")
    void disabledSchedulerDoesNothing() {
        SimulationService simulation = mock(SimulationService.class);
        new SimulationScheduler(simulation, false).autoTick();

        verify(simulation, never()).tick();
    }

    @Test
    @DisplayName("某次 tick 抛异常不会向外传播，下一次仍会执行")
    void failedTickDoesNotStopScheduler() {
        SimulationService simulation = mock(SimulationService.class);
        doThrow(new IllegalStateException("Station not found for order")).doReturn(null).when(simulation).tick();
        SimulationScheduler scheduler = new SimulationScheduler(simulation, true);

        scheduler.autoTick();
        scheduler.autoTick();

        verify(simulation, times(2)).tick();
    }
}
