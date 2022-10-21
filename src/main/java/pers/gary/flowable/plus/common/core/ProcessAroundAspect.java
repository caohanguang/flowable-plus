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
			//获取ProcessNode注解
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

			//业务引擎处理方式
			if(Objects.requireNonNull(method).isAnnotationPresent(ProcessNode.class)){

				ProcessNode processPram =  method.getAnnotation(ProcessNode.class);
				Map<String,Object> map = new HashMap<>();
				Parameter[] parameters = method.getParameters();
				Object[] args = proceedingJoinPoint.getArgs();
				for (int i = 0, len = parameters.length; i < len; i++) {
					String name = parameters[i].getName();
					map.put(name,args[i]);
				}

				//输入参数获取
				//TODO 在findFlowInfo里面获取businessId 和 productId
				FlowInfo flowInfo = processNodeContext.findFlowInfo(proceedingJoinPoint,processPram,map);
				//plus插件可以自动判断当前方法的执行是否是在流程引擎的一个上下文中
				if(!flowInfo.isInProcess()){
					return proceedingJoinPoint.proceed();
				}

				//如果是serviceTask走到这里就直接出返回了
				if(flowInfo.isSameScope()){
					return proceedingJoinPoint.proceed();
				}

				//参数校验
				if(StringUtils.isBlank(flowInfo.getProcessModelKey())
					&& StringUtils.isBlank(flowInfo.getProcessId())
				//TODO 加入对businessId的支持
				&& (StringUtils.isBlank(flowInfo.getBusinessId())
						|| StringUtils.isBlank(flowInfo.getProductId()))){
					throw new FlowableException("param error!");
				}

				if(flowInfo.isProcessStart()){
					//注意这里，这个分支自动忽略的启动流程时传入的processId
					if(StringUtils.isBlank(flowInfo.getProcessModelKey())){
						throw new FlowableException("processModelKey is necessary 4 process start!");
					}
					flowInfo.setProcessId(null);
				}


				//校验fields和mappingFields
				String[] mappingFields = processPram.mappingFields();
				String[] fieldPaths = processPram.fields();
				if(mappingFields.length > 0 && mappingFields.length != fieldPaths.length){
					throw new FlowableException("count of fields must be equal to count of mappingFields");
				}

				ProcessMetadata metadata;
				//启动流程不做任务
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

					//封装网关表达式所需变量及业务上下文
					Map<String, Object> vars = createFieldMapping(obj, mappingFields, fieldPaths);
					if(StringUtils.isNotBlank(flowInfo.getTaskUser())){
						if(flowInfo.getTaskUser().startsWith(Constant.TASK_USER_PREFIX)){
							vars.put(Constant.TASK_USER, flowInfo.getTaskUser());
						}else {
							vars.put(Constant.TASK_USER,userContext.getUserById(flowInfo.getTaskUser()));
						}
					}
					//注意：这个是流程变量
					vars.put(Constant.PROCESS_BUSINESS_CONTEXT,JSON.toJSONString(obj));
					vars.put(Constant.PROCESS_BUSINESS_CONTEXT_CLASS,obj.getClass().toGenericString());

					//校验businessId
					if(StringUtils.isBlank(flowInfo.getBusinessId())){
						if(processPram.outputBusinessIdName().length == 0
								|| StringUtils.isBlank(processPram.productIdName()) ){
							throw new FlowableException("businessId and  is productId " +
									"necessary 4 process starting!");
						}
						MetaObject metaObject = new Configuration().newMetaObject(obj);
						//TODO 这里注意header的优先级
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
						//清理上下文
						processNodeContext.clearFlowInfo();
					}
					return obj;
				}else{
					//获取process任务元数据
					metadata = getMetadata(flowInfo,method,
						processPram.clazz().getCanonicalName(),null);
					//获取task
					Task task = null;
					try {
						task = this.queryTask(metadata,userContext.getCurrentUser());
					}catch (NotCurrentProcessNodeException e) {
						log.error(e.getMessage());
						obj = proceedingJoinPoint.proceed();
						return obj;
					}

					//获取task 表单属性
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

					//封装网关表达式所需变量及业务上下文
					Map<String, Object> vars = createFieldMapping(obj, mappingFields, fieldPaths);

					if(!StringUtils.isBlank(flowInfo.getBusinessId())){
						vars.put(Constant.BUSINESS_ID, flowInfo.getBusinessId());
					}

					//指定下一步处理用户。
					//没有传入的情况下默认使用当前用户
					//TODO 以下代码需要重构
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

					//处理业务流任务
					doTask(metadata,task,vars);
				}

			}else {
				obj = proceedingJoinPoint.proceed();
			}
		}catch (Throwable throwable) {
			log.error(throwable.getMessage());
			throw new FlowableException(throwable.getMessage(),throwable);
		}finally {
			//在同一个线程中，流程业务执行过程中任何一步报错直接清理上下文
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
			//TODO 去重校验，processInstance与businessId一一对应,这里的代码写的乱，后续重构
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
			//TODO 先从配置的header里按照配置的key获取
			//TODO 从输入参数中按照配置的key获取
			//TODO 默认调用userContext.getCurrentUser()
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
		//当前节点是哪一个任务
		metadata.setTaskDefinitionKey(interfaceName + "." +method.getName());
		return metadata;
	}

	private void doTask(ProcessMetadata metadata,Task task,Map<String,Object> vars){
		if(task != null){
			processNodeContext.getCurrentFlowInfo().setSameScope(true);
			try{
				taskService.complete(task.getId(),vars);
				log.info("处理任务 processId : {}, processKey : {}, taskKey : {}",metadata.getProcessInstanceId()
						,metadata.getProcessDefinitionKey(),metadata.getTaskDefinitionKey());
			}finally {
				//清理上下文
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
