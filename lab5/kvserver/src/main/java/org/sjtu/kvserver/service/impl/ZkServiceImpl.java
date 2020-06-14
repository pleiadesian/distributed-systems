package org.sjtu.kvserver.service.impl;

import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.lock.ZkpDistributedReadWriteLock;
import org.sjtu.kvserver.service.ZkService;
import org.sjtu.kvserver.zkp.ZkWatcher;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.sjtu.kvserver.config.Config.zkClient;

public class ZkServiceImpl implements ZkService {
    private static Map<String, ZkpDistributedReadWriteLock> zkrwls = new ConcurrentHashMap<>();

    public String getNode(String key) throws RemoteException {
        String nodeName = ZkWatcher.ch.getObjectNode(key);
        return ((ServerInfo)zkClient.readData(String.format("%s/%s", "/clusterServer", nodeName))).getIp();
    }

    public void login(String clientId) throws RemoteException {
        if (zkrwls.get(clientId) != null) {
            System.out.println(String.format("%s has logged in", clientId));
            throw(new RemoteException());
        }
        ZkpDistributedReadWriteLock zkrwl = new ZkpDistributedReadWriteLock();
        zkrwl.lockRead();
        zkrwls.put(clientId, zkrwl);
    }

    public void logout(String clientId) throws RemoteException {
        ZkpDistributedReadWriteLock zkrwl = zkrwls.get(clientId);
        if (zkrwl == null) {
            System.out.println(String.format("%s has not logged in", clientId));
            throw(new RemoteException());
        }
        zkrwl.unlockRead();
        zkrwls.remove(clientId);
    }
}
