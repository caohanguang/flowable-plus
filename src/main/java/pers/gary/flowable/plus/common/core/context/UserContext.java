package pers.gary.flowable.plus.common.core.context;

/**
 * 用户上下文策略接口,可扩展为token、 session、JNDI、DB、LDAP等用户数据源
 */
public interface UserContext {
	/**
	 * 从数据源获取当前用户，格式为taskUser_xxx
	 * @return 格式化用户
	 */
	String getCurrentUser();

	/**
	 * 拼接用户，格式为taskUser_xxx
	 * @return 格式化用户
	 */
	String getUserById(String id);
}
