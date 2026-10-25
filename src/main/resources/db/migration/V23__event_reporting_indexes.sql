-- Supports stable manager event-log paging and date-window reporting.
CREATE INDEX IF NOT EXISTS idx_batch_event_report_order
    ON batch_event(batch_id, event_date DESC, created_at DESC, id DESC);
