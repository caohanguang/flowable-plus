package pers.gary.flowable.plus.config;

/**
 * 任务类型枚举
 */
public enum FlowEnum {

	PROCESS_MODEL_KEY("processModelKey"), PROCESS_ID("processId")
	,TASK_USER("taskUser"),BUSINESS_ID("businessId"),
	//PROCESS_START("processStart"),
	BUSINESS_KEY("businessKey"),PRODUCT_ID("productId"),START_USER("startUser");
	private String name;
	 FlowEnum(String name){
		this.name = name;
	}

	public String getName(){
		 return this.name;
	}
}
