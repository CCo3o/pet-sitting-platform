package com.petsitting.agent;

import com.petsitting.BookingService;
import com.petsitting.directory.DirectoryService;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.ai.tool.annotation.Tool;
import static com.petsitting.agent.AgentDtos.*;

/** Bound to the authenticated owner. The model cannot select an actor or create orders. */
public class AgentTools {
    private RoleAgentTools roleTools;
    private String role="OWNER";
    public AgentTools withRole(String role,RoleAgentTools tools){this.role=role;this.roleTools=tools;return this;}
    public String role(){return role;}
    public Object toolObject(){return roleTools==null?this:roleTools.tools();}
    private final long actor;
    private final String session;
    private final DirectoryService directory;
    private final BookingService bookings;
    private final DraftService drafts;
    private final AgentTraceService traces;
    private final String run;
    private final AtomicInteger sequence=new AtomicInteger();
    private Map<String,Object> draft;
    private final Set<String> queriedSlots=new HashSet<>();
    private final Set<String> queriedPets=new HashSet<>(),queriedAddresses=new HashSet<>();
    public AgentTools(long actor,String session,DirectoryService directory,BookingService bookings,DraftService drafts) {
        this(actor,session,directory,bookings,drafts,null,null);
    }
    public AgentTools(long actor,String session,DirectoryService directory,BookingService bookings,DraftService drafts,AgentTraceService traces,String run) {
        this.actor=actor;this.session=session;this.directory=directory;this.bookings=bookings;this.drafts=drafts;this.traces=traces;this.run=run;
    }
    private <T> T invoke(String name,Map<String,Object> args,Supplier<T> action) {
        return traces==null?action.get():traces.execute(run,sequence.incrementAndGet(),name,args,action);
    }
    @Tool(description="查询当前主人的宠物及照顾注意事项。宠物只能从真实返回值中选择。")
    public List<PetOption> listMyPets() {
        return invoke("listMyPets",Map.of(),()->{
            var found=directory.pets(actor).stream().map(p->new PetOption((String)p.get("ID"),(String)p.get("NAME"),(String)p.get("SPECIES"),(String)p.get("NOTES"))).toList();
            queriedPets.clear();found.forEach(p->queriedPets.add(p.id()));return found;
        });
    }
    @Tool(description="查询当前主人的上门地址，包含 ID 和位置所在区域，不包含联系电话和详细门牌。没有服务区域匹配限制。")
    public List<AddressOption> listMyAddresses() {
        return invoke("listMyAddresses",Map.of(),()->{
            var found=directory.addresses(actor).stream().map(a->new AddressOption((String)a.get("ID"),(String)a.get("REGION"),(String)a.get("CONTACT"))).toList();
            queriedAddresses.clear();found.forEach(a->queriedAddresses.add(a.id()));return found;
        });
    }
    @Tool(description="按北京时间日期 YYYY-MM-DD 和预算（整数分）查询所有喂养员的可约时段及真实报价，不按地址或区域筛选。返回时间带时区，展示时换算为北京时间。")
    public List<SlotOption> searchSlots(String date,int maxPriceCents) {
        return invoke("searchSlots",Map.of("date",date,"maxPriceCents",maxPriceCents),()->{
            queriedSlots.clear();
            LocalDate day=LocalDate.parse(date);
            var found=bookings.slots().stream().map(SlotOption::from).filter(s->s.startsAt().atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate().equals(day)&&s.priceCents()<=maxPriceCents).limit(20).toList();
            found.forEach(s->queriedSlots.add(s.id()));return found;
        });
    }
    @Tool(description="在用户明确宠物、日期、地址并选择具体时段后生成预约草稿。只保存草稿，不创建订单、不占用时段；仍需用户点击确认按钮。")
    public DraftSummary prepareDraft(String petId,String slotId,String addressId) {
        return invoke("prepareDraft",Map.of("petId",petId,"slotId",slotId,"addressId",addressId),()->{
            bookings.require(queriedPets.contains(petId)&&queriedAddresses.contains(addressId),"请先调用listMyPets和listMyAddresses，使用本轮返回的真实宠物ID和地址ID，不要猜测ID；查询已获得用户授权，无需再次征求查询许可");
            bookings.require(queriedSlots.contains(slotId),"请先重新查询可约时段，再选择工具实际返回的时段生成草稿");
            draft=drafts.create(actor,session,petId,slotId,addressId);return DraftSummary.from(draft);
        });
    }
    public Map<String,Object> draft() { return roleTools==null?draft:roleTools.draft(); }
    public String previousContext() { return traces==null?"[]":traces.previousContext(actor,session); }
}
