package org.coderthoughts.service.spector.impl;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.coderthoughts.service.spector.ServiceAspect;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

@Component(scope=ServiceScope.PROTOTYPE)
public class CountingServiceAspect implements ServiceAspect {
    Map<String, LongAdder> invocationCounts = new ConcurrentHashMap<>();

    @Override
    public String announce() {
        return "Invocation counting started.\n";
    }

    @Override
    public void preServiceInvoke(ServiceReference<?> service, Method method, Object[] args) {
        Class<?> declaringClass = method.getDeclaringClass();
        String key = declaringClass.getSimpleName() + "#" + method.getName();
        invocationCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    @Override
    public void postServiceInvoke(ServiceReference<?> service, Method method, Object[] args, Object result) {
        // nothing to do
    }

    @Override
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("Invocation counts\n");
        sb.append("=================\n");
        for (Map.Entry<String, LongAdder> entry : invocationCounts.entrySet()) {
            sb.append(entry.getKey() + ": " + entry.getValue() + "\n");
        }
        return sb.toString();
    }
}
