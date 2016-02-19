package org.coderthoughts.service.spector;

import java.lang.reflect.Method;

public interface ServiceAspect {
    void preServiceInvoke(Object service, Method method, Object[] args) throws Exception;

    // The postServiceInvoke will be called on the same thread as the preServiceInvoke
    void postServiceInvoke(Object service, Method method, Object[] args, Object result) throws Exception;

    void report();
}
