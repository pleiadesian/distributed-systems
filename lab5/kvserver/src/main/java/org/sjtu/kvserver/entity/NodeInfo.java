package org.sjtu.kvserver.entity;

public class NodeInfo {
    private boolean offline = false;

    public boolean isOffline() {
        return offline;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public NodeInfo(boolean offline) {
        this.offline = offline;
    }
}
