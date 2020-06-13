package org.sjtu.kvserver.zkp;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.sjtu.kvserver.dht.ConsistentHashing;
import org.sjtu.kvserver.entity.NodeInfo;
import org.sjtu.kvserver.entity.ServerInfo;
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
    public static ZkClient zkClient;

    @Override
    public void run() {
        zkClient = new ZkClient(connectString, 5000, 5000, new SerializableSerializer());

        // todo: auto-cleaning when system quits
        if (zkClient.exists(clusterPath)) {
            zkClient.deleteRecursive(clusterPath);
        }
        if (zkClient.exists(registryPath)) {
            zkClient.deleteRecursive(registryPath);
        }

        // init root node in zookeeper
        if (!zkClient.exists(clusterPath)) {
            zkClient.createPersistent(clusterPath);
        }
        if (!zkClient.exists(registryPath)) {
            zkClient.createPersistent(registryPath);
        }

        childs = zkClient.getChildren(registryPath);
        zkClient.subscribeChildChanges(registryPath, new IZkChildListener() {
            @Override
            public void handleChildChange(String path, List<String> childList) throws Exception {
                // register a new kv node
                for (String child : childList) {
                    if (!childs.contains(child)) {
                        ch.addPhysicalNode(child);
                        System.out.println("Add " + child);
                        for (String migChild : childList) {
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
                        childs.add(child);
                        zkClient.subscribeDataChanges(String.format("%s/%s", registryPath, child), new IZkDataListener() {
                            @Override
                            public void handleDataChange(String s, Object o) throws Exception {
                                // todo: lock
                                if (((NodeInfo) o).isOffline()) {
                                    childs.remove(child);
                                    ch.removePhysicalNode(child);
                                    System.out.println("Delete " + child);
                                    String childIP = ((ServerInfo) zkClient.readData(String.format("%s/%s", clusterPath, child))).getIp();
                                    Registry fromRegistry = LocateRegistry.getRegistry(childIP, 1099);
                                    KVService fromKv = (KVService) fromRegistry.lookup("KVService");
                                    // distribute all key-value pairs to the other data nodes
                                    for (String key : fromKv.getKeys()) {
                                        String targetIP = ((ServerInfo) zkClient.readData(String.format("%s/%s", clusterPath, ch.getObjectNode(key)))).getIp();
                                        Registry toRegistry = LocateRegistry.getRegistry(targetIP, 1099);
                                        KVService toKv = (KVService) toRegistry.lookup("KVService");
                                        toKv.put(key, fromKv.read(key));
                                    }
                                    zkClient.delete(String.format("%s/%s", registryPath, child));
                                }
                            }

                            @Override
                            public void handleDataDeleted(String s) throws Exception {

                            }
                        });
                    }
                }
            }
        });

        for (String child : childs) {
            ch.addPhysicalNode(child);
        }

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
