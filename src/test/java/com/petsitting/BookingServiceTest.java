package com.petsitting;

import java.time.OffsetDateTime;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@org.springframework.test.context.ActiveProfiles("test")
class BookingServiceTest {
    @Autowired BookingService service;
    @Autowired JdbcTemplate db;
    String pet1, pet3, slot;
    @BeforeEach void setup() {
        db.update("DELETE FROM service_records"); db.update("DELETE FROM orders");
        db.update("DELETE FROM slots"); db.update("DELETE FROM pets");
        pet1 = (String) service.addPet(1, "团子", "每日喂粮").get("ID");
        pet3 = (String) service.addPet(3, "豆豆", "换水").get("ID");
        slot = (String) service.addSlot(2, OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1), 5000).get("ID");
    }
    @Test void concurrentOwnersOnlyOneWins() throws Exception {
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attempt(gate, 1, pet1));
            var second = pool.submit(() -> attempt(gate, 3, pet3));
            gate.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }
    private int attempt(CountDownLatch gate, long actor, String pet) throws Exception {
        gate.await();
        try { service.book(actor, pet, slot, "race"); return 1; }
        catch (ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return 0; }
    }
    @Test void concurrentRetriesReturnSameOrder() throws Exception {
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Object> call = () -> { gate.await(); return service.book(1, pet1, slot, "retry").get("ID"); };
            var first = pool.submit(call); var second = pool.submit(call); gate.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }
    @Test void mismatchedIdempotencyPayloadRejected() {
        service.book(1, pet1, slot, "same");
        assertThatThrownBy(() -> service.book(1, pet1, "different", "same")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void cancellationReleasesSlotAndRetryDoesNotRebook() {
        String order = (String) service.book(1, pet1, slot, "original").get("ID");
        service.transition(1, order, "cancel", "");
        service.book(3, pet3, slot, "next");
        assertThat(service.book(1, pet1, slot, "original").get("STATUS")).isEqualTo("CANCELLED");
        service.transition(1, order, "cancel", "");
        assertThat(service.slots()).isEmpty();
    }
    @Test void roleAndOwnershipEnforced() {
        assertThatThrownBy(() -> service.book(1, pet3, slot, "bad")).isInstanceOf(ResponseStatusException.class);
        String order = (String) service.book(1, pet1, slot, "good").get("ID");
        assertThatThrownBy(() -> service.transition(3, order, "cancel", "")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.transition(1, order, "accept", "")).isInstanceOf(ResponseStatusException.class);
        assertThat(service.orders(3)).isEmpty();
    }
    @Test void completionRequiresAcceptedAndFinishedService() {
        String order = (String) service.book(1, pet1, slot, "flow").get("ID");
        assertThatThrownBy(() -> service.transition(2, order, "complete", "已喂养")).isInstanceOf(ResponseStatusException.class);
        service.transition(2, order, "accept", "");
        assertThatThrownBy(() -> service.transition(2, order, "complete", "已喂养")).isInstanceOf(ResponseStatusException.class);
        db.update("UPDATE slots SET starts_at=?,ends_at=? WHERE id=?", OffsetDateTime.now().minusHours(2), OffsetDateTime.now().minusHours(1), slot);
        service.transition(1,order,"pay","");
        service.transition(2,order,"start","");
        service.record(2,order,"已喂粮并换水",java.util.List.of());
        assertThat(service.transition(2, order, "complete", "已喂粮并换水").get("STATUS")).isEqualTo("WAITING_CONFIRM");
        service.transition(2, order, "complete", "重复提交");
        assertThat(service.transition(1,order,"confirm","").get("STATUS")).isEqualTo("COMPLETED");
        assertThat(service.records(1, order)).hasSize(1);
        assertThatThrownBy(() -> service.transition(1, order, "cancel", "")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void overlappingSitterSlotsRejectedButAdjacentAllowed() {
        var start = (OffsetDateTime) service.slots().getFirst().get("STARTS_AT");
        assertThatThrownBy(() -> service.addSlot(2, start.plusMinutes(30), start.plusHours(2), 100)).isInstanceOf(ResponseStatusException.class);
        service.addSlot(2, start.plusHours(1), start.plusHours(2), 100);
        assertThat(service.slots()).hasSize(2);
    }
}
