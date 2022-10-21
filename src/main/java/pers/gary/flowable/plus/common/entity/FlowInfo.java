package pers.gary.flowable.plus.common.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程service入参实体类
 * @param
 */
@Data
public class FlowInfo implements Serializable {
	public static final long serialVersionUID = 1l;
	//流程key 外部传入
	private String processModelKey;
	//流程id 外部传入
	private String processId;

	//对应el表达式${taskUser},启动流程时或执行一些任务时由外部传入，为下一个任务设置执行用户
	private String taskUser;

	//业务Id 外部传入
	private String businessId;
	//是否启动流程 启动流程时外部传入
	private boolean processStart;

	//产品Id,用于区分不同的应用
	private String productId;

	//与流程引擎模板关联的业务key 外部传入
	private String businessKey;

	//一个userTask和由它触发所有serviceTask，内部设置
	private boolean sameScope;

	//当前上下文是否在一个流程中，这个字段使得即使一个服务方法标注了@PrecessNode注解
	// 它依然可以在非业务引擎上线文中使用
	// 内部设置
	private boolean inProcess = true;

	//流程发起者
	private String startUser;

}
