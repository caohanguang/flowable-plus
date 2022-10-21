package pers.gary.flowable.plus.common.core;

import org.aspectj.lang.ProceedingJoinPoint;

public interface RetryPolicy {
    Object doResume(ProceedingJoinPoint proceedingJoinPoint);
}
