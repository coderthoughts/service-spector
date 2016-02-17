package org.coderthoughts.service.spector;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(immediate=true)
public class ServiceSpector implements ServiceListener {
    private static final String PROXY_SERVICE_PROP = ".service.spector.proxy";

    BundleContext bundleContext;
    Config config;
    List<Filter> filters;
    Map<ServiceReference<?>, ServiceRegistration<?>> managed = new ConcurrentHashMap<>();
    Map<String, LongAdder> invocationCounts = new ConcurrentHashMap<>();

    @Activate
    private void activate(BundleContext bc, Config cfg) {
        bundleContext = bc;
        config = cfg;

        List<Filter> fl = new ArrayList<>(cfg.service_filters().length);
        for (String f : cfg.service_filters()) {
            try {
                fl.add(bundleContext.createFilter(f));
            } catch (InvalidSyntaxException e) {
                e.printStackTrace();
            }
        }
        filters = Collections.unmodifiableList(fl);

        bundleContext.addServiceListener(this);

        System.out.println("%%@@@@@@@@@@@@@@@@");
        System.out.println("Service filters: " + Arrays.toString(cfg.service_filters()));
        System.out.println("Hide services: " + cfg.hide_services());
    }

    @Deactivate
    private void deactivate() {
        bundleContext.removeServiceListener(this);
        bundleContext = null;
        config = null;
        filters = null;
        for (ServiceRegistration<?> sr : managed.values()) {
            sr.unregister();
        }

        System.out.println("Invocation counts");
        System.out.println("=================");
        for (Map.Entry<String, LongAdder> entry : invocationCounts.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        ServiceReference<?> ref = event.getServiceReference();
        switch (event.getType()) {
        case ServiceEvent.MODIFIED:
            ServiceRegistration<?> reg = managed.remove(ref);
            if (reg != null) {
                reg.unregister();
                bundleContext.ungetService(ref);
            }
            // no break, continue as with REGISTERED
        case ServiceEvent.REGISTERED:
            if (managed.get(ref) == null && ref.getProperty(PROXY_SERVICE_PROP) == null) {
                Object svc = bundleContext.getService(ref);
                for (Filter filter : filters) {
                    if (filter.match(ref)) {
                        managed.put(ref, registerProxy(ref, svc));
                    }
                }
            }
            break;
        case ServiceEvent.MODIFIED_ENDMATCH:
        case ServiceEvent.UNREGISTERING:
            ServiceRegistration<?> reg2 = managed.remove(ref);
            if (reg2 != null) {
                reg2.unregister();
                bundleContext.ungetService(ref);
            }
            break;
        }
    }

    private ServiceRegistration<?> registerProxy(ServiceReference<?> ref, Object svc) {
        String[] objectClass = null;
        Integer ranking = 0;

        Dictionary<String, Object> newProps = new Hashtable<>();
        for (String key : ref.getPropertyKeys()) {
            Object val = ref.getProperty(key);
            switch(key) {
            case Constants.SERVICE_ID:
            case Constants.SERVICE_PID:
                break;
            case Constants.SERVICE_RANKING:
                if (val instanceof Integer)
                    ranking = (Integer) val;
                break;
            case Constants.OBJECTCLASS:
                if (val instanceof String[])
                    objectClass = (String[]) val;
                break;
            default:
                newProps.put(key, ref.getProperty(key));
                break;
            }
        }
        ranking++;

        newProps.put(Constants.SERVICE_RANKING, ranking);
        newProps.put(PROXY_SERVICE_PROP, Boolean.TRUE);

        List<Class<?>> interfaces = new ArrayList<>();
        for (String intf : objectClass) {
            try {
                Class<?> cls = ref.getBundle().loadClass(intf);
                if (!cls.isInterface())
                    continue;
                interfaces.add(cls);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        Object proxy = createProxy(ref.getBundle(), interfaces, svc);
        return bundleContext.registerService(
                interfaces.stream().map(Class::getName).toArray(String[]::new),
                proxy, newProps);
    }

    private Object createProxy(Bundle bundle, List<Class<?>> interfaces, Object svc) {
        BundleWiring bw = bundle.adapt(BundleWiring.class);
        ClassLoader cl = bw.getClassLoader();

        return Proxy.newProxyInstance(cl, interfaces.toArray(new Class[]{}), new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // TODO make better
                String key = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
                LongAdder la = new LongAdder();
                LongAdder prev = invocationCounts.putIfAbsent(key, la);
                if (prev != null)
                    la = prev;
                la.increment();
                return method.invoke(svc, args);
            }
        });
    }

    @interface Config {
        String [] service_filters() default {};
        boolean hide_services() default false;
    }
}