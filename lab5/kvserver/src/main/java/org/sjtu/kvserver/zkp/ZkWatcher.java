package org.sjtu.kvserver.zkp;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.sjtu.kvserver.dht.ConsistentHashing;
import org.sjtu.kvserver.entity.NodeInfo;
import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.lock.ZkpDistributedReadWriteLock;
import org.sjtu.kvserver.service.KVService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.sjtu.kvserver.config.Config.*;

public class ZkWatcher implements Runnable {

    private static List<String> childs;
    public static ConsistentHashing ch = new ConsistentHashing();
    private static ZkpDistributedReadWriteLock zkrwl = new ZkpDistributedReadWriteLock();

    private static void subscribeOffline(String child) {
        zkClient.subscribeDataChanges(String.format("%s/%s", registryPath, child), new IZkDataListener() {
            @Override
            public void handleDataChange(String s, Object o) throws Exception {
                // todo: lock and test this part
                if (((NodeInfo) o).isOffline()) {
                    zkrwl.lockWrite();
                    childs.remove(child);
                    ch.removePhysicalNode(child);
                    System.out.println("Delete " + child);

                    // distribute all key-value pairs to the other data nodes
                    String childIP = ((ServerInfo) zkClient.readData(String.format("%s/%s", clusterPath, child))).getIp();
                    Registry fromRegistry = LocateRegistry.getRegistry(childIP, 1099);
                    KVService fromKv = (KVService) fromRegistry.lookup("KVService");
                    System.out.println("migrating key-value to other nodes");
                    for (String key : fromKv.getKeys()) {
                        String targetIP = ((ServerInfo) zkClient.readData(String.format("%s/%s", clusterPath, ch.getObjectNode(key)))).getIp();
                        Registry toRegistry = LocateRegistry.getRegistry(targetIP, 1099);
                        KVService toKv = (KVService) toRegistry.lookup("KVService");
                        toKv.put(key, fromKv.read(key));
                    }

                    zkClient.delete(String.format("%s/%s", registryPath, child));
                    System.out.println("migration finished");
                    zkrwl.unlockWrite();
                }
            }

            @Override
            public void handleDataDeleted(String s) throws Exception {

            }
        });
    }

    @Override
    public void run() {
        zkrwl.lockWrite();
        childs = zkClient.getChildren(registryPath);

        // monitor if a new child is added
        zkClient.subscribeChildChanges(registryPath, new IZkChildListener() {
            @Override
            public void handleChildChange(String path, List<String> childList) throws Exception {
                // register a new kv node
                for (String child : childList) {
                    if (!childs.contains(child)) {
                        zkrwl.lockWrite();
                        ch.addPhysicalNode(child);
                        System.out.println("Add " + child);

                        // migrating key-value from old nodes to new node
                        for (String migChild : childList) {
                            System.out.println(String.format("migrating from %s to %s", migChild, child));
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
                        System.out.println("migration finished");

                        childs.add(child);
                        subscribeOffline(child);
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

        zkrwl.unlockWrite();

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.err.println("Zookeeper watcher running");
        }
    }
}
