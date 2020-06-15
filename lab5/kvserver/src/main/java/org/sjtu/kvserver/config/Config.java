package org.sjtu.kvserver.config;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;

import java.text.SimpleDateFormat;

public class Config {

    // Zookeeper paths
    public final static String clusterPath = "/clusterServer";
    public final static String registryPath = "/serverRegistry";
    public final static String lockPath = "/lock";
    public final static String masterPath = "/master";

    // Zookeeper cluster IP
    public final static String publicConnectString =
            "47.101.211.167:2181,139.196.42.83:2181,106.14.175.160:2181,139.196.109.125:2181,139.196.188.241:2181";
    private final static String connectString =
            "172.19.44.153:2181,172.19.44.155:2181,172.19.44.158:2181,172.19.44.154:2181,172.19.44.156:2181";

    // write-after-log
    public static boolean wal = true;
    public final static String logFilename = "kv.log";

    public final static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    public static ZkClient zkClient;

    public enum OpType
    {
        READ,
        PUT,
        DELETE
    }

    /**
     * Connect to Zookeeper cluster and do some initialization
     */
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

    /**
     * Refresh all states in Zookeeper cluster when restarting the kv cluster
     */
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

    /**
     * Disconnect to Zookeeper when going off-line
     */
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
