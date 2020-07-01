package org.sjtu.kvserver.service.impl;

import org.sjtu.kvserver.lock.ZkpDistributedReadWriteLock;
import org.sjtu.kvserver.log.LogManager;
import org.sjtu.kvserver.rpc.KVServer;
import org.sjtu.kvserver.service.KVService;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Thread.sleep;
import static org.sjtu.kvserver.config.Config.*;
import static org.sjtu.kvserver.log.LogManager.*;

public class KVServiceImpl implements KVService {

    // local kv storage
    private static Map<String, String> kv = new ConcurrentHashMap<>();

    private static int syncToSlave(OpType op, int seqNum, String key, String value) {
        try {
            String nodeId = KVServer.getNodeId();
            String currentIP = KVServer.getIP();
            String registerPath = String.format("%s/%s", registryPath, nodeId);
            List<String> nodes = zkClient.getChildren(registerPath);
            for (String node : nodes) {
                // do not sync data with self
                if (node.equals(currentIP)) {
                    continue;
                }
                Registry toRegistry = LocateRegistry.getRegistry(node, 1099);
                KVService toKv;
                toKv = (KVService) toRegistry.lookup("KVService");
                if (toKv.syncFromMaster(op, seqNum, key, value) < 0) {
                    return -1;
                }
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * PUT
     * @param key key to PUT
     * @param value value of the key
     * @return 0 when succeeded, -1 when failed
     */
    public int put(String key, String value) {
        try {
            // write ahead log
            if (wal) {
                int seqNum = log(OpType.PUT, key, value, 0);
                // retry to sync until all slaves commit
                while (syncToSlave(OpType.PUT, seqNum, key, value) < 0);
                // write sequence should be consistency with log sequence
                while (getWriteSeqNum() < seqNum);
            }

            // if crashed here, inconsistency exists between master and slave
            kv.put(key, value);
            if (wal) {
                setWriteSeqNum(getWriteSeqNum() + 1);
            }
            logger.info(String.format("%s PUT %s=%s", df.format(new Date()), key, value));
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * READ
     * @param key key to READ
     * @return value when succeeded, null when failed
     */
    public String read(String key) {
        if (wal) {
            int seqNum = getLogSeqNum();
            while (getWriteSeqNum() < seqNum) {
                try {
                    sleep(100);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        String value = kv.get(key);
        logger.info(String.format("%s READ %s=%s", df.format(new Date()), key, value));
        return value;
    }

    /**
     * DELETE
     * @param key key to DELETE
     * @return 0 when succeeded, -1 when failed
     */
    public int delete(String key) {
        try {
            // write ahead log
            if (wal) {
                int seqNum = log(OpType.DELETE, key, null, 0);
                // retry to sync until all slaves commit
                while (syncToSlave(OpType.DELETE, seqNum, key, null) < 0);
                // write sequence should be consistency with log sequence
                while (getWriteSeqNum() < seqNum);
            }

            // if crashed here, inconsistency exists between master and slave
            kv.remove(key);
            if (wal) {
                setWriteSeqNum(getWriteSeqNum() + 1);
            }
            logger.info(String.format("%s DELETE %s", df.format(new Date()), key));
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Slave syncs key-value from master
     * @param op operation type, PUT or DELETE
     * @param seqNum log sequence number decided by primary node
     * @param key key
     * @param value value
     * @return 0 for succeed, -1 for failed
     */
    public int syncFromMaster(OpType op, int seqNum, String key, String value) {
        try {
            // may block here, waiting for previous operation to finish
            log(op, key, value, seqNum);
            if (op == OpType.PUT) {
                kv.put(key, value);
            } else if (op == OpType.DELETE) {
                kv.remove(key);
            }
            setLogSeqNum(getLogSeqNum() + 1);
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * get local log sequence number
     * @return local log sequence number
     */
    public int getLogSeqNum() {
        return LogManager.getLogSeqNum();
    }

    /**
     * get all keys when migrating data from nodes to nodes
     * @return list of keys on this node
     */
    public List<String> getKeys() {
        logger.info(String.format("%s READ ALL KEYS", df.format(new Date())));
        return new ArrayList<>(kv.keySet());
    }
}
