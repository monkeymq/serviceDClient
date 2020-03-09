package com.webex;

import com.webex.serviceDClient.ServiceDClient;
import com.webex.serviceDClient.entity.HostEntity;
import com.webex.serviceDClient.lb.RandomLB;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Unit test for simple App.
 */
public class AppTest {
    private ServiceDClient client;
    private String serviceName = "test-service";

    @Before
    public void before() {
        String local = "10.140.203.112";
        Set<HostEntity> serviceDNodes = new HashSet<>();
        HostEntity h1 = new HostEntity();
        HostEntity h2 = new HostEntity();
        HostEntity h3 = new HostEntity();
        h1.setIp(local);
        h2.setIp(local);
        h3.setIp(local);
        h1.setPort(16000);
        h2.setPort(16001);
        h3.setPort(16002);
        serviceDNodes.add(h1);
        serviceDNodes.add(h2);
        serviceDNodes.add(h3);
        client = new ServiceDClient(serviceDNodes);
    }

    @Test
    public void clt() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            HostEntity service = new HostEntity("localhost", 200+i);
            client.register(serviceName, service);
        }

        Thread.sleep(3000);

        for(int i=0; i<30; i++) {
            int finalI = i;
//            new Thread(() -> {
                long t = System.currentTimeMillis();
                HostEntity he = client.getAvailableService(serviceName);
                t = System.currentTimeMillis() - t;
                System.out.println("Thread 1=====  Time: " + finalI + "   service: " + he + "   cost time: " + t);
//            }).start();

//            new Thread(() -> {
//                long t = System.currentTimeMillis();
//                HostEntity he = client.getAvailableService(serviceName);
//                t = System.currentTimeMillis() - t;
//                System.out.println("Thread 2 -----  Time: " + finalI + "   service: " + he + " cost time: " + t);
//            }).start();
        }

        Thread.sleep(10000);
    }
}
