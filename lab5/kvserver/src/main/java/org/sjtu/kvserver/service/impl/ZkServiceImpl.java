package org.sjtu.kvserver.service.impl;

import org.sjtu.kvserver.service.ZkService;
import org.sjtu.kvserver.zkp.ZkWatcher;

import java.rmi.RemoteException;

public class ZkServiceImpl implements ZkService {
    public String getNode(String key) throws RemoteException {
        return ZkWatcher.ch.getObjectNode(key);
    }
}
