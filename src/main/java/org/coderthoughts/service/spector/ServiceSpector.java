package org.coderthoughts.service.spector;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component
public class ServiceSpector {
    @Activate
    private void activate() {
        System.out.println("@@@@@@@@@@@@@@@@");
    }
}
