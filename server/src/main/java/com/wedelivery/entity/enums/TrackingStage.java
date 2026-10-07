package com.wedelivery.entity.enums;

public enum TrackingStage {
    TO_PICKUP,
    TO_DROPOFF,
    RETURNING,
    COMPLETED,
    CANCELLED,
    // Operational (non-route) events. These are NOT milestones: an order can
    // be modified/confirmed multiple times, and uq_tracking_event_order_stage
    // (order_id, stage) must stay unique for the four route milestones above,
    // so operational events get their own values. Keep them LAST — TrackingService
    // compares stage ordinals to backfill milestones.
    ORDER_UPDATED,
    DELIVERY_CONFIRMED
}
