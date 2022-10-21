package pers.gary.flowable.plus.config.def;

import pers.gary.flowable.plus.common.core.context.UserContext;

public class DefaultUserContext implements UserContext {
    @Override
    public String getCurrentUser() {
        return "";
    }

    @Override
    public String getUserById(String id) {
        return id;
    }
}
