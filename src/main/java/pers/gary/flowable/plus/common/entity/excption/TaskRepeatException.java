package pers.gary.flowable.plus.common.entity.excption;

import org.flowable.common.engine.api.FlowableException;

/**
 * 业务重放时，由业务方法抛出此异常
 * 此异常在切面内抛出，流程引擎将自动重试当前任务
 */
public class TaskRepeatException extends FlowableException {
	public TaskRepeatException(String message, Throwable cause) {
		super(message, cause);
	}

	public TaskRepeatException(String message) {
		super(message);
	}
}
