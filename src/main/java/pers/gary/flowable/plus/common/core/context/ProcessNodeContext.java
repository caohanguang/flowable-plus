package pers.gary.flowable.plus.common.core.context;

import pers.gary.flowable.plus.common.annotation.ProcessNode;
import pers.gary.flowable.plus.common.entity.FlowInfo;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.Map;

/**
 * 获取流程引擎相关索引数据
 */
public interface ProcessNodeContext {

    /**
     *十分重要的方法，用于确定流程引擎上下文
     * @param proceedingJoinPoint
     * @param processNode
     * @return FlowInfo
     */
    FlowInfo findFlowInfo(ProceedingJoinPoint proceedingJoinPoint, ProcessNode processNode, Map<String,Object> params);
    void initNodeContext(FlowInfo flowInfo);
    FlowInfo getCurrentFlowInfo();
    void clearFlowInfo();
}
