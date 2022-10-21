package pers.gary.flowable.plus.common.entity.excption;

public class DuplicateBusinessIdException extends RuntimeException{
    public DuplicateBusinessIdException(String message, Throwable cause) {
        super(message, cause);
    }

    public DuplicateBusinessIdException(String message) {
        super(message);
    }

    public DuplicateBusinessIdException(Throwable cause) {
        super(cause);
    }
}
