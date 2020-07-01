package org.sjtu.kvserver.config;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;

import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Config {

    // Zookeeper paths
    public final static String clusterPath = "/clusterServer";
    public final static String registryPath = "/serverRegistry";
    public final static String lockPath = "/lock";
    public final static String masterPath = "/master";
    private final static String logConfigPath = "/logConfig";

    // Zookeeper cluster IP
    public final static String publicConnectString =
            "101.132.122.146:2181,139.224.114.108:2181,139.196.111.201:2181,106.14.212.114:2181,106.14.215.121:2181";
    private final static String connectString =
            "172.19.44.153:2181,172.19.44.155:2181,172.19.44.158:2181,172.19.44.154:2181,172.19.44.156:2181";

    // write ahead log
    public static boolean wal = true;
    public final static String logFilename = "kv.log";

    // console log
    public final static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    public static Logger logger = Logger.getLogger("global");

    public static ZkClient zkClient;

    public enum OpType
    {
        READ,
        PUT,
        DELETE
    }

    public static void loggerLevelConfig(String level) {
        Level logLevel;
        if ("SEVERE".equals(level)) {
            logLevel = Level.SEVERE;
        } else if ("WARNING".equals(level)) {
            logLevel = Level.WARNING;
        } else if ("INFO".equals(level)) {
            logLevel = Level.INFO;
        } else if ("CONFIG".equals(level)) {
            logLevel = Level.CONFIG;
        } else if ("FINE".equals(level)) {
            logLevel =  Level.FINE;
        } else if ("FINER".equals(level)) {
            logLevel =  Level.FINER;
        } else if ("FINEST".equals(level)) {
            logLevel =  Level.FINEST;
        } else if ("ALL".equals(level)) {
            logLevel =  Level.ALL;
        } else if ("OFF".equals(level)) {
            logLevel =  Level.OFF;
        } else {
            return;
        }
        zkClient.writeData(logConfigPath, logLevel);
        logger.setLevel(logLevel);
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

        // get logger level configuration
        if (!zkClient.exists(logConfigPath)) {
            zkClient.createPersistent(logConfigPath);
            zkClient.writeData(logConfigPath, Level.INFO);
        }
        logger.setLevel(zkClient.readData(logConfigPath));
        zkClient.subscribeDataChanges(logConfigPath, new IZkDataListener() {
            @Override
            public void handleDataChange(String s, Object o) throws Exception {
                logger.setLevel((Level) o);
            }

            @Override
            public void handleDataDeleted(String s) throws Exception {

            }
        });
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
        zkClient.unsubscribeAll();
        zkClient.close();
    }
}
