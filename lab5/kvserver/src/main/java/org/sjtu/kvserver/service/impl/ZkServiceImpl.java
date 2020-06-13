package org.sjtu.kvserver.service.impl;

import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.service.ZkService;
import org.sjtu.kvserver.zkp.ZkWatcher;

import java.rmi.RemoteException;

public class ZkServiceImpl implements ZkService {
    public String getNode(String key) throws RemoteException {
        String nodeName = ZkWatcher.ch.getObjectNode(key);
        return ((ServerInfo)ZkWatcher.zkClient.readData(String.format("%s/%s", "/clusterServer", nodeName))).getIp();
    }
}
