package com.webex.serviceDClient;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.webex.serviceDClient.entity.HostEntity;
import com.webex.serviceDClient.lb.LB;
import com.webex.serviceDClient.lb.RoundRobin;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServiceDClient {
    private static Logger logger = LoggerFactory.getLogger(ServiceDClient.class);
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(2);
    private List<HostEntity> serviceDNodes;
    private Set<HostEntity> notAvailableServiceDNodes = new HashSet<>();
    private final ReentrantReadWriteLock RWLOCK = new ReentrantReadWriteLock();
    private final ConcurrentHashMap<String, HostEntity> lastAvailableService = new ConcurrentHashMap<>();
    private LB lb;
    private HostEntity leaderServiceD;

    public ServiceDClient(Set<HostEntity> serviceDNodes) {
        this(serviceDNodes, new RoundRobin());
    }

    public ServiceDClient(Set<HostEntity> serviceDNodes, LB lb) {
        if (serviceDNodes == null || serviceDNodes.isEmpty()) {
            throw new IllegalArgumentException("serviceDNodes is null");
        }
        this.serviceDNodes = new ArrayList<>(serviceDNodes);
        this.lb = lb;
        this.leaderServiceD = this.serviceDNodes.get(1);
    }

    public void register(String serviceName, HostEntity service) {
        if (Utils.isBlank(serviceName)) {
            throw new IllegalArgumentException("serviceName is null");
        }

        if (service == null || Utils.isBlank(service.getIp())) {
            throw new IllegalArgumentException("service is null");
        }


        SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(() -> {
            HostEntity serviceD = this.leaderServiceD;
            if (serviceD != null) {
                try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                    String param = "key=" + URLEncoder.encode(serviceName, "UTF-8") + "&value=" + URLEncoder.encode(JSONObject.toJSONString(service), "UTF-8");
                    String setURL = "http://" + serviceD + "/set?" + param;
                    HttpGet register = new HttpGet(setURL);
                    try (CloseableHttpResponse response = httpclient.execute(register)) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            HttpEntity entity = response.getEntity();
                            if (entity != null) {
                                String r = EntityUtils.toString(entity);
                                if (r == null || !r.equalsIgnoreCase("ok")) {
                                    verifyLeader();
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    disableServiceDNode(serviceD);
                }
            }
        }, 0, 5, TimeUnit.SECONDS);

    }

    public synchronized HostEntity getAvailableService(String serviceName) {
        if (Utils.isBlank(serviceName)) {
            throw new IllegalArgumentException("serviceName is null");
        }
        HostEntity serviceD = pickOneServiceD();
        if (!lastAvailableService.containsKey(serviceName)) {
            verifyLeader();
            serviceD = this.leaderServiceD;
        }
        if (serviceD != null) {
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                String getURL = "http://" + serviceD.toString() + "/get?key=" + URLEncoder.encode(serviceName, "UTF-8");
                HttpGet get = new HttpGet(getURL);
                try (CloseableHttpResponse response = httpclient.execute(get)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        HttpEntity entity = response.getEntity();
                        if (entity != null) {
                            String ava = EntityUtils.toString(entity);
                            if (!Utils.isBlank(ava)) {
                                JSONArray arr = JSONArray.parseArray(ava);
                                int len = arr.size();
                                List<HostEntity> hosts = new ArrayList<>();
                                for (int i = 0; i < len; i++) {
                                    hosts.add(JSON.parseObject(arr.getString(i), HostEntity.class));
                                }
                                lastAvailableService.put(serviceName, lb.get(hosts));
                                enableServiceDNode(serviceD);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                disableServiceDNode(serviceD);
            }
        }
        return lastAvailableService.get(serviceName);
    }

    private HostEntity pickOneServiceD() {
        if (this.serviceDNodes.isEmpty()) {
            logger.warn("No available serviceD nodes");
            return null;
        }
        Collections.shuffle(this.serviceDNodes);
        return this.serviceDNodes.get(0);
    }

    private void enableServiceDNode(HostEntity serviceDNode) {
        try {
            RWLOCK.writeLock().tryLock(500, TimeUnit.MILLISECONDS);
            if (serviceDNode != null) {
                this.notAvailableServiceDNodes.remove(serviceDNode);
                this.serviceDNodes.add(serviceDNode);
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        } finally {
            RWLOCK.writeLock().unlock();
        }
    }

    private void disableServiceDNode(HostEntity serviceDNode) {
        try {
            RWLOCK.writeLock().tryLock(500, TimeUnit.MILLISECONDS);
            if (serviceDNode != null) {
                this.serviceDNodes.remove(serviceDNode);
                this.notAvailableServiceDNodes.add(serviceDNode);
            }
            logger.info("notAvailableServiceDNodes => " + notAvailableServiceDNodes);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        } finally {
            RWLOCK.writeLock().unlock();
        }
    }

    private void verifyLeader() {
        try {
            RWLOCK.writeLock().tryLock(500, TimeUnit.MILLISECONDS);
            String param = "key=verifyLeader" + "&value=1";
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                for (HostEntity he : this.serviceDNodes) {
                    if (he.equals(this.leaderServiceD)) {
                        continue;
                    }
                    String verifyURL = "http://" + he + "/set?" + param;
                    HttpGet set = new HttpGet(verifyURL);
                    try (CloseableHttpResponse response = httpclient.execute(set)) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            HttpEntity entity = response.getEntity();
                            if (entity != null) {
                                String r = EntityUtils.toString(entity);
                                if (r != null && r.equalsIgnoreCase("ok")) {
                                    System.out.println("old leader is :" + this.leaderServiceD + " , current leader is :  " + he);
                                    this.leaderServiceD = he;
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        } finally {
            RWLOCK.writeLock().unlock();
        }
    }


}
