package pers.gary.flowable.plus.common.core;

import com.alibaba.fastjson.JSON;
import pers.gary.flowable.plus.common.annotation.ProcessNode;
import pers.gary.flowable.plus.common.entity.excption.DuplicateBusinessIdException;
import pers.gary.flowable.plus.common.entity.excption.NotCurrentProcessNodeException;
import pers.gary.flowable.plus.common.util.PlusUtil;
import pers.gary.flowable.plus.common.core.context.ProcessNodeContext;
import pers.gary.flowable.plus.common.core.context.UserContext;
import pers.gary.flowable.plus.common.entity.FlowInfo;
import pers.gary.flowable.plus.common.entity.ProcessMetadata;
import pers.gary.flowable.plus.common.entity.excption.TaskRepeatException;
import pers.gary.flowable.plus.config.Constant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.*;
import org.flowable.engine.form.FormProperty;
import org.flowable.engine.form.TaskFormData;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * @author gary.cho
 */
@Slf4j
@Aspect
@Order(Integer.MAX_VALUE-1)
public class ProcessAroundAspect {

	@Autowired
	private  RuntimeService runtimeService;

	@Autowired
	private  IdentityService identityService;

	@Autowired
	private  TaskService taskService;

	@Autowired
	private FormService formService;

	@Autowired
	private UserContext userContext;

	@Autowired
	private ProcessNodeContext processNodeContext;

	@Autowired
	private RepositoryService repositoryService;

	@Autowired
	private RetryPolicy policy;

	@Pointcut("execution(public * *..service.impl.*.*(..))")
	public void pointCut(){}

