package pers.gary.flowable.plus.common.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ProcessNode {

	/**
	 * service方法返回对象中需要往下传的字段路径,网关表达式默认取全路径
	 * 比如：service方法返回对象的字段路径为key1.key2,则网关上下文表达式中的key为key1.key2
	 * @return String类型数组
	 */
	String[] fields() default{};

	/**
	 * 当前方法来源于哪一个service接口
	 * @return 类对象
	 */
	Class<?> clazz();


	/**
	 * <p>字段映射的key列表，比如：设fields属性列表为["name1","name2.name3"],该属性列表为["name1","name4"]</p>
	 * <p>那么，流程变量中的属性（map）形如：{“name1”:"xxxx","name4":"yyyy"}</p>
	 * @return String类型数组
	 */
	String[] mappingFields() default{};

	/**
	 * <p>业务方法businessId字段名称</p>
	 * <p>流程启动时从业务方法返回值中获取</p>
	 * <p>如果businessId由多个字段组成，那么将按照数组中的顺序依次取出字段的值并使用“:”连接<p/>
	 * @return String类型
	 */
	String[] outputBusinessIdName() default {};


	/**
	 * <p>业务方法businessId字段名称</p>
	 * <p>流程启动时从业务方法返回值中获取</p>
	 * <p>如果businessId由多个字段组成，那么将按照数组中的顺序依次取出字段的值并使用“:”连接<p/>
	 * @return String类型
	 */
	String[] inputBusinessIdName() default {};


	/**
	 * <p>从请求参数拿应用标识productId字段名称</p>
	 * @return String类型
	 */
	String productIdName() default "";

	/**
	 *<p>模型标识processModelKey字段名称</p>
	 * @return String类型
	 */
	String processModelKeyName() default "";

	/**
	 * 下一任务执行用户id字段名称,用于bpmn图中配置${taskUser}
	 * @return String类型
	 */
	String inputTaskUserName() default "";

	/**
	 * 下一任务执行用户id字段名称,用于bpmn图中配置${taskUser}
	 * @return String类型
	 */
	String outputTaskUserName() default "";

	/**
	 * 任务发起者的字段名称，优先级（序号越小优先级越高）如下：
	 * 1、先从配置的header里按照配置的key获取
	 * 2、从输入参数中获取
	 * 3、使用userContext.getCurrentUser()方法获取
	 * @return String类型
	 */
	String startUserName() default "";

}
