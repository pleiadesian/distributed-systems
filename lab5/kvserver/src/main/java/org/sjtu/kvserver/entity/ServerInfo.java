package org.sjtu.kvserver.entity;

import java.io.Serializable;

public class ServerInfo implements Serializable {
    private String ip;
    private String domain;
    private String nodeId;
    private int port;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public ServerInfo(String ip, String domain, String nodeId, int port) {
        this.ip = ip;
        this.domain = domain;
        this.nodeId = nodeId;
        this.port = port;
    }

    @Override
    public String toString() {
        return "ServerInfo{" +
                "ip='" + ip + '\'' +
                ", domain='" + domain + '\'' +
                ", nodeId='" + nodeId + '\'' +
                ", port=" + port +
                '}';
    }
}
