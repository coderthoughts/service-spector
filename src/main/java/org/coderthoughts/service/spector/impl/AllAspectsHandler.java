package org.coderthoughts.service.spector.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;

import org.coderthoughts.service.spector.ServiceAspect;

class AllAspectsHandler implements InvocationHandler {
    // This is a concurrent list that is dynamically updated with the available services
    private final List<ServiceAspect> aspectServices;

    // The original service
    private final Object original;

    public AllAspectsHandler(List<ServiceAspect> aspects, Object obj) {
        aspectServices = aspects;
        original = obj;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> declaringClass = method.getDeclaringClass();
        boolean objectMethod = declaringClass.equals(Object.class);

        if (!objectMethod) {
            for (ServiceAspect aspect : aspectServices) {
                try {
                    aspect.preServiceInvoke(original, method, args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        Object res = method.invoke(original, args);

        if (!objectMethod) {
            for (ServiceAspect aspect : aspectServices) {
                try {
                    aspect.postServiceInvoke(original, method, args, res);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return res;
    }
}