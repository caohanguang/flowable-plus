package pers.gary.flowable.plus.common.dto;

import lombok.Data;

import java.util.Date;

@Data
public class ProcessIns {
    /**
     * 流程实例状态，active:已发起未结束,ended:已经完成,suspended:被挂起
     */
    private String status = "active";

    /**
     * 流程发起人
     */
    private String startUserId;

    /**
     * 流程实例开始时间
     */
    private Long startTime;


    /**
     * 流程模型唯一标识
     */
    private String modelKey;

    /**
     * 流程模型名称
     */
    private String modelName;

    /**
     * 业务id
     */
    private String businessId;

    /**
     * 流程实例id
     */
    private String processInstanceId;
}
