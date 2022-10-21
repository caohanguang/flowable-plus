package pers.gary.flowable.plus.common.entity.excption;

/**
 * plus api的参数异常
 */
public class ArgumentException extends RuntimeException{
	public ArgumentException(String message, Throwable cause) {
		super(message, cause);
	}

	public ArgumentException(String message) {
		super(message);
	}

	public ArgumentException(Throwable cause) {
		super(cause);
	}
}
