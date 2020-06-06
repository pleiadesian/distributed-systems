package org.sjtu.kvserver.zkp;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.sjtu.kvserver.dht.ConsistentHashing;
import org.sjtu.kvserver.entity.ServerInfo;

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
                        childs.add(child);
                        ch.addPhysicalNode(child);
                        System.out.println("Add " + child);
                    }
                }
                // delete a child
                for (String child : childs) {
                    if (!childList.contains(child)) {
                        childs.remove(child);
                        ch.removePhysicalNode(child);
                        System.out.println("Delete " + child);
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
