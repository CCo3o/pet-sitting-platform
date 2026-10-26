package com.petsitting;

import com.petsitting.auth.AuthService;
import com.petsitting.directory.*;
import com.petsitting.agent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:features;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","app.uploads-dir=target/test-uploads"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class PlatformFeaturesTest {
    @Autowired BookingService bookings;@Autowired DirectoryService directory;@Autowired AuthService auth;
    @Autowired DraftService drafts;@Autowired AgentService agent;@Autowired JdbcTemplate db;
    @Autowired AgentQuotaService quota;
    @Autowired RoleAgentService roleAgent;
    @Autowired AgentTraceService agentTraces;
    @Test void roleWorkspacesHaveIsolatedSessionsAndTools(){
        var f=fixture();String sid=(String)agent.session(f.sitter).get("ID");
        assertThat(agent.sessions(f.sitter)).anyMatch(s->sid.equals(s.get("ID")));
        assertThatThrownBy(()->agent.get(f.owner,sid)).isInstanceOf(ResponseStatusException.class);
        assertThat(roleAgent.overview(f.sitter)).containsKey("active");
        assertThat(roleAgent.overview(4)).containsKey("openComplaints");
        assertThatThrownBy(()->roleAgent.complaints(f.sitter)).isInstanceOf(ResponseStatusException.class);
        var tools=new RoleAgentTools(f.sitter,sid,agentTraces.begin(f.sitter,sid),"SITTER",roleAgent,agentTraces);
        assertThat(tools.tools()).isInstanceOf(RoleAgentTools.Sitter.class);
        assertThatThrownBy(()->((RoleAgentTools.Sitter)tools.tools()).prepareServiceRecord("guessed","fact")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void fulfillmentDraftRequiresConfirmationAndReplaysOnlyOnce(){
        var f=fixture();String order=book(f);inProgress(f,order);
        String sid=(String)agent.session(f.sitter).get("ID");
        var tools=(RoleAgentTools.Sitter)new RoleAgentTools(f.sitter,sid,agentTraces.begin(f.sitter,sid),"SITTER",roleAgent,agentTraces).tools();
        tools.inspectOrder(order);var draft=tools.prepareServiceRecord(order,"已按主人要求换水，食盆剩余少量粮。");
        assertThat(bookings.records(f.sitter,order)).isEmpty();
        String id=(String)draft.get("ID");
        assertThatThrownBy(()->roleAgent.confirm(f.owner,id,"test")).isInstanceOf(ResponseStatusException.class);
        roleAgent.confirm(f.sitter,id,"已核实：完成换水。");roleAgent.confirm(f.sitter,id,"重复点击");
        assertThat(bookings.records(f.sitter,order)).hasSize(1).first().satisfies(r->assertThat(r.get("NOTE")).isEqualTo("已核实：完成换水。"));
    }
    @Test void complaintDraftChecksExpiryAndCurrentState(){
        var f=fixture();String order=book(f);String complaint=(String)bookings.complain(f.owner,order,"请求核实服务时间").get("ID");
        String sid=(String)agent.session(4).get("ID");
        var tools=(RoleAgentTools.Admin)new RoleAgentTools(4,sid,agentTraces.begin(4,sid),"ADMIN",roleAgent,agentTraces).tools();
        tools.inspectComplaint(complaint);var draft=tools.prepareResolution(complaint,"建议核实时间，暂无履约证据。");String id=(String)draft.get("ID");
        assertThat(bookings.one("SELECT status FROM complaints WHERE id=?",complaint).get("STATUS")).isEqualTo("OPEN");
        db.update("UPDATE agent_action_drafts SET expires_at=? WHERE id=?",OffsetDateTime.now().minusMinutes(1),id);
        assertThatThrownBy(()->roleAgent.confirm(4,id,"核实完成")).isInstanceOf(ResponseStatusException.class);
        var second=tools.prepareResolution(complaint,"经人工核实后处理");bookings.resolve(4,complaint,"通过普通页面处理");
        assertThatThrownBy(()->roleAgent.confirm(4,(String)second.get("ID"),"不能覆盖已有结果")).isInstanceOf(ResponseStatusException.class);
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;
    String name(){return "u_"+UUID.randomUUID().toString().replace("-","").substring(0,20);}
    long user(String role){return auth.register(name(),"TestPass123!","测试用户",role).user().id();}
    record Fixture(long owner,long sitter,String pet,String address,String slot){}
    Fixture fixture(){
        long owner=user("OWNER"),sitter=user("SITTER");
        directory.saveProfile(sitter,new DirectoryController.Profile("喂粮和换水","测试区",5000));
        directory.audit(4,sitter,new DirectoryController.Audit(true,"通过"));
        String pet=(String)directory.savePet(owner,null,new DirectoryController.Pet("团子","换水","CAT","英短",24)).get("ID");
        String address=(String)directory.saveAddress(owner,null,new DirectoryController.Address("小王","13800000000","测试区","测试街道 1 号")).get("ID");
        String slot=(String)bookings.addSlot(sitter,OffsetDateTime.now().plusDays(1),OffsetDateTime.now().plusDays(1).plusHours(1),5000).get("ID");
        return new Fixture(owner,sitter,pet,address,slot);
    }
    String book(Fixture f){return (String)bookings.book(f.owner,f.pet,f.slot,f.address,name()).get("ID");}
    String bearer(long user){var username=auth.user(user).username();return "Bearer "+auth.login(username,"TestPass123!").accessToken();}
    void inProgress(Fixture f,String order){bookings.transition(f.sitter,order,"accept","");bookings.transition(f.owner,order,"pay","");db.update("UPDATE slots SET starts_at=?,ends_at=? WHERE id=?",OffsetDateTime.now().minusHours(2),OffsetDateTime.now().minusHours(1),f.slot);bookings.transition(f.sitter,order,"start","");}
    @Test void petAndAddressOwnershipAndSoftDeletion(){
        var f=fixture();long outsider=user("OWNER");
        assertThatThrownBy(()->directory.savePet(outsider,f.pet,new DirectoryController.Pet("其他","","CAT","",0))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->directory.deleteAddress(outsider,f.address)).isInstanceOf(ResponseStatusException.class);
        String order=book(f);
        directory.deletePet(f.owner,f.pet);directory.deleteAddress(f.owner,f.address);
        assertThat(directory.pets(f.owner)).isEmpty();assertThat(directory.addresses(f.owner)).isEmpty();
        assertThat(bookings.detail(f.owner,order).get("ADDRESS_SNAPSHOT")).asString().contains("测试街道");
        bookings.transition(f.owner,order,"cancel","");
        assertThatThrownBy(()->bookings.book(f.owner,f.pet,f.slot,f.address,name())).isInstanceOf(ResponseStatusException.class);
    }
    @Test void profileRequiresApprovalAndEditingResubmits(){
        long sitter=user("SITTER");directory.saveProfile(sitter,new DirectoryController.Profile("介绍","测试区",5000));
        assertThatThrownBy(()->bookings.addSlot(sitter,OffsetDateTime.now().plusDays(1),OffsetDateTime.now().plusDays(1).plusHours(1),5000)).isInstanceOf(ResponseStatusException.class);
        directory.audit(4,sitter,new DirectoryController.Audit(true,"通过"));
        bookings.addSlot(sitter,OffsetDateTime.now().plusDays(1),OffsetDateTime.now().plusDays(1).plusHours(1),5000);
        directory.saveProfile(sitter,new DirectoryController.Profile("新介绍","测试区",5000));
        assertThat(directory.profile(sitter).get("STATUS")).isEqualTo("PENDING");
        assertThat(bookings.slots()).noneMatch(row->((Number)row.get("SITTER_ID")).longValue()==sitter);
    }
    @Test void differentRegionsCanBookButWithdrawnSlotsRejected(){
        var f=fixture();String wrong=(String)directory.saveAddress(f.owner,null,new DirectoryController.Address("小王","13800000000","其他区","门牌")).get("ID");
        String order=(String)bookings.book(f.owner,f.pet,f.slot,wrong,name()).get("ID");
        assertThat(bookings.detail(f.sitter,order).get("ADDRESS_SNAPSHOT")).asString().contains("其他区","门牌");
        bookings.transition(f.sitter,order,"reject","距离不合适");
        bookings.deleteSlot(f.sitter,f.slot);
        assertThatThrownBy(()->book(f)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void agentFindsAndConfirmsSlotWithoutRegionRestriction(){
        var f=fixture();
        String address=(String)directory.saveAddress(f.owner,null,new DirectoryController.Address("主人","13800000000","不同城市","实际上门楼栋")).get("ID");
        String session=(String)agent.session(f.owner).get("ID");
        var tools=new AgentTools(f.owner,session,directory,bookings,drafts);
        String day=BookingService.time(bookings.one("SELECT * FROM slots WHERE id=?",f.slot).get("STARTS_AT")).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
        assertThat(tools.searchSlots(day,5000)).anyMatch(row->f.slot.equals(row.id()));
        assertThat(tools.searchSlots(day,4999)).noneMatch(row->f.slot.equals(row.id()));
        assertThatThrownBy(()->tools.prepareDraft(f.pet,f.slot,address)).isInstanceOf(ResponseStatusException.class);
        tools.searchSlots(day,5000);tools.listMyPets();tools.listMyAddresses();
        String draft=tools.prepareDraft(f.pet,f.slot,address).id();
        String order=(String)drafts.confirm(f.owner,draft).get("ID");
        assertThat(bookings.detail(f.sitter,order).get("ADDRESS_SNAPSHOT")).asString().contains("不同城市","实际上门楼栋");
    }
    @Test void sitterCanSubmitProfileWithoutServiceRegion() throws Exception {
        long sitter=user("SITTER");
        mvc.perform(put("/api/sitter/profile").header("Authorization",bearer(sitter)).contentType("application/json").content("{\"bio\":\"照顾宠物\",\"priceCents\":5000}")).andExpect(status().isOk());
        assertThat(directory.profile(sitter).get("REGION")).isEqualTo("");
    }
    @Test void rejectedOrderRefundsAndReleasesAndCannotReleaseNextBooking(){
        var f=fixture();String order=book(f);bookings.transition(f.owner,order,"pay","");
        assertThat(bookings.transition(f.sitter,order,"reject","时间不合适").get("PAYMENT_STATE")).isEqualTo("REFUNDED");
        String next=book(f);bookings.transition(f.sitter,order,"reject","重复");
        assertThat(bookings.detail(f.owner,next).get("STATUS")).isEqualTo("PENDING");
        assertThat(bookings.one("SELECT * FROM slots WHERE id=?",f.slot).get("BOOKED")).isEqualTo(true);
    }
    @Test void expiryChecksActualStateAndReleasesOnlyOnce(){
        var f=fixture();String order=book(f);db.update("UPDATE orders SET created_at=? WHERE id=?",OffsetDateTime.now().minusHours(3),order);
        assertThatThrownBy(()->bookings.transition(f.sitter,order,"accept","")).isInstanceOf(ResponseStatusException.class);
        bookings.expire(order);assertThat(bookings.detail(f.owner,order).get("STATUS")).isEqualTo("EXPIRED");
        book(f);bookings.expire(order);assertThat(bookings.one("SELECT * FROM slots WHERE id=?",f.slot).get("BOOKED")).isEqualTo(true);
    }
    @Test void fullWorkflowRequiresPaymentRecordAndOwnerConfirmation(){
        var f=fixture();String order=book(f);bookings.transition(f.sitter,order,"accept","");
        assertThatThrownBy(()->bookings.transition(f.sitter,order,"start","")).isInstanceOf(ResponseStatusException.class);
        bookings.transition(f.owner,order,"pay","");
        assertThatThrownBy(()->bookings.transition(f.sitter,order,"start","")).isInstanceOf(ResponseStatusException.class);
        db.update("UPDATE slots SET starts_at=?,ends_at=? WHERE id=?",OffsetDateTime.now().minusHours(2),OffsetDateTime.now().minusHours(1),f.slot);
        bookings.transition(f.sitter,order,"start","");
        assertThatThrownBy(()->bookings.transition(f.owner,order,"cancel","")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->bookings.transition(f.sitter,order,"complete","")).isInstanceOf(ResponseStatusException.class);
        bookings.record(f.sitter,order,"喂粮换水，状态正常",List.of());bookings.transition(f.sitter,order,"complete","");
        assertThat(bookings.detail(f.owner,order).get("STATUS")).isEqualTo("WAITING_CONFIRM");
        bookings.transition(f.owner,order,"confirm","");bookings.review(f.owner,order,5,"照顾细心");
        assertThatThrownBy(()->bookings.review(f.owner,order,5,"重复")).isInstanceOf(ResponseStatusException.class);
        assertThat(bookings.records(f.owner,order)).hasSize(1);
    }
    @Test void complaintsArePrivateAndAdministratorResolves(){
        var f=fixture();String order=book(f);long outsider=user("OWNER");
        assertThatThrownBy(()->bookings.complain(outsider,order,"越权")).isInstanceOf(ResponseStatusException.class);
        String complaint=(String)bookings.complain(f.owner,order,"请核实服务安排").get("ID");
        assertThat(bookings.complaints(outsider)).isEmpty();
        assertThatThrownBy(()->bookings.resolve(f.owner,complaint,"违规处理")).isInstanceOf(ResponseStatusException.class);
        assertThat(bookings.resolve(4,complaint,"已联系双方核实").get("STATUS")).isEqualTo("RESOLVED");
    }
    @Test void photoUploadUsesActualImageAndEnforcesOrderPrivacy() throws Exception {
        var f=fixture();String order=book(f);inProgress(f,order);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var uploaded=mvc.perform(multipart("/api/orders/"+order+"/photos").file(new MockMultipartFile("file","../../unsafe.png","text/plain",bytes.toByteArray())).header("Authorization",bearer(f.sitter)))
            .andExpect(status().isOk()).andReturn();
        String photo=json.readTree(uploaded.getResponse().getContentAsString()).get("id").asText();
        bookings.record(f.sitter,order,"换水完成",List.of(photo));
        mvc.perform(get("/api/photos/"+photo).header("Authorization",bearer(f.owner))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/photos/"+photo).header("Authorization",bearer(user("OWNER")))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/orders/"+order+"/photos").file(new MockMultipartFile("file","fake.png","image/png","not image".getBytes())).header("Authorization",bearer(f.sitter))).andExpect(status().isBadRequest());
        assertThatThrownBy(()->bookings.record(f.sitter,order,"伪造照片",List.of(UUID.randomUUID().toString()))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void draftDoesNotReserveAndConfirmationIsIdempotent(){
        var f=fixture();String session=(String)agent.session(f.owner).get("ID");String draft=(String)drafts.create(f.owner,session,f.pet,f.slot,f.address).get("ID");
        assertThat(bookings.one("SELECT * FROM slots WHERE id=?",f.slot).get("BOOKED")).isEqualTo(false);
        assertThat(drafts.confirm(f.owner,draft).get("ID")).isEqualTo(drafts.confirm(f.owner,draft).get("ID"));
        assertThatThrownBy(()->drafts.confirm(user("OWNER"),draft)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void draftExpiryChangedPriceAndRaceRevalidated(){
        var f=fixture();String session=(String)agent.session(f.owner).get("ID");String draft=(String)drafts.create(f.owner,session,f.pet,f.slot,f.address).get("ID");
        db.update("UPDATE slots SET price_cents=6000 WHERE id=?",f.slot);
        assertThatThrownBy(()->drafts.confirm(f.owner,draft)).isInstanceOf(ResponseStatusException.class);
        db.update("UPDATE slots SET price_cents=5000 WHERE id=?",f.slot);db.update("UPDATE booking_drafts SET expires_at=? WHERE id=?",OffsetDateTime.now().minusMinutes(1),draft);
        assertThatThrownBy(()->drafts.confirm(f.owner,draft)).isInstanceOf(ResponseStatusException.class);
        String other=(String)drafts.create(f.owner,session,f.pet,f.slot,f.address).get("ID");book(f);
        assertThatThrownBy(()->drafts.confirm(f.owner,other)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void concurrentDraftConfirmationAndManualBookingDoNotDeadlock() throws Exception {
        var f=fixture();String session=(String)agent.session(f.owner).get("ID");String draft=(String)drafts.create(f.owner,session,f.pet,f.slot,f.address).get("ID");
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->attempt(gate,()->drafts.confirm(f.owner,draft)));
            var second=pool.submit(()->attempt(gate,()->book(f)));gate.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS)+second.get(10,TimeUnit.SECONDS)).isEqualTo(1);
        }
    }
    int attempt(CountDownLatch gate,Callable<?> action) throws Exception {gate.await();try{action.call();return 1;}catch(ResponseStatusException ex){assertThat(ex.getStatusCode().value()).isEqualTo(409);return 0;}}
    @Test void unconfiguredAgentExplainsAndSessionBelongsToOwner() throws Exception {
        var f=fixture();String session=(String)agent.session(f.owner).get("ID");
        assertThatThrownBy(()->agent.get(user("OWNER"),session)).isInstanceOf(ResponseStatusException.class);
        mvc.perform(post("/api/agent/sessions/"+session+"/messages").header("Authorization",bearer(f.owner)).contentType("application/json").content("{\"message\":\"明天喂猫\"}"))
            .andExpect(status().isServiceUnavailable());
    }
    @Test void disablingUserRevokesSessionsAndBlocksLogin() throws Exception {
        long owner=user("OWNER");String token=bearer(owner);auth.accountStatus(4,owner,false);
        mvc.perform(get("/api/auth/me").header("Authorization",token)).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->auth.login(auth.user(owner).username(),"TestPass123!")).isInstanceOf(ResponseStatusException.class);
        auth.accountStatus(4,owner,true);assertThat(auth.login(auth.user(owner).username(),"TestPass123!").user().enabled()).isTrue();
        assertThatThrownBy(()->auth.accountStatus(4,4,false)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void sixteenOwnersCompeteForOneSlotOnlyOneSucceeds() throws Exception {
        var f=fixture();var calls=new ArrayList<Callable<Integer>>();var gate=new CountDownLatch(1);
        for(int i=0;i<16;i++){
            long owner=user("OWNER");String pet=(String)directory.savePet(owner,null,new DirectoryController.Pet("猫","","CAT","",0)).get("ID");
            String address=(String)directory.saveAddress(owner,null,new DirectoryController.Address("主人","13800000000","测试区","门牌")).get("ID");
            calls.add(()->attempt(gate,()->bookings.book(owner,pet,f.slot,address,name())));
        }
        try(var pool=Executors.newFixedThreadPool(16)){
            var futures=calls.stream().map(pool::submit).toList();gate.countDown();int wins=0;
            for(var future:futures)wins+=future.get(20,TimeUnit.SECONDS);assertThat(wins).isEqualTo(1);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orders WHERE slot_id=?",Integer.class,f.slot)).isEqualTo(1);
    }
    @Test void concurrentAgentQuotaReservationsCannotExceedDailyLimit() throws Exception {
        long owner=user("OWNER");java.time.LocalDate day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        db.update("INSERT INTO agent_quotas VALUES (?,?,49)",owner,day);var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(10)){
            var futures=new ArrayList<Future<Integer>>();
            for(int i=0;i<10;i++)futures.add(pool.submit(()->{gate.await();try{quota.reserve(owner);return 1;}catch(ResponseStatusException ex){assertThat(ex.getStatusCode().value()).isEqualTo(429);return 0;}}));
            gate.countDown();int allowed=0;for(var future:futures)allowed+=future.get(15,TimeUnit.SECONDS);assertThat(allowed).isEqualTo(1);
        }
        assertThat(db.queryForObject("SELECT calls FROM agent_quotas WHERE owner_id=? AND quota_date=?",Integer.class,owner,day)).isEqualTo(50);
    }
}
