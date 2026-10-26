package com.petsitting.agent;

import java.util.*;
import java.util.function.Supplier;
import org.springframework.ai.tool.annotation.Tool;

/** Separate tool objects mean the model only receives its role's permitted tool schemas. */
public class RoleAgentTools {
    private final long actor;private final String session,run,role;
    private final RoleAgentService service;private final AgentTraceService traces;
    private int sequence;private Map<String,Object> draft;
    private final Set<String> inspected=new HashSet<>();
    public RoleAgentTools(long actor,String session,String run,String role,RoleAgentService service,AgentTraceService traces){this.actor=actor;this.session=session;this.run=run;this.role=role;this.service=service;this.traces=traces;}
    private <T>T call(String name,Map<String,Object> args,Supplier<T> action){return traces.execute(run,++sequence,name,args,action);}
    public Object tools(){return role.equals("SITTER")?new Sitter():new Admin();}
    public Map<String,Object> draft(){return draft;}
    public class Sitter {
        @Tool(description="查询本人订单、时间、状态和宠物照顾要求；根据北京时间筛选今日待办。")
        public List<Map<String,Object>> listMyOrders(){return call("listMyOrders",Map.of(),()->service.orders(actor));}
        @Tool(description="读取本人订单和已有履约记录，生成草稿前必须调用。")
        public Map<String,Object> inspectOrder(String orderId){return call("inspectOrder",Map.of("orderId",orderId),()->{var result=service.order(actor,orderId);inspected.add(orderId);return result;});}
        @Tool(description="依据用户明确提供的实际服务事实整理履约记录草稿，不得编造已喂食等事实。仅限当前已查询的服务中订单，保存前需用户核实并点击确认。")
        public Map<String,Object> prepareServiceRecord(String orderId,String content){return call("prepareServiceRecord",Map.of("orderId",orderId),()->prepare("FULFILLMENT",orderId,content));}
    }
    public class Admin {
        @Tool(description="查询实时运营统计、待审核喂养员数和待处理投诉数。")
        public Map<String,Object> getOperationsOverview(){return call("getOperationsOverview",Map.of(),()->service.overview(actor));}
        @Tool(description="查询待审核喂养员资料，分析完整性；审核必须由管理员在管理页面操作。")
        public List<Map<String,Object>> listPendingProfiles(){return call("listPendingProfiles",Map.of(),()->service.profiles(actor));}
        @Tool(description="查询待处理投诉，必须根据真实结果分析待办。")
        public List<Map<String,Object>> listOpenComplaints(){return call("listOpenComplaints",Map.of(),()->service.complaints(actor));}
        @Tool(description="查询投诉及关联订单、履约记录作为处理依据，区分投诉陈述与已核实事实。")
        public Map<String,Object> inspectComplaint(String complaintId){return call("inspectComplaint",Map.of("complaintId",complaintId),()->{var result=service.complaint(actor,complaintId);inspected.add(complaintId);return result;});}
        @Tool(description="生成投诉处理建议草稿，需包含依据、待核实事项及处理建议。仅能使用本轮已读取的投诉ID；管理员核实编辑后点击确认才会结案。")
        public Map<String,Object> prepareResolution(String complaintId,String content){return call("prepareResolution",Map.of("complaintId",complaintId),()->prepare("RESOLUTION",complaintId,content));}
    }
    private Map<String,Object> prepare(String kind,String target,String content){
        if(!inspected.contains(target))throw new IllegalArgumentException("请先读取当前对象的真实详情");
        draft=service.draft(actor,session,kind,target,content);return draft;
    }
}
