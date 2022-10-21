package pers.gary.flowable.plus.common.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import pers.gary.flowable.plus.common.core.context.ProcessNodeContext;
import pers.gary.flowable.plus.common.core.context.UserContext;
import pers.gary.flowable.plus.common.dto.ProcessIns;
import pers.gary.flowable.plus.common.dto.TaskDetail;
import pers.gary.flowable.plus.common.dto.TaskInfo;
import pers.gary.flowable.plus.common.entity.FlowInfo;
import pers.gary.flowable.plus.common.entity.ProcessMetadata;
import pers.gary.flowable.plus.common.entity.excption.ArgumentException;
import pers.gary.flowable.plus.config.Constant;
import org.apache.commons.lang.StringUtils;
import org.flowable.bpmn.model.*;
import org.flowable.engine.*;
import org.flowable.engine.form.FormProperty;
import org.flowable.engine.form.TaskFormData;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlusUtil {
	/**
	 * 当前请求元数据上下文
	 */
	private static final ThreadLocal<ProcessMetadata> PROCESS_METADATA
		= new ThreadLocal<>();


	/**
	 * 根据业务Id获取流程信息，包括：流程状态（完成，未完成），流程实例信息，流程流转历史信息
	 * @param businessId 流程实例Id
	 * @return 实例信息
	 */
	public static JSONObject getProcessInsByBusinessId(String businessId){
		if(StringUtils.isBlank(businessId)){
			throw new ArgumentException("businessId can not be empty!");
		}
		HistoryService historyService = FlowableServiceFactory.getServiceByType(ProcessEngine.class)
				.getHistoryService();
		HistoricTaskInstanceQuery taskInstanceQuery = historyService.createHistoricTaskInstanceQuery();
		taskInstanceQuery.processInstanceBusinessKey(businessId);
		List<HistoricTaskInstance>  historicTaskList = taskInstanceQuery.orderByHistoricTaskInstanceStartTime().desc().list();
		ProcessIns processIns = new ProcessIns();
		try{
			ProcessInstance processInstance = getProcessInstanceByBusinessId(businessId);
			processIns.setProcessInstanceId(processInstance.getProcessInstanceId());
			processIns.setBusinessId(processInstance.getBusinessKey());
			processIns.setModelKey(processInstance.getProcessDefinitionKey());
			processIns.setModelName(processInstance.getProcessDefinitionName());
			processIns.setStartTime(processInstance.getStartTime().getTime());
			String user = processInstance.getStartUserId();
			if(StringUtils.isNotBlank(user)){
				user = user.replace("taskUser_","");
				processIns.setStartUserId(user);
			}

			//设置流程实例状态
			if(processInstance.isEnded()){
				processIns.setStatus(Constant.ENDED);
			} else if(processInstance.isSuspended()){
				processIns.setStatus(Constant.SUSPENDED);
			}
		}catch (Exception ex){
			//TODO 这里是否可以不放在异常分支里
			HistoricProcessInstance hisProcessIns = historyService.createHistoricProcessInstanceQuery()
					.processInstanceBusinessKey(businessId).singleResult();

			processIns.setProcessInstanceId(hisProcessIns.getId());
			processIns.setBusinessId(hisProcessIns.getBusinessKey());
			processIns.setModelKey(hisProcessIns.getProcessDefinitionKey());
			processIns.setModelName(hisProcessIns.getProcessDefinitionName());
			processIns.setStartTime(hisProcessIns.getStartTime().getTime());
			String user = hisProcessIns.getStartUserId();
			if(StringUtils.isNotBlank(user)){
				user = user.replace("taskUser_","");
				processIns.setStartUserId(user);
			}
			processIns.setStatus(Constant.ENDED);
		}
		//processIsFinished
		JSONObject jsonObject = new JSONObject();
		//处理流程实例数据
		JSONObject processJSON = JSONObject.parseObject(JSON.toJSONString(processIns));
		jsonObject.put("processInstance",processJSON);
		List<TaskInfo> taskInfos = new ArrayList<>();
		if(historicTaskList != null && historicTaskList.size() > 0){
			historicTaskList.forEach(e->{
				TaskInfo taskInfo = new TaskInfo();
				taskInfo.setName(e.getName());
				taskInfo.setTaskId(e.getId());
				if(!StringUtils.isBlank(e.getAssignee())){
					taskInfo.setAssignee(e.getAssignee().replace("taskUser_",""));
				}

				if(!StringUtils.isBlank(e.getOwner())){
					taskInfo.setOwner(e.getOwner().replace("taskUser_",""));
				}

				taskInfo.setExecutionId(e.getExecutionId());
				taskInfo.setStartTime(e.getCreateTime().getTime());
				if(e.getEndTime() != null){
					taskInfo.setEndTime(e.getEndTime().getTime());
					taskInfo.setFinish(true);
				}
				taskInfos.add(taskInfo);
			});
		}
		//处理历史数据
		JSONArray historyList = JSONArray.parseArray(JSON.toJSONString(taskInfos));
		jsonObject.put("historicTaskList",historyList);
		return jsonObject;
	}

	/**
	 * 查询当前用户关于某一业务的代办任务
	 * @param businessId 业务id
	 * @param userId 用户id
	 * @return 任务详情
	 */
	public static Map<String,Object> queryTodoTask(String businessId,String userId){
		TaskService taskService = FlowableServiceFactory.getServiceByType(TaskService.class);
		String user = FlowableServiceFactory.getServiceByType(UserContext.class).getUserById(userId);
		TaskQuery query = taskService.createTaskQuery();
		Task task = query.processInstanceBusinessKey(businessId)
				.active().taskAssignee(user).singleResult();
		return queryTaskByTaskId(task.getId());
	}


	/**
	 *根据流程实例ID查询流程是否结束
	 * @param processInstanceId 流程实例Id
	 * @return true完成，false未完成
	 */
	 static boolean isFlowEnd(String processInstanceId) {
		if(StringUtils.isBlank(processInstanceId)){
			throw new ArgumentException("processId can not be empty!");
		}
		boolean result = false;
		TaskService taskService = FlowableServiceFactory.getServiceByType(TaskService.class);
		RepositoryService repositoryService =
			FlowableServiceFactory.getServiceByType(RepositoryService.class);
		Task task = taskService.createTaskQuery()
			.processInstanceId(processInstanceId).singleResult();
		if(task==null){
			return true;
		}
		HistoryService historyService = FlowableServiceFactory.getServiceByType(HistoryService.class);
		String definitionId = historyService
			.createProcessInstanceHistoryLogQuery(processInstanceId).singleResult()
			.getProcessDefinitionId();
		BpmnModel bpmnModel = repositoryService.getBpmnModel(definitionId);
		FlowNode flowNode = (FlowNode) bpmnModel
			.getFlowElement(task.getTaskDefinitionKey());
		List<SequenceFlow> outgoingFlows = flowNode.getOutgoingFlows();
		for (SequenceFlow outgoingFlow : outgoingFlows) {
			FlowElement targetFlowElement = outgoingFlow.getTargetFlowElement();
			if (targetFlowElement instanceof EndEvent) {
				result = true;
				break;
			}
		}
		return result;
	}


	/**
	 * 根据流程实例ID获取流程实例信息
	 * @param processId 流程实例id
	 * @return ProcessInstance
	 */
	public static ProcessInstance getProcessInstanceById(String processId){
		if(StringUtils.isBlank(processId)){
			throw new ArgumentException("processId can not be empty!");
		}
		ApplicationContext applicationContext =
			ApplicationContextUtil.getStringApplicationContext();
		RuntimeService runtimeService =
			applicationContext.getBean(RuntimeService.class);
		return runtimeService.createProcessInstanceQuery()
			.processInstanceId(processId).includeProcessVariables().singleResult();
	}


	/**
	 *根据流程实例ID和任务定义的Key获取任务信息（包含表单属性）
	 * @param metadata processInstanceId 和 task的Key为必填项
	 * @param isActive 是否活跃，就是没有取消或者办结
	 * @return 任务详情
	 */
	 static TaskDetail queryTask(ProcessMetadata metadata,boolean isActive){
		if(StringUtils.isBlank(metadata.getProcessInstanceId()) ||
			StringUtils.isBlank(metadata.getTaskDefinitionKey())){
			throw new ArgumentException("processId and taskDefinitionKey can not be empty!");
		}
		TaskDetail detail = new TaskDetail();
		Task task;
		FormService formService = FlowableServiceFactory.getServiceByType(FormService.class);
		TaskService taskService = FlowableServiceFactory.getServiceByType(TaskService.class);
		TaskQuery query = taskService.createTaskQuery();
		if(StringUtils.isNotBlank(metadata.getProcessDefinitionKey())){
			 query = query
				.processDefinitionKey(metadata.getProcessDefinitionKey())
				.processInstanceId(metadata.getProcessInstanceId());
			 if(metadata.getTaskDefinitionKey() != null){
				 query = query.taskDefinitionKey(metadata.getTaskDefinitionKey());
			 }
		} else{
			query = query
				.processInstanceId(metadata.getProcessInstanceId());
			if(metadata.getTaskDefinitionKey() != null){
				query = query.taskDefinitionKey(metadata.getTaskDefinitionKey());
			}
		}

		if(isActive){
			task = query.active().singleResult();
		} else{
			task = query.list().get(0);
		}

		if(task != null){
			detail.setTask(task);
			TaskFormData formData = formService.getTaskFormData(task.getId());
			if(formData != null){
				List<FormProperty> properties = formData.getFormProperties();
				detail.setFormProperties(properties);
			}
		}
		return detail;
	}

	/**
	 *根据任务Id查询任务详情
	 * @param taskId 任务Id
	 * @return 任务详情
	 */
	 static Map<String,Object> queryTaskByTaskId(String taskId){
		TaskDetail detail = new TaskDetail();
		Task task;
		FormService formService = FlowableServiceFactory.getServiceByType(FormService.class);
		TaskService taskService = FlowableServiceFactory.getServiceByType(TaskService.class);
		TaskQuery query = taskService.createTaskQuery();
		task = query.taskId(taskId).includeProcessVariables().singleResult();
		if(task != null){
			detail.setTask(task);
			TaskFormData formData = formService.getTaskFormData(task.getId());
			if(formData != null){
				//TODO form属性的类型需要包装一下
				List<FormProperty> properties = formData.getFormProperties();
				detail.setFormProperties(properties);
			}
		}
		Map<String,Object> res = new HashMap<>();
		res.put("taskId",detail.getTask().getId());
		res.put("variables",detail.getTask().getProcessVariables());
		res.put("formProperties",detail.getFormProperties());
		return res;
	}

	/**
	 * 根据业务key获取流程实例信息
	 * @param businessId 业务Id，注意入参的Key是流程Key:实际业务ID
	 * @return 流程实例
	 */
	 static ProcessInstance getProcessInstanceByBusinessId(String businessId){
		if(StringUtils.isBlank(businessId)){
			throw new ArgumentException("businessKey can not be empty!");
		}
		ApplicationContext applicationContext =
			ApplicationContextUtil.getStringApplicationContext();
		RuntimeService runtimeService =
			applicationContext.getBean(RuntimeService.class);
		return runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(businessId)
			   .includeProcessVariables().singleResult();
	}

	/**
	 * 获取当前线程上下文流程引擎元数据（适用于业务代码中）
	 * @return
	 */
	public static ProcessMetadata getMetadata(){
		return PROCESS_METADATA.get();
	}

	public static void setMetadata(ProcessMetadata metadata){
		 PROCESS_METADATA.set(metadata);
	}

	public static void clearMetadata(){
		PROCESS_METADATA.remove();
	}

	/**
	 * 获取当前线程上下文流程引擎入参，是业务service和流程引擎沟通的桥梁
	 * @return
	 */
	public static FlowInfo currentFlowInfo(){
		return FlowableServiceFactory.getServiceByType(ProcessNodeContext.class).getCurrentFlowInfo();
	}

	/**
	 * 启动流程时，手动初始化流程实例上下文。此方法多用于MVC的控制器中，涉及字段包含：
	 * <p>processModelKey & businessKey用于获取启动流程模板 二选一，processModelKey的优先级高于businessKey</p>
	 * <p>taskUser 非必填，用于设置下一任务的执行用户就是userId</p>
	 * <p>businessId 实际业务的Id,贯穿整个流程。优先级高于 @ProcessNode的businessIdName属性配置。二选一</p>
	 * <p>processStart 必传，必须为true,作为流程启动的信号灯</p>
	 * @param fLowInfo
	 */
	public static void initFLowInfo(FlowInfo fLowInfo){
		FlowableServiceFactory.getServiceByType(ProcessNodeContext.class).initNodeContext(fLowInfo);
	}

}
