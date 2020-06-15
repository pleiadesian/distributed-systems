package org.sjtu.kvserver.zkp;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.sjtu.kvserver.dht.ConsistentHashing;
import org.sjtu.kvserver.entity.NodeInfo;
import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.lock.ZkpDistributedReadWriteLock;
import org.sjtu.kvserver.service.KVService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

import static java.lang.Thread.sleep;
import static org.sjtu.kvserver.config.Config.*;

/**
 * Thread on the master node to monitor Zookeeper events
 */
public class ZkWatcher implements Runnable {

    // consistent hashing for data partition
    public static ConsistentHashing ch = new ConsistentHashing();

    // local copy of all register node on the Zookeeper
    private static List<String> childs;

    // read-write lock on the whole kv cluster
    private static ZkpDistributedReadWriteLock zkrwl = new ZkpDistributedReadWriteLock();

    // TODO: lock and test this part
    /**
     * Monitor off-line events of data nodes
     * @param child znode of child to be subscribe
     */
    private static void subscribeOffline(String child) {
        zkClient.subscribeDataChanges(String.format("%s/%s", registryPath, child), new IZkDataListener() {
            @Override
            public void handleDataChange(String s, Object o) throws Exception {
                if (((NodeInfo) o).isOffline()) {
                    // lock the kv cluster
                    zkrwl.lockWrite();

                    childs.remove(child);
                    ch.removePhysicalNode(child);
                    logger.warning("Delete " + child);

                    // distribute all key-value pairs to the other data nodes
                    String childIP = ((ServerInfo) zkClient.readData(String.format("%s/%s", clusterPath, child))).getIp();
                    Registry fromRegistry = LocateRegistry.getRegistry(childIP, 1099);
                    KVService fromKv = (KVService) fromRegistry.lookup("KVService");
                    logger.warning("migrating key-value to other nodes");
                    for (String key : fromKv.getKeys()) {
                        String targetIP = ((ServerInfo) zkClient.readData(String.format("%s/%s", clusterPath, ch.getObjectNode(key)))).getIp();
                        Registry toRegistry = LocateRegistry.getRegistry(targetIP, 1099);
                        KVService toKv = (KVService) toRegistry.lookup("KVService");
                        toKv.put(key, fromKv.read(key));
                    }

                    zkClient.delete(String.format("%s/%s", registryPath, child));
                    logger.warning("migration finished");

                    // unlock the kv cluster
                    zkrwl.unlockWrite();
                }
            }

            @Override
            public void handleDataDeleted(String s) throws Exception {

            }
        });

        // lock the cluster when a node is doing election or totally crashed
        zkClient.subscribeDataChanges(String.format("%s/%s", clusterPath, child), new IZkDataListener() {
            @Override
            public void handleDataChange(String s, Object o) throws Exception {

            }

            @Override
            public void handleDataDeleted(String s) throws Exception {
                logger.warning(String.format("%s crashes, lock the cluster", s));
                zkrwl.lockWrite();
                while (!zkClient.exists(String.format("%s/%s", clusterPath, child))) {
                    sleep(100);
                }
                zkrwl.unlockWrite();
                logger.warning(String.format("%s recovered, unlock the cluster", s));
            }
        });
    }

    @Override
    public void run() {
        // lock the kv cluster
        zkrwl.lockWrite();

        childs = zkClient.getChildren(registryPath);

        // monitor if a new child is added
        zkClient.subscribeChildChanges(registryPath, new IZkChildListener() {
            @Override
            public void handleChildChange(String path, List<String> childList) throws Exception {
                // register a new kv node
                for (String child : childList) {
                    if (!childs.contains(child)) {
                        // lock the kv cluster
                        zkrwl.lockWrite();

                        ch.addPhysicalNode(child);
                        logger.warning("Add " + child);

                        // migrating key-value from old nodes to new node
                        for (String migChild : childList) {
                            if (migChild.equals(child)) {
                                continue;
                            }
                            logger.warning(String.format("migrating from %s to %s", migChild, child));
                            String migChildIP = ((ServerInfo)zkClient.readData(String.format("%s/%s", clusterPath, migChild))).getIp();
                            String childIP = ((ServerInfo)zkClient.readData(String.format("%s/%s", clusterPath, child))).getIp();
                            Registry fromRegistry = LocateRegistry.getRegistry(migChildIP, 1099);
                            KVService fromKv = (KVService) fromRegistry.lookup("KVService");
                            Registry toRegistry = LocateRegistry.getRegistry(childIP, 1099);
                            KVService toKv = (KVService) toRegistry.lookup("KVService");
                            List<String> keys = fromKv.getKeys();
                            // the key-value pair should be stolen by new server from the target server
                            for (String key : keys) {
                                if (child.equals(ch.getObjectNode(key))) {
                                    toKv.put(key, fromKv.read(key));
                                }
                            }
                        }
                        logger.warning("migration finished");

                        childs.add(child);
                        subscribeOffline(child);

                        // unlock the kv cluster
                        zkrwl.unlockWrite();
                    }
                }
            }
        });

        // monitor existing childs
        for (String child : childs) {
            ch.addPhysicalNode(child);
            subscribeOffline(child);
        }

        // unlock the kv cluster
        zkrwl.unlockWrite();

        while (true) {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.err.println("master running");
        }
    }
}
