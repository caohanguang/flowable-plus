package pers.gary.flowable.plus.common.core.context;

import pers.gary.flowable.plus.common.annotation.ProcessNode;
import pers.gary.flowable.plus.common.entity.FlowInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.aspectj.lang.ProceedingJoinPoint;


import java.util.Map;
import java.util.Optional;

/**
 * 该类的实例由业务代码的配置类注入
 */
@Slf4j
public abstract class AbstractProcessNodeContext implements ProcessNodeContext {
    private static final ThreadLocal<FlowInfo> nodeContext = new ThreadLocal<>();

    /**
     * Controller层调用
     * @param flowInfo
     */
    @Override
    public void initNodeContext(FlowInfo flowInfo){
        nodeContext.set(flowInfo);
        log.info("process node context init {}",flowInfo);
    }

    @Override
    public FlowInfo getCurrentFlowInfo(){
        return nodeContext.get();
    }

    @Override
    public void clearFlowInfo(){
         nodeContext.remove();
    }
    @Override
    public FlowInfo findFlowInfo(ProceedingJoinPoint proceedingJoinPoint, ProcessNode processPram, Map<String,Object> params) {

        FlowInfo flowInfo  = nodeContext.get();
        if(flowInfo == null){
            for(Object o : proceedingJoinPoint.getArgs()){
                if(o instanceof FlowInfo){
                    flowInfo = (FlowInfo)o;
                    nodeContext.set(flowInfo);
                    break;
                }
            }
        }

        //header中取不到值的情况,先初始化一下
        if(!Optional.ofNullable(flowInfo).isPresent()){
            flowInfo = new FlowInfo();
        }


        //TODO 纯注解方式从参数中提取流程上下文
        if(StringUtils.isNotBlank(processPram.processModelKeyName())
                && StringUtils.isBlank(flowInfo.getProcessModelKey())){
            String processModelKeyName = processPram.processModelKeyName();
            flowInfo.setProcessModelKey(getContextArgs(processPram, params, processModelKeyName));
        }

        if(processPram.productIdName().length() > 0 && StringUtils.isBlank(flowInfo.getProductId())){
            flowInfo.setProductId(getContextArgs(processPram, params, processPram.productIdName()));
        }

        if(processPram.inputBusinessIdName().length > 0 &&
                StringUtils.isNotBlank(processPram.productIdName()) &&
                StringUtils.isBlank(flowInfo.getBusinessId())){
            StringBuilder sb = new StringBuilder();
            for(String s : processPram.inputBusinessIdName()){
                sb.append(getContextArgs(processPram, params, s)).append(":");
            }
            flowInfo.setBusinessId(sb.substring(0,sb.length() - 1));
            flowInfo.setProductId(getContextArgs(processPram, params, processPram.productIdName()));
        }

        if(StringUtils.isNotBlank(processPram.inputTaskUserName())
                && StringUtils.isBlank(flowInfo.getTaskUser())){
            flowInfo.setTaskUser(getContextArgs(processPram, params, processPram.inputTaskUserName()));
        }

        if(StringUtils.isNotBlank(processPram.startUserName())
                && StringUtils.isBlank(flowInfo.getStartUser())){
            flowInfo.setStartUser(getContextArgs(processPram, params, processPram.startUserName()));
        }

        if(StringUtils.isBlank(flowInfo.getProcessId()) &&
                StringUtils.isBlank(flowInfo.getProcessModelKey()) &&
                StringUtils.isBlank(flowInfo.getBusinessKey())
                && StringUtils.isBlank(flowInfo.getProductId())
                && StringUtils.isBlank(flowInfo.getBusinessId())){
            flowInfo.setInProcess(false);
            //return flowInfo;
        }

        String businessKey = flowInfo.getBusinessKey();
        //启动流程获取model
        if(StringUtils.isNotBlank(flowInfo.getProcessModelKey()) || StringUtils.isNotBlank(businessKey)){
            flowInfo.setProcessStart(true);
            //使用businessKey关联processModelKey
            if(StringUtils.isNotBlank(businessKey) &&
                    StringUtils.isBlank(flowInfo.getProcessModelKey())){
                flowInfo.setProcessModelKey(findProcessModelKeyByBusinessKey(businessKey));
            }
        }
        return flowInfo;
    }

    private String getContextArgs(ProcessNode processPram, Map<String, Object> params, String path) {
        if(path.contains(".")){
            //复杂路径的情况
            //提取第一级路径
            int firstIndex = path.indexOf(".");
            Object domainObject = params.get(processPram.processModelKeyName().substring(0,firstIndex));
            if(domainObject != null){
                MetaObject metaObject = new Configuration().newMetaObject(domainObject);
                String path_ = path.substring(firstIndex + 1);
                return (String)metaObject.getValue(path_);
            }

        }else{
            //简单路径
            return (String)params.get(path);
        }
        return null;
    }

    /**
     * @param businessKey
     * @return 流程模板key
     */
    public abstract String findProcessModelKeyByBusinessKey(String businessKey);
}
