package pers.gary.flowable.plus.config;

public class Constant {

	/**
	 * 流程开始时初始化流程上下文key,需要实现序列化接口
	 */
	public static final String PROCESS_BUSINESS_CONTEXT = "processBusinessContext";

	/**
	 * processBusinessContext 反序列化类型
	 */
	public static final String PROCESS_BUSINESS_CONTEXT_CLASS = "processBusinessContextClass";

	/**
	 * 每个任务的业务上下文Key,主要用于回调方法内获取业务执行结果
	 */
	public static final String TASK_BUSINESS_CONTEXT = "taskBusinessContext";

	/**
	 *taskBusinessContext 反序列化类型
	 */
	public static final String TASK_BUSINESS_CONTEXT_CLASS = "taskBusinessContextClass";


	public static final String BUSINESS_ID = "businessId";

	public static final String TASK_USER = "taskUser";

	public static final String TASK_USER_PREFIX = "taskUser_";

	public static final String SELF_BUSINESS_ID = "myself";

	//已经挂起
	public static final String  SUSPENDED = "suspended";

	//已经结束
	public static final String  ENDED = "ended";

	//运行中
	public static final String  ACTIVE = "active";

	public static final String IP_PORT_REGEX = "(25[0-5]|2[0-4]\\d|[0-1]\\d{2}|[1-9]?\\d)\\.(25[0-5]|2[0-4]\\d|[0-1]\\d{2}|[1-9]?\\d)\\.(25[0-5]|2[0-4]\\d|[0-1]\\d{2}|[1-9]?\\d)\\.(25[0-5]|2[0-4]\\d|[0-1]\\d{2}|[1-9]?\\d)\\:(6553[0-5]|655[0-2]\\d|65[0-4]\\d{2}|6[0-4]\\d{3}|[0-5]\\d{4}|[1-9]\\d{0,3})";

}
