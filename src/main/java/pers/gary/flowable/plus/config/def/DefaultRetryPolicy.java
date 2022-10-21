package pers.gary.flowable.plus.config.def;

import pers.gary.flowable.plus.common.core.RetryPolicy;
import org.aspectj.lang.ProceedingJoinPoint;

public class DefaultRetryPolicy implements RetryPolicy {
    @Override
    public Object doResume(ProceedingJoinPoint proceedingJoinPoint) {
        throw new UnsupportedOperationException("not support 4 findProcessModelKeyByBusinessKey");
    }
}
