package org.coderthoughts.service.spector.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.coderthoughts.service.spector.ServiceAspect;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

class AllAspectsHandler implements InvocationHandler {
    private final BundleContext bundleContext;
    private final ServiceSpector serviceSpector;

    // A reference to the original service
    private final ServiceReference<?> original;

    AllAspectsHandler(BundleContext bc, ServiceSpector spector, ServiceReference<?> ref) {
        bundleContext = bc;
        serviceSpector = spector;
        original = ref;
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

        Object res = null;
        try {
            Object svc = bundleContext.getService(original);
            res = method.invoke(svc, args);
        } finally {
            bundleContext.ungetService(original);
        }

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