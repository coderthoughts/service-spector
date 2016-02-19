package org.coderthoughts.service.spector.impl;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.coderthoughts.service.spector.ServiceAspect;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

@Component(immediate=true)
public class ServiceSpector implements ServiceListener {
    // This property is put on the proxy so that it won't be proxied a second time
    private static final String PROXY_SERVICE_PROP = ".service.spector.proxy";

    BundleContext bundleContext;
    List<Filter> filters;
    ServiceRegistration<?> hookReg;
    final Map<ServiceReference<?>, ServiceRegistration<?>> managed = new ConcurrentHashMap<>();
    final List<ServiceAspect> aspects = new CopyOnWriteArrayList<>();

    @Activate
    private synchronized void activate(BundleContext bc, Config cfg) {
        bundleContext = bc;
        if (cfg == null || cfg.service_filters() == null)
            return;

        if (cfg.hide_services()) {
            hookReg = bundleContext.registerService(new String[] {FindHook.class.getName(), EventListenerHook.class.getName()},
                new HidingHook(bundleContext, managed), null);
        }

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
        visitExistingServices();

        System.out.println("Service Spector");
        System.out.println("===============");
        System.out.println("Service filters: " + Arrays.toString(cfg.service_filters()));
        System.out.println("Hide services: " + cfg.hide_services());
    }

    @Deactivate
    private synchronized void deactivate() {
        bundleContext.removeServiceListener(this);
        filters = null;
        for (Map.Entry<ServiceReference<?>, ServiceRegistration<?>> entry : managed.entrySet()) {
            entry.getValue().unregister();
            bundleContext.ungetService(entry.getKey());
        }
        managed.clear();
        bundleContext = null;

        if (hookReg != null) {
            hookReg.unregister();
            hookReg = null;
        }
    }

    @Reference(policy=ReferencePolicy.DYNAMIC, cardinality=ReferenceCardinality.AT_LEAST_ONE)
    void bindServiceAspect(ServiceAspect sa) {
        aspects.add(sa);
    }

    void unbindServiceAspect(ServiceAspect sa) {
        aspects.remove(sa);
        sa.report();
    }

    List<ServiceAspect> getAspects() {
        return aspects;
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        ServiceReference<?> ref = event.getServiceReference();
        switch (event.getType()) {
        case ServiceEvent.MODIFIED:
            handleRemovedService(ref);
            // no break, continue as with REGISTERED
        case ServiceEvent.REGISTERED:
            handleNewService(ref);
            break;
        case ServiceEvent.MODIFIED_ENDMATCH:
        case ServiceEvent.UNREGISTERING:
            handleRemovedService(ref);
            break;
        }
    }

    private void handleRemovedService(ServiceReference<?> ref) {
        ServiceRegistration<?> reg2 = managed.remove(ref);
        if (reg2 != null) {
            reg2.unregister();
            bundleContext.ungetService(ref);
        }
    }

    private void handleNewService(ServiceReference<?> ref) {
        if (managed.get(ref) == null && ref.getProperty(PROXY_SERVICE_PROP) == null) {
            Object svc = bundleContext.getService(ref);
            for (Filter filter : filters) {
                if (filter.match(ref)) {
                    managed.put(ref, registerProxy(ref, svc));
                }
            }
        }
    }

    private void visitExistingServices() {
        for (Filter filter : filters) {
            try {
                ServiceReference<?>[] refs = bundleContext.getServiceReferences((String) null, filter.toString());
                if (refs != null) {
                    for (ServiceReference<?> ref : refs) {
                        handleNewService(ref);
                    }
                }
            } catch (InvalidSyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    private ServiceRegistration<?> registerProxy(ServiceReference<?> originalRef, Object svc) {
        String[] objectClass = null;
        Integer ranking = 0;

        Dictionary<String, Object> newProps = new Hashtable<>();
        for (String key : originalRef.getPropertyKeys()) {
            Object val = originalRef.getProperty(key);
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
                newProps.put(key, originalRef.getProperty(key));
                break;
            }
        }
        ranking++;

        // Register the service with the new higher ranking
        newProps.put(Constants.SERVICE_RANKING, ranking);

        // Always register as a prototype scope service, this will also work for proxied
        // services that are non-prototype
        newProps.put(Constants.SERVICE_SCOPE, Constants.SCOPE_PROTOTYPE);

        // This property is set to recognize the registration as a proxy, so it's not
        // proxied again
        newProps.put(PROXY_SERVICE_PROP, Boolean.TRUE);

        List<Class<?>> interfaces = new ArrayList<>();
        for (String intf : objectClass) {
            try {
                Class<?> cls = originalRef.getBundle().loadClass(intf);
                if (!cls.isInterface())
                    continue;
                interfaces.add(cls);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        PrototypeServiceFactory<Object> psf = new PrototypeServiceFactory<Object>() {
            @Override
            public Object getService(Bundle bundle, ServiceRegistration<Object> registration) {
                Object originalService = bundle.getBundleContext().getService(originalRef);
                return createProxy(originalRef.getBundle(), interfaces, originalService);
            }

            @Override
            public void ungetService(Bundle bundle, ServiceRegistration<Object> registration, Object service) {
                bundle.getBundleContext().ungetService(originalRef);
            }
        };

        return originalRef.getBundle().getBundleContext().registerService(
                interfaces.stream().map(Class::getName).toArray(String[]::new),
                psf, newProps);
    }

    private Object createProxy(Bundle bundle, List<Class<?>> interfaces, Object svc) {
        BundleWiring bw = bundle.adapt(BundleWiring.class);
        ClassLoader cl = bw.getClassLoader();
        return Proxy.newProxyInstance(cl,
                interfaces.toArray(new Class[]{}), new AllAspectsHandler(this, svc));
    }

    @interface Config {
        String [] service_filters();
        boolean hide_services() default false;
    }
}
