package org.coderthoughts.service.spector;

import java.lang.reflect.Method;

public interface ServiceAspect {
    void preServiceInvoke(Object service, Method method, Object[] args) throws Exception;
    void postServiceInvoke(Object service, Method method, Object[] args, Object result) throws Exception;

    void report();
}
