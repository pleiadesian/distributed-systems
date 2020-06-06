package org.sjtu.kvserver.zkp;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.sjtu.kvserver.dht.ConsistentHashing;
import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.service.KVService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZkWatcher implements Runnable {

    private static List<String> childs;
    public static ConsistentHashing ch = new ConsistentHashing();

    @Override
    public void run() {
        String connectString = "172.19.44.153:2181,172.19.44.155:2181,172.19.44.158:2181";
        ZkClient zkClient = new ZkClient(connectString, 5000, 5000, new SerializableSerializer());

        String clusterPath = "/clusterServer";
        if (!zkClient.exists(clusterPath)) {
            zkClient.createPersistent(clusterPath);
        }

        childs = zkClient.getChildren(clusterPath);

        zkClient.subscribeChildChanges(clusterPath, new IZkChildListener() {
            @Override
            public void handleChildChange(String path, List<String> childList) throws Exception {
                // add a child
                for (String child : childList) {
                    if (!childs.contains(child)) {
                        ch.addPhysicalNode(child);
                        System.out.println("Add " + child);
                        for (String migChild : childList) {
                            Registry fromRegistry = LocateRegistry.getRegistry(migChild, 1099);
                            KVService fromKv = (KVService) fromRegistry.lookup("KVService");
                            Registry toRegistry = LocateRegistry.getRegistry(child, 1099);
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
                    }
                }
                // delete a child
                for (String child : childs) {
                    if (!childList.contains(child)) {
                        childs.remove(child);
                        ch.removePhysicalNode(child);
                        System.out.println("Delete " + child);
                        Registry fromRegistry = LocateRegistry.getRegistry(child, 1099);
                        KVService fromKv = (KVService) fromRegistry.lookup("KVService");
                        // distribute all key-value pairs to the other data nodes
                        for (String key : fromKv.getKeys()) {
                            Registry toRegistry = LocateRegistry.getRegistry(ch.getObjectNode(key), 1099);
                            KVService toKv = (KVService) toRegistry.lookup("KVService");
                            toKv.put(key, fromKv.read(key));
                        }
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
