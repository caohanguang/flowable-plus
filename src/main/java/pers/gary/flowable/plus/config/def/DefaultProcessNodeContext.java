package pers.gary.flowable.plus.config.def;

import pers.gary.flowable.plus.common.core.context.AbstractProcessNodeContext;

public class DefaultProcessNodeContext extends AbstractProcessNodeContext {

    @Override
    public String findProcessModelKeyByBusinessKey(String businessKey) {
        throw new UnsupportedOperationException("not support 4 findProcessModelKeyByBusinessKey");
    }
}
