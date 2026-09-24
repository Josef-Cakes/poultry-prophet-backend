package com.poultryprophet.realtime;

import com.poultryprophet.alert.Alert;
import com.poultryprophet.alert.dto.AlertEvent;
import com.poultryprophet.analytics.Indicator;
import com.poultryprophet.analytics.dto.IndicatorResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * SDD 3.3 SocketServer / AlertChannelService equivalent. Replaces Socket.IO with STOMP over
 * WebSocket: pushes indicator and alert events to per-farm topics that authorised dashboard
 * clients subscribe to. Must be called inside an open persistence context (lazy access).
 */
@Service
public class RealtimeNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher events;

    public RealtimeNotificationService(SimpMessagingTemplate messagingTemplate,
                                       ApplicationEventPublisher events) {
        this.messagingTemplate = messagingTemplate;
        this.events = events;
    }

    /** SDD 2.1 sequence: "emit indicatorUpdated to subscribed dashboards". */
    public void publishIndicatorUpdated(Indicator indicator) {
        Long farmId = indicator.getBatch().getFarmId();
        events.publishEvent(new IndicatorUpdatedNotification(farmId, IndicatorResponse.from(indicator)));
    }

    /** SDD 3.3 sequence: "publish(alertEvent) -> emit to farm room". */
    public void publishAlertCreated(Alert alert) {
        Long farmId = alert.getBatch().getFarmId();
        events.publishEvent(new AlertCreatedNotification(farmId, AlertEvent.from(alert)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendIndicatorUpdated(IndicatorUpdatedNotification notification) {
        messagingTemplate.convertAndSend(
                "/topic/farms/" + notification.farmId() + "/indicators", notification.payload());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAlertCreated(AlertCreatedNotification notification) {
        messagingTemplate.convertAndSend(
                "/topic/farms/" + notification.farmId() + "/alerts", notification.payload());
    }
}