	@Around(value = "pointCut()")
	public Object around(ProceedingJoinPoint proceedingJoinPoint){

		Object obj = null;
		try {
			//??????ProcessNode??????
			String methodName = proceedingJoinPoint.getSignature().toLongString();
			Class<?> targetClass = proceedingJoinPoint.getTarget().getClass();

			Method method = null;
			Method[] methods = targetClass.getMethods();
			for(Method m : methods){
				log.info(m.toGenericString());
				log.info(m.toString());
				if(m.toGenericString().equals(methodName) ||
				m.toString().equals(methodName)){
					method = m;
					break;
				}
			}

			//????????????????????????
			if(Objects.requireNonNull(method).isAnnotationPresent(ProcessNode.class)){

				ProcessNode processPram =  method.getAnnotation(ProcessNode.class);
				Map<String,Object> map = new HashMap<>();
				Parameter[] parameters = method.getParameters();
				Object[] args = proceedingJoinPoint.getArgs();
				for (int i = 0, len = parameters.length; i < len; i++) {
					String name = parameters[i].getName();
					map.put(name,args[i]);
				}

				//??????????????????
				//TODO ???findFlowInfo????????????businessId ??? productId
				FlowInfo flowInfo = processNodeContext.findFlowInfo(proceedingJoinPoint,processPram,map);
				//plus??????????????????????????????????????????????????????????????????????????????????????????
				if(!flowInfo.isInProcess()){
					return proceedingJoinPoint.proceed();
				}

				//?????????serviceTask?????????????????????????????????
				if(flowInfo.isSameScope()){
					return proceedingJoinPoint.proceed();
				}

				//????????????
				if(StringUtils.isBlank(flowInfo.getProcessModelKey())
					&& StringUtils.isBlank(flowInfo.getProcessId())
				//TODO ?????????businessId?????????
				&& (StringUtils.isBlank(flowInfo.getBusinessId())
						|| StringUtils.isBlank(flowInfo.getProductId()))){
					throw new FlowableException("param error!");
				}

				if(flowInfo.isProcessStart()){
					//??????????????????????????????????????????????????????????????????processId
					if(StringUtils.isBlank(flowInfo.getProcessModelKey())){
						throw new FlowableException("processModelKey is necessary 4 process start!");
					}
					flowInfo.setProcessId(null);
				}


				//??????fields???mappingFields
				String[] mappingFields = processPram.mappingFields();
				String[] fieldPaths = processPram.fields();
				if(mappingFields.length > 0 && mappingFields.length != fieldPaths.length){
					throw new FlowableException("count of fields must be equal to count of mappingFields");
				}

				ProcessMetadata metadata;
				//????????????????????????
				if(flowInfo.isProcessStart()){
					metadata = new ProcessMetadata();
					metadata.setProcessDefinitionKey(flowInfo.getProcessModelKey());
					try{
						obj = getRes(proceedingJoinPoint, metadata);
					}catch (TaskRepeatException taskRepeatException){
						log.warn("start process : {} retry!", flowInfo.getBusinessId());
						obj = policy.doResume(proceedingJoinPoint);
					}
//					catch (Throwable e){
//						log.error(e.getMessage());
//						throw e;
//					}

					//???????????????????????????????????????????????????
					Map<String, Object> vars = createFieldMapping(obj, mappingFields, fieldPaths);
					if(StringUtils.isNotBlank(flowInfo.getTaskUser())){
						if(flowInfo.getTaskUser().startsWith(Constant.TASK_USER_PREFIX)){
							vars.put(Constant.TASK_USER, flowInfo.getTaskUser());
						}else {
							vars.put(Constant.TASK_USER,userContext.getUserById(flowInfo.getTaskUser()));
						}
					}
					//??????????????????????????????
					vars.put(Constant.PROCESS_BUSINESS_CONTEXT,JSON.toJSONString(obj));
					vars.put(Constant.PROCESS_BUSINESS_CONTEXT_CLASS,obj.getClass().toGenericString());

					//??????businessId
					if(StringUtils.isBlank(flowInfo.getBusinessId())){
						if(processPram.outputBusinessIdName().length == 0
								|| StringUtils.isBlank(processPram.productIdName()) ){
							throw new FlowableException("businessId and  is productId " +
									"necessary 4 process starting!");
						}
						MetaObject metaObject = new Configuration().newMetaObject(obj);
						//TODO ????????????header????????????
						String productId = flowInfo.getProductId();
						if(processPram.outputBusinessIdName().length > 0
								&& Constant.SELF_BUSINESS_ID.equals(processPram.outputBusinessIdName()[0])){
							flowInfo.setBusinessId(productId + ":" + obj);
						}else{
							StringBuilder businessId = new StringBuilder();
							for(String s : processPram.outputBusinessIdName()){
								businessId.append(metaObject.getValue(s)).append(":");
							}
							String id = productId + ":" + businessId.toString();
							flowInfo.setBusinessId(id.substring(0,id.length()-1));
						}
					}

					try {
						getMetadata(flowInfo, method,
								processPram.clazz().getCanonicalName(), vars);
					} catch(DuplicateBusinessIdException e){
						return obj;
					}finally {
						//???????????????
						processNodeContext.clearFlowInfo();
					}
					return obj;
				}else{
					//??????process???????????????
					metadata = getMetadata(flowInfo,method,
						processPram.clazz().getCanonicalName(),null);
					//??????task
					Task task = null;
					try {
						task = this.queryTask(metadata,userContext.getCurrentUser());
					}catch (NotCurrentProcessNodeException e) {
						log.error(e.getMessage());
						obj = proceedingJoinPoint.proceed();
						return obj;
					}

					//??????task ????????????
					TaskFormData formData = formService.getTaskFormData(task.getId());
					List<FormProperty> properties = formData.getFormProperties();
					metadata.setFormProperties(properties);
					try{
						obj = getRes(proceedingJoinPoint, metadata);
					}catch (TaskRepeatException taskRepeatException){
						log.warn("task : {} {} retry!",task.getTaskDefinitionKey(),task.getId());
						obj = policy.doResume(proceedingJoinPoint);
					}
//					catch (Throwable e){
//						log.error(e.getMessage());
//						throw e;
//					}

					//???????????????????????????????????????????????????
					Map<String, Object> vars = createFieldMapping(obj, mappingFields, fieldPaths);

					if(!StringUtils.isBlank(flowInfo.getBusinessId())){
						vars.put(Constant.BUSINESS_ID, flowInfo.getBusinessId());
					}

					//??????????????????????????????
					//????????????????????????????????????????????????
					//TODO ????????????????????????
					String taskUser = flowInfo.getTaskUser();
					if(StringUtils.isBlank(taskUser) && StringUtils.isNotBlank(processPram.outputTaskUserName())){
						MetaObject metaObject = new Configuration().newMetaObject(obj);
						taskUser = metaObject.getValue(processPram.outputTaskUserName()).toString();
						flowInfo.setTaskUser(taskUser);
					}
					if(StringUtils.isNotBlank(taskUser)){
						if(flowInfo.getTaskUser().startsWith(Constant.TASK_USER_PREFIX)){
							vars.put(Constant.TASK_USER, flowInfo.getTaskUser());
						}else {
							vars.put(Constant.TASK_USER,userContext.getUserById(flowInfo.getTaskUser()));
						}
					}else{
						vars.put(Constant.TASK_USER,userContext.getCurrentUser());
					}

					//?????????????????????
					doTask(metadata,task,vars);
				}

			}else {
				obj = proceedingJoinPoint.proceed();
			}
		}catch (Throwable throwable) {
			log.error(throwable.getMessage());
			throw new FlowableException(throwable.getMessage(),throwable);
		}finally {
			//??????????????????????????????????????????????????????????????????????????????????????????
			processNodeContext.clearFlowInfo();
		}

		return obj;
	}

	private Map<String, Object> createFieldMapping(Object obj, String[] mappingFields, String[] fieldPaths) {
		Map<String,Object> vars = new HashMap<>();
		vars.put(Constant.TASK_BUSINESS_CONTEXT,JSON.toJSONString(obj));
		vars.put(Constant.TASK_BUSINESS_CONTEXT_CLASS,obj.getClass().toGenericString());
		if(fieldPaths.length > 0){
			MetaObject metaObject = new Configuration().newMetaObject(obj);
			if(mappingFields.length == 0){
				for(String path : fieldPaths){
					//String key = path.substring(path.lastIndexOf(".") + 1);
					vars.put(path, metaObject.getValue(path));
				}
			}else {
				for(int i = 0; i < fieldPaths.length; i++){
					vars.put(mappingFields[i], metaObject.getValue(fieldPaths[i]));
				}
			}
		}
		return vars;
	}

