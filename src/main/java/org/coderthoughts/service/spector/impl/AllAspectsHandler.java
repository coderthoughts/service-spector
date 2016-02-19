package org.coderthoughts.service.spector.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.coderthoughts.service.spector.ServiceAspect;

class AllAspectsHandler implements InvocationHandler {
    private final ServiceSpector serviceSpector;

    // The original service
    private final Object original;

    public AllAspectsHandler(ServiceSpector spector, Object obj) {
        serviceSpector = spector;
        original = obj;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> declaringClass = method.getDeclaringClass();
        boolean objectMethod = declaringClass.equals(Object.class);

        if (!objectMethod) {
            for (ServiceAspect aspect : serviceSpector.getAspects()) {
                try {
                    aspect.preServiceInvoke(original, method, args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        Object res = method.invoke(original, args);

        if (!objectMethod) {
            for (ServiceAspect aspect : serviceSpector.getAspects()) {
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