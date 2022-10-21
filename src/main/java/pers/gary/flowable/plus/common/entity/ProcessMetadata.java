package pers.gary.flowable.plus.common.entity;

import lombok.Data;
import org.flowable.engine.form.FormProperty;

import java.util.List;

@Data
public class ProcessMetadata {
	/**
	 * 流程定义的主键
	 */
	private String processDefinitionKey;
	/**
	 * 流程实例的Id
	 */
	private String processInstanceId;
	/**
	 * task的Key,即任务主键
	 */
	private String taskDefinitionKey;
	/**
	 * 任务的表单属性
	 */
	private List<FormProperty> formProperties;

}
