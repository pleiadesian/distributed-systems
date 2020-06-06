package org.sjtu.kvserver.rpc;

import org.sjtu.kvserver.service.KVService;
import org.sjtu.kvserver.service.ZkService;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

public class ClusterClient {

    public static void main(String[] args) {
        try{
            Registry registry = LocateRegistry.getRegistry("139.196.33.196", 1099);
            ZkService zk = (ZkService) registry.lookup("ZkService");

            Map<String, String> kvs = new HashMap<>();

            // create
            Thread th0 = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            String key = UUID.randomUUID().toString();
                            String value = Long.toString(System.currentTimeMillis());
                            String nodeIp = zk.getNode(key);
                            Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);
                            KVService kv = (KVService) nodeRegistry.lookup("KVService");
                            kv.put(key, value);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            // update
            Thread th1 = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            String[] keys = kvs.keySet().toArray(new String[0]);
                            Random random = new Random();
                            String key = keys[random.nextInt(keys.length)];
                            String value = Long.toString(System.currentTimeMillis());
                            String nodeIp = zk.getNode(key);
                            Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);
                            KVService kv = (KVService) nodeRegistry.lookup("KVService");
                            kv.put(key, value);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            // delete
            Thread th2 = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            String[] keys = kvs.keySet().toArray(new String[0]);
                            Random random = new Random();
                            String key = keys[random.nextInt(keys.length)];
                            String nodeIp = zk.getNode(key);
                            Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);
                            KVService kv = (KVService) nodeRegistry.lookup("KVService");
                            kv.delete(key);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            // assert
            Thread th3 = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            String[] keys = kvs.keySet().toArray(new String[0]);
                            Random random = new Random();
                            String key = keys[random.nextInt(keys.length)];
                            String nodeIp = zk.getNode(key);
                            Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);
                            KVService kv = (KVService) nodeRegistry.lookup("KVService");
                            String localValue = kvs.get(key);
                            String remoteValue = kv.read(key);
                            if (!remoteValue.equals(localValue)) {
                                throw(new Exception(String.format("expected <%s, %s>, get <%s, %s>",
                                        key, localValue, key, remoteValue)));
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            th0.start();
            th1.start();
            th2.start();
            th3.start();

            while (true) {
                Thread.sleep(1);
            }

        } catch (RemoteException e) {
            System.out.println("Remote: " + e);
        } catch (NotBoundException e) {
            System.out.println("Not Bound: " + e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
