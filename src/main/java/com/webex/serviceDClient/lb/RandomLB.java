package com.webex.serviceDClient.lb;

import com.webex.serviceDClient.entity.HostEntity;

import java.util.Collections;
import java.util.List;

public class RandomLB implements LB {
    @Override
    public HostEntity get(List<HostEntity> services) {
        if (services == null || services.isEmpty()) {
            return null;
        }
        Collections.shuffle(services);
        return services.get(0);
    }
}
