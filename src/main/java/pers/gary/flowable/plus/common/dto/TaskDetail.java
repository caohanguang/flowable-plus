package pers.gary.flowable.plus.common.dto;

import lombok.Data;
import org.flowable.engine.form.FormProperty;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.Task;

import java.util.List;

/**
 * 流程任务详情
 */
@Data
public class TaskDetail {
	private HistoricActivityInstance historicActivityInstance;
	private Task task;
	/**
	 * 任务的表单属性
	 */
	private List<FormProperty> formProperties;
}
