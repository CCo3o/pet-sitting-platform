package com.petsitting.agent;

import java.util.List;
import java.util.Map;
public interface AgentModel {
    boolean configured();
    ModelReply reply(List<Map<String,String>> history,AgentTools tools);
    record ModelReply(String content,long inputTokens,long outputTokens){}
}
