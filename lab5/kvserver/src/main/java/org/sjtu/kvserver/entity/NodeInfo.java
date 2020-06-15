package org.sjtu.kvserver.entity;

import java.io.Serializable;

public class NodeInfo implements Serializable {
    private boolean offline;

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
