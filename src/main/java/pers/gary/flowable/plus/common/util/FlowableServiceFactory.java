package pers.gary.flowable.plus.common.util;

class FlowableServiceFactory {
	static <T> T getServiceByType(Class<T> clazz){
		return ApplicationContextUtil.getStringApplicationContext().getBean(clazz);
	}
}
