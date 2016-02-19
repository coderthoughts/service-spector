package org.coderthoughts.service.spector.impl;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.coderthoughts.service.spector.ServiceAspect;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

@Component(scope=ServiceScope.PROTOTYPE)
public class CountingServiceAspect implements ServiceAspect {
    Map<String, LongAdder> invocationCounts = new ConcurrentHashMap<>();

    @Override
    public void preServiceInvoke(Object service, Method method, Object[] args) throws Exception {
        Class<?> declaringClass = method.getDeclaringClass();
        String key = declaringClass.getSimpleName() + "#" + method.getName();
        invocationCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    @Override
    public void postServiceInvoke(Object service, Method method, Object[] args, Object result) throws Exception {
        // nothing to do
    }

    @Override
    public void report() {
        System.out.println("Invocation counts");
        System.out.println("=================");
        for (Map.Entry<String, LongAdder> entry : invocationCounts.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println();
    }
}
