package org.sjtu.kvserver.service.impl;

import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.lock.ZkpDistributedReadWriteLock;
import org.sjtu.kvserver.service.ZkService;
import org.sjtu.kvserver.zkp.ZkWatcher;

import java.rmi.RemoteException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.sjtu.kvserver.config.Config.df;
import static org.sjtu.kvserver.config.Config.zkClient;

public class ZkServiceImpl implements ZkService {
    // local kv storage
    private static Map<String, ZkpDistributedReadWriteLock> zkrwls = new ConcurrentHashMap<>();

    /**
     * Get the node ID which the key belongs to
     * @param key key to find
     * @param clientId client ID
     * @return node ID
     * @throws RemoteException client not logged in will raise remote exception
     */
    public String getNode(String key, String clientId) throws RemoteException {
        if (zkrwls.get(clientId) == null) {
            System.out.println(String.format("%s %s has not logged in", df.format(new Date()), clientId));
            throw(new RemoteException());
        }
        System.out.println(String.format("%s GET NODE %s", df.format(new Date()), key));
        String nodeName = ZkWatcher.ch.getObjectNode(key);
        return ((ServerInfo)zkClient.readData(String.format("%s/%s", "/clusterServer", nodeName))).getIp();
    }

    /**
     * A new client log in, acquire a new read lock
     * @param clientId client ID
     * @throws RemoteException repeat logins will raise remote exception
     */
    public void login(String clientId) throws RemoteException {
        if (zkrwls.get(clientId) != null) {
            System.out.println(String.format("%s %s has logged in", df.format(new Date()), clientId));
            throw(new RemoteException());
        }
        ZkpDistributedReadWriteLock zkrwl = new ZkpDistributedReadWriteLock();
        zkrwl.lockRead();
        zkrwls.put(clientId, zkrwl);
        System.out.println(String.format("%s %s logs in", df.format(new Date()), clientId));
    }

    /**
     * A client goes offline
     * @param clientId client ID
     * @throws RemoteException repeat logouts will raise remote exception
     */
    public void logout(String clientId) throws RemoteException {
        ZkpDistributedReadWriteLock zkrwl = zkrwls.get(clientId);
        if (zkrwl == null) {
            System.out.println(String.format("%s %s has not logged in", df.format(new Date()), clientId));
            throw(new RemoteException());
        }
        zkrwl.unlockRead();
        zkrwls.remove(clientId);
        System.out.println(String.format("%s %s logs out", df.format(new Date()), clientId));
    }
}
