package com.petsitting;

import com.petsitting.booking.*;
import com.petsitting.directory.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;

/** Existing API adapter. Transactions belong to domain services. */
@Service
public class BookingService {
    private final BookingRepository repository;
    private final DirectoryService directory;
    private final SlotService slots;
    private final OrderService orders;
    private final FulfillmentService fulfillment;
    private final FeedbackService feedback;
    private final AdminStatsService administration;
    public BookingService(BookingRepository repository,DirectoryService directory,SlotService slots,OrderService orders,FulfillmentService fulfillment,FeedbackService feedback,AdminStatsService administration) {
        this.repository=repository;this.directory=directory;this.slots=slots;this.orders=orders;this.fulfillment=fulfillment;this.feedback=feedback;this.administration=administration;
    }
    public static OffsetDateTime time(Object value) {
        if(value instanceof OffsetDateTime t) return t;
        if(value instanceof Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
        if(value instanceof LocalDateTime t) return t.atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(value.toString());
    }
    public Map<String,Object> one(String sql,Object... args) { return repository.one(sql,args); }
    public List<Map<String,Object>> list(String sql,Object... args) { return repository.list(sql,args); }
    public void require(boolean condition,String message) { BookingRepository.require(condition,message); }
    public Map<String,Object> addPet(long actor,String name,String notes) { return directory.savePet(actor,null,new DirectoryController.Pet(name,notes,"CAT","",0)); }
    public List<Map<String,Object>> pets(long actor) { return directory.pets(actor); }
    public Map<String,Object> addSlot(long actor,OffsetDateTime start,OffsetDateTime end,int price) { return slots.addSlot(actor,start,end,price); }
    public List<Map<String,Object>> slots() { return slots.slots(); }
    public List<Map<String,Object>> ownSlots(long actor) { return slots.ownSlots(actor); }
    public void deleteSlot(long actor,String slot) { slots.deleteSlot(actor,slot); }
    public Map<String,Object> book(long actor,String pet,String slot,String key) { return orders.book(actor,pet,slot,key); }
    public Map<String,Object> book(long actor,String pet,String slot,String address,String key) { return orders.book(actor,pet,slot,address,key); }
    public List<Map<String,Object>> orders(long actor) { return orders.orders(actor); }
    public Map<String,Object> detail(long actor,String orderId) { return orders.detail(actor,orderId); }
    public void authorize(long actor,Map<String,Object> order) { orders.authorize(actor,order); }
    public Map<String,Object> transition(long actor,String orderId,String action,String note) { return orders.transition(actor,orderId,action,note); }
    public void expire(String orderId) { orders.expire(orderId); }
    public Map<String,Object> record(long actor,String orderId,String note,List<String> photos) { return fulfillment.record(actor,orderId,note,photos); }
    public List<Map<String,Object>> records(long actor,String orderId) { return fulfillment.records(actor,orderId); }
    public Map<String,Object> review(long actor,String orderId,int rating,String content) { return feedback.review(actor,orderId,rating,content); }
    public List<Map<String,Object>> reviews(long actor,String orderId) { return feedback.reviews(actor,orderId); }
    public Map<String,Object> complain(long actor,String orderId,String content) { return feedback.complain(actor,orderId,content); }
    public List<Map<String,Object>> complaints(long actor) { return feedback.complaints(actor); }
    public Map<String,Object> resolve(long actor,String complaint,String resolution) { return feedback.resolve(actor,complaint,resolution); }
    public Map<String,Object> stats(long actor) { return administration.stats(actor); }
}
