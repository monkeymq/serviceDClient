package com.webex.serviceDClient.lb;

import com.webex.serviceDClient.entity.HostEntity;

import java.util.List;

public interface LB {
    HostEntity get(List<HostEntity> services);
}
