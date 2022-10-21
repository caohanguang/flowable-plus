package pers.gary.flowable.plus.common.dto;

import lombok.Data;

@Data
public class TaskInfo {
    /**
     * 任务开始时间
     */
    private Long startTime;

    /**
     * 任务办结时间
     */
    private Long endTime;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 潜在处理用户
     */
    private String assignee;

    /**
     * 处理用户
     */
    private String owner;

    /**
     * 执行id
     */
    private String executionId;

    /**
     * 任务id
     */
    private String taskId;

    /**
     * 任务定义标识
     */
    private String taskDefinitionKey;

    /**
     * 是否已经完成
     */
    private boolean finish;
}
