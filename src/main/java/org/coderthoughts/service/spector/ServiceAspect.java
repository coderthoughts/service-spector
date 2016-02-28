package org.coderthoughts.service.spector;

import java.lang.reflect.Method;

import org.osgi.framework.ServiceReference;

/**
 * An aspect that can be added to services. The aspect will be called before and after service
 * invocations.
 * Additionally aspects can announce themselves and will be called to report their results at
 * the end of a run.
 */
public interface ServiceAspect {
    /**
     * The aspect can announce itself.
     * @return The announcement message, if any.
     */
    String announce();

    /**
     * Called before a service is invoked. A matching {@link #postServiceInvoke} will be made on the
     * same thread after this service invocation has completed.
     * @param service The service reference of the service that will be invoked.
     * @param method The method that is going to be invoked.
     * @param args The arguments to the method.
     */
    void preServiceInvoke(ServiceReference<?> service, Method method, Object[] args);

    /**
     * Called after the service is invoked. A matching {@link #preServiceInvoke} has been made on the
     * same thread before the service invocation is made.
     * @param service The service reference of the service that has been invoked.
     * @param method The method has has been invoked.
     * @param args The arguments to the method.
     * @param result The result of the service invocation.
     */
    void postServiceInvoke(ServiceReference<?> service, Method method, Object[] args, Object result);

    /**
     * The aspect should report its findings.
     * @return The report message.
     */
    String report();
}
