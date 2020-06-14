package org.sjtu.kvserver.config;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;

import java.text.SimpleDateFormat;

public class Config {

    public final static String clusterPath = "/clusterServer";
    public final static String registryPath = "/serverRegistry";
    public final static String lockPath = "/lock";
    public final static String logFilename = "kv.log";
    private final static String connectString = "172.19.44.153:2181,172.19.44.155:2181,172.19.44.158:2181";
    public final static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    public static ZkClient zkClient;
    public static boolean wal = true;

    public enum OpType
    {
        READ,
        PUT,
        DELETE
    }

    public static void connect() {
        zkClient = new ZkClient(connectString, 5000, 5000, new SerializableSerializer());
        // init root node in zookeeper
        if (!zkClient.exists(clusterPath)) {
            zkClient.createPersistent(clusterPath);
        }
        if (!zkClient.exists(registryPath)) {
            zkClient.createPersistent(registryPath);
        }
        if (!zkClient.exists(lockPath)) {
            zkClient.createPersistent(lockPath);
        }
    }

    // todo : auto-cleaning when master quits
    public static void refresh() {
        if (zkClient.exists(clusterPath)) {
            zkClient.deleteRecursive(clusterPath);
        }
        if (zkClient.exists(registryPath)) {
            zkClient.deleteRecursive(registryPath);
        }
        if (zkClient.exists(lockPath)) {
            zkClient.deleteRecursive(registryPath);
        }
        if (!zkClient.exists(clusterPath)) {
            zkClient.createPersistent(clusterPath);
        }
        if (!zkClient.exists(registryPath)) {
            zkClient.createPersistent(registryPath);
        }
        if (!zkClient.exists(lockPath)) {
            zkClient.createPersistent(lockPath);
        }
    }

    public static void disconnect() {
        if (zkClient.exists(clusterPath)) {
            zkClient.deleteRecursive(clusterPath);
        }
        if (zkClient.exists(registryPath)) {
            zkClient.deleteRecursive(registryPath);
        }
        if (zkClient.exists(lockPath)) {
            zkClient.deleteRecursive(lockPath);
        }
        zkClient.close();
    }
}
