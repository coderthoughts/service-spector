package org.coderthoughts.service.spector;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;

public class HidingHook implements FindHook, EventListenerHook {
    private final long myBundleId;
    private final Map<ServiceReference<?>, ServiceRegistration<?>> managedServices;

    HidingHook(BundleContext bc, Map<ServiceReference<?>, ServiceRegistration<?>> managed) {
        myBundleId = bc.getBundle().getBundleId();
        managedServices = managed;
    }

    @Override
    public void find(BundleContext context, String name, String filter, boolean allServices,
            Collection<ServiceReference<?>> references) {
        long id = context.getBundle().getBundleId();
        if (id == 0 || id == myBundleId)
            return;

        for (Iterator<ServiceReference<?>> it = references.iterator(); it.hasNext(); ) {
            ServiceReference<?> ref = it.next();
            if (managedServices.containsKey(ref))
                it.remove();
        }
    }

    @Override
    public void event(ServiceEvent event, Map<BundleContext, Collection<ListenerInfo>> listeners) {
        ServiceReference<?> ref = event.getServiceReference();

        for (Iterator<BundleContext> it = listeners.keySet().iterator(); it.hasNext(); ) {
            BundleContext bc = it.next();
            long id = bc.getBundle().getBundleId();
            if (id == 0 || id == myBundleId)
                continue;

            if (managedServices.containsKey(ref))
                it.remove();
        }
    }
}
