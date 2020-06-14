package org.sjtu.kvserver.rpc;

import org.sjtu.kvserver.service.KVService;
import org.sjtu.kvserver.service.ZkService;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClusterClient {

    public static void main(String[] args) {
        try{
            // connect to master
            // todo: multiple master
            Registry registry = LocateRegistry.getRegistry("47.101.211.167", 1099);
            ZkService zk = (ZkService) registry.lookup("ZkService");

            Map<String, String> kvs = new HashMap<>();
            Map<String, ReentrantReadWriteLock> rwlMap = new HashMap<>();
            ReentrantReadWriteLock kvsRwl = new ReentrantReadWriteLock();

            // create by PUT
            Thread th0 = new Thread() {
                @Override
                public void run() {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    while (true) {
                        try {
                            // generate unique key-value pair
                            String key = UUID.randomUUID().toString();
                            String value = Long.toString(System.currentTimeMillis());

                            // log in of the kv cluster
                            String clientId = UUID.randomUUID().toString();
                            zk.login(clientId);

                            // get node ID
                            String nodeIp = zk.getNode(key);
                            Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);

                            // PUT
                            KVService kv = (KVService) nodeRegistry.lookup("KVService");
                            ReentrantReadWriteLock rwl = rwlMap.get(key);
                            if (rwl == null) {
                                rwl = new ReentrantReadWriteLock();
                                rwlMap.put(key, rwl);
                            }
                            kvsRwl.writeLock().lock();
                            rwl.writeLock().lock();
                            kv.put(key, value);
                            kvs.put(key, value);
                            rwl.writeLock().unlock();
                            kvsRwl.writeLock().unlock();
                            System.out.println(String.format("%s PUT %s=%s on %s", df.format(new Date()), key, value, nodeIp));

                            // log out of the kv cluster
                            zk.logout(clientId);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            // update by PUT
            Thread th1 = new Thread() {
                @Override
                public void run() {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    while (true) {
                        try {
                            // choose a key-value pair
                            if (kvs.isEmpty()) {
                                sleep(1000);
                                continue;
                            }
                            kvsRwl.readLock().lock();
                            String[] keys = kvs.keySet().toArray(new String[0]);
                            kvsRwl.readLock().unlock();
                            Random random = new Random();
                            String key = keys[random.nextInt(keys.length)];
                            String value = Long.toString(System.currentTimeMillis());

                            // log in of the kv cluster
                            String clientId = UUID.randomUUID().toString();
                            zk.login(clientId);

                            // get node ID
                            String nodeIp = zk.getNode(key);
                            Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);

                            // PUT
                            KVService kv = (KVService) nodeRegistry.lookup("KVService");
                            ReentrantReadWriteLock rwl = rwlMap.get(key);
                            kvsRwl.writeLock().lock();
                            rwl.writeLock().lock();
                            kv.put(key, value);
                            kvs.put(key, value);
                            rwl.writeLock().unlock();
                            kvsRwl.writeLock().unlock();
                            System.out.println(String.format("%s PUT %s=%s on %s", df.format(new Date()), key, value, nodeIp));

                            // log out of the kv cluster
                            zk.logout(clientId);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            // delete by DELETE
            Thread th2 = new Thread() {
                @Override
                public void run() {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    while (true) {
                        try {
                            // choose a key-value pair
                            if (kvs.isEmpty()) {
                                sleep(1000);
                                continue;
                            }
                            kvsRwl.readLock().lock();
                            String[] keys = kvs.keySet().toArray(new String[0]);
                            kvsRwl.readLock().unlock();
                            Random random = new Random();
                            String key = keys[random.nextInt(keys.length)];

                            // log in of the kv cluster
                            String clientId = UUID.randomUUID().toString();
                            zk.login(clientId);

                            // get node ID
                            String nodeIp = zk.getNode(key);
                            Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);
                            KVService kv = (KVService) nodeRegistry.lookup("KVService");

                            // DELETE
                            ReentrantReadWriteLock rwl = rwlMap.get(key);
                            kvsRwl.writeLock().lock();
                            rwl.writeLock().lock();
                            kv.delete(key);
                            kvs.remove(key);
                            rwl.writeLock().unlock();
                            kvsRwl.writeLock().unlock();
                            System.out.println(String.format("%s DELETE %s on %s", df.format(new Date()), key, nodeIp));

                            // log out of the kv cluster
                            zk.logout(clientId);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            // read and assert by READ
            Thread th3 = new Thread() {
                @Override
                public void run() {
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    while (true) {
                        try {
                            // choose a key-value pair
                            if (kvs.isEmpty()) {
                                sleep(1000);
                                continue;
                            }
                            kvsRwl.readLock().lock();
                            String[] keys = kvs.keySet().toArray(new String[0]);
                            kvsRwl.readLock().unlock();
                            Random random = new Random();
                            String key = keys[random.nextInt(keys.length)];

                            // log in of the kv cluster
                            String clientId = UUID.randomUUID().toString();
                            zk.login(clientId);

                            // get node ID
                            String nodeIp = zk.getNode(key);
                            Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);

                            // READ and assert
                            KVService kv = (KVService) nodeRegistry.lookup("KVService");
                            ReentrantReadWriteLock rwl = rwlMap.get(key);
                            rwl.readLock().lock();
                            String localValue = kvs.get(key);
                            String remoteValue = kv.read(key);
                            if (remoteValue != null && localValue == null) {
                                throw(new Exception(String.format("expected <%s, NULL>, get <%s, %s>",
                                        key, key, remoteValue)));
                            } else if (remoteValue == null && localValue != null) {
                                throw(new Exception(String.format("expected <%s, %s>, get <%s, NULL>",
                                        key, localValue, key)));
                            } else if (remoteValue != null && localValue != null && !remoteValue.equals(localValue)) {
                                throw(new Exception(String.format("expected <%s, %s>, get <%s, %s>",
                                        key, localValue, key, remoteValue)));
                            }
                            rwl.readLock().unlock();
                            System.out.println(String.format("%s READ %s=%s on %s", df.format(new Date()), key, remoteValue, nodeIp));

                            // log out of the kv cluster
                            zk.logout(clientId);
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
                Thread.sleep(1000);
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
