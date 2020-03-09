package com.webex.serviceDClient.entity;

import java.util.Objects;

public class HostEntity implements Comparable<HostEntity> {
    private String ip;
    private int port;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public HostEntity() {
    }

    public HostEntity(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostEntity that = (HostEntity) o;
        return port == that.port &&
                ip.equals(that.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }

    @Override
    public String toString() {
        return ip.concat(":").concat(port + "");
    }

    @Override
    public int compareTo(HostEntity o) {
        if (this.getIp().equals(o.getIp())) {
            return this.getPort() - o.getPort();
        } else {
            return this.getIp().compareTo(o.getIp());
        }
    }
}
