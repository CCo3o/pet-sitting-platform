package com.petsitting;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.order-expiry.enabled",havingValue="true",matchIfMissing=true)
public class OrderExpiryJob {
    private final BookingService service;
    public OrderExpiryJob(BookingService service){this.service=service;}
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void expire(){
        for(var order:service.list("SELECT o.id FROM orders o JOIN slots s ON s.id=o.slot_id WHERE o.workflow_state='PENDING' AND (o.created_at < ? OR s.starts_at <= ?)",java.time.OffsetDateTime.now().minusHours(2),java.time.OffsetDateTime.now()))
            service.expire((String)order.get("ID"));
    }
}
