package com.webex.serviceDClient.lb;

import com.webex.serviceDClient.entity.HostEntity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobin implements LB {
    private AtomicInteger index = new AtomicInteger(0);

    @Override
    public HostEntity get(List<HostEntity> services) {
        if (services == null || services.isEmpty()) {
            return null;
        }
        Collections.sort(services);
        int len = services.size();
        int currentIndex = index.get() % len;
        index.set(currentIndex + 1);
        return services.get(currentIndex);
    }
}
