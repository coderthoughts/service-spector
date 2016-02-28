package org.coderthoughts.service.spector.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;

class HidingHook implements FindHook, EventListenerHook {
    private final long myBundleId;
    private final ServiceSpector serviceSpector;

    HidingHook(BundleContext bc, ServiceSpector spector) {
        myBundleId = bc.getBundle().getBundleId();
        serviceSpector = spector;
    }

    @Override
    public void find(BundleContext context, String name, String filter, boolean allServices,
            Collection<ServiceReference<?>> references) {
        long id = context.getBundle().getBundleId();

        // Don't hide from me not the system bundle.
        if (id == 0 || id == myBundleId)
            return;

        for (Iterator<ServiceReference<?>> it = references.iterator(); it.hasNext(); ) {
            ServiceReference<?> ref = it.next();
            if (serviceSpector.managed.containsKey(ref))
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

            if (serviceSpector.managed.containsKey(ref))
                it.remove();
        }
    }
}