	private Object getRes(ProceedingJoinPoint proceedingJoinPoint, ProcessMetadata metadata) throws Throwable {
		Object obj;
		PlusUtil.setMetadata(metadata);
		obj = proceedingJoinPoint.proceed();
		PlusUtil.clearMetadata();
		return obj;
	}


	private ProcessMetadata getMetadata(FlowInfo flowInfo, Method method,
										String interfaceName, Map<String,Object> vars){
		String processId;
		ProcessInstance process = null;
		if(flowInfo.isProcessStart()){
			//TODO ???????????????processInstance???businessId????????????,???????????????????????????????????????
			process = runtimeService.createProcessInstanceQuery()
					.processInstanceBusinessKey(flowInfo.getBusinessId()).active().singleResult();
			if(process != null){
				log.error("processInstance 4 businessId :"
						+ flowInfo.getBusinessId() + " is already exists!" );
				throw new DuplicateBusinessIdException("processInstance 4 businessId :"
						+ flowInfo.getBusinessId() + " is already exists!");
			}
			log.info("start Process name {}", flowInfo.getProcessModelKey());
			ProcessInstance processInstance;
			if(StringUtils.isBlank(flowInfo.getBusinessId())){
				throw new FlowableException("businessId  is necessary 4 process starting!");
			}
			//TODO ???????????????header??????????????????key??????
			//TODO ?????????????????????????????????key??????
			//TODO ????????????userContext.getCurrentUser()
			if(StringUtils.isNotBlank(flowInfo.getStartUser())){
				identityService.setAuthenticatedUserId(flowInfo.getStartUser());
			}else{
				identityService.setAuthenticatedUserId(userContext.getCurrentUser());
			}

			if(vars != null){
				processInstance = runtimeService.
					startProcessInstanceByKey(flowInfo.getProcessModelKey(),  flowInfo.getBusinessId(),vars);
			}else{
				processInstance = runtimeService.
					startProcessInstanceByKey(flowInfo.getProcessModelKey(),  flowInfo.getBusinessId());
			}

			processId = processInstance.getProcessInstanceId();
			flowInfo.setProcessId(processId);
			log.info("process instance created! process id : {}",processId);
		}else {

			if(StringUtils.isNotBlank(flowInfo.getProcessId())){
				 process = runtimeService.createProcessInstanceQuery()
						.processInstanceId(flowInfo.getProcessId()).active().singleResult();
			}else if(StringUtils.isNotBlank(flowInfo.getBusinessId())){
				process = runtimeService.createProcessInstanceQuery()
						.processInstanceBusinessKey(flowInfo.getBusinessId()).active().singleResult();
			}else{
				throw new FlowableException("processId and businessId are null");
			}

			if(process == null){
				throw new FlowableException("invalid processId or businessId");
			}
			processId = process.getProcessInstanceId();
			if(process.isEnded()){
				throw new FlowableException("process " + flowInfo.getProcessId() + " is ended!");
			}
		}
		ProcessMetadata metadata = new ProcessMetadata();
		metadata.setProcessInstanceId(processId);
		metadata.setProcessDefinitionKey(flowInfo.getProcessModelKey());
		//??????????????????????????????
		metadata.setTaskDefinitionKey(interfaceName + "." +method.getName());
		return metadata;
	}

	private void doTask(ProcessMetadata metadata,Task task,Map<String,Object> vars){
		if(task != null){
			processNodeContext.getCurrentFlowInfo().setSameScope(true);
			try{
				taskService.complete(task.getId(),vars);
				log.info("???????????? processId : {}, processKey : {}, taskKey : {}",metadata.getProcessInstanceId()
						,metadata.getProcessDefinitionKey(),metadata.getTaskDefinitionKey());
			}finally {
				//???????????????
				processNodeContext.clearFlowInfo();
			}
		}
	}

	private Task queryTask(ProcessMetadata metadata,String user){
		Task task = null;
		if(StringUtils.isNotBlank(metadata.getProcessDefinitionKey())){
			List<Task> tasks = taskService.createTaskQuery()
				.processDefinitionKey(metadata.getProcessDefinitionKey())
				.processInstanceId(metadata.getProcessInstanceId())
				.taskAssignee(user)
				.taskDefinitionKey(metadata.getTaskDefinitionKey()).active()
				.list();
			if(tasks != null && tasks.size() > 0){
				task = tasks.get(0);
			}
		} else{
			List<Task> tasks = taskService.createTaskQuery()
				.processInstanceId(metadata.getProcessInstanceId())
				.taskDefinitionKey(metadata.getTaskDefinitionKey()).active()
				.taskAssignee(user)
				.list();
			if(tasks != null && tasks.size() > 0){
				task = tasks.get(0);
			}
		}

		if(task == null){
			throw new NotCurrentProcessNodeException("no active task 4 process instance "+ metadata.getProcessInstanceId());
		}

		return task;
	}

}
