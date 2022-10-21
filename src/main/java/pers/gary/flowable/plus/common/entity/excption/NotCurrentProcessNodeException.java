package pers.gary.flowable.plus.common.entity.excption;

/**
 * 配置了@ProcessNode注解的service方法，但不是当前任务节点，此异常不会阻止业务正常进行，只会记录日志
 */
public class NotCurrentProcessNodeException extends RuntimeException{
    public NotCurrentProcessNodeException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotCurrentProcessNodeException(String message) {
        super(message);
    }

    public NotCurrentProcessNodeException(Throwable cause) {
        super(cause);
    }
}
