package org.sjtu.kvserver.rpc;

import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.service.KVService;
import org.sjtu.kvserver.service.ZkService;
import org.I0Itec.zkclient.ZkClient;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.sjtu.kvserver.config.Config.masterPath;
import static org.sjtu.kvserver.config.Config.publicConnectString;

/**
 * Run concurrent clients to test the kv cluster
 */
public class ClusterClient {

    private static ZkClient zkClient = new ZkClient(publicConnectString, 5000, 5000, new SerializableSerializer());

    /**
     * Find the master node
     * @return service on the master node
     */
    private static ZkService connectMaster() {
        try {
            String masterIp = ((ServerInfo) zkClient.readData(masterPath)).getIp();
            Registry registry = LocateRegistry.getRegistry(masterIp, 1099);
            return (ZkService) registry.lookup("ZkService");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        // local copy of kv state for asserting
        Map<String, String> kvs = new HashMap<>();

        // synchronize local updates with updates on the kv cluster
        Map<String, ReentrantReadWriteLock> rwlMap = new HashMap<>();
        ReentrantReadWriteLock kvsRwl = new ReentrantReadWriteLock();

        // create by PUT
        Thread th0 = new Thread() {
            @Override
            public void run() {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                String clientId = UUID.randomUUID().toString();
                while (true) {
                    ZkService zk = connectMaster();
                    if (zk == null) {
                        continue;
                    }
                    try {
                        // generate unique key-value pair
                        String key = UUID.randomUUID().toString();
                        String value = Long.toString(System.currentTimeMillis());

                        // log in of the kv cluster
                        zk.login(clientId);

                        // get node ID
                        String nodeIp = zk.getNode(key, clientId);
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
                        try {
                            if (kv.put(key, value) < 0) {
                                throw(new Exception("PUT failed"));
                            }
                        } catch (Exception e) {
                            rwl.writeLock().unlock();
                            kvsRwl.writeLock().unlock();
                            throw e;
                        }
                        kvs.put(key, value);
                        rwl.writeLock().unlock();
                        kvsRwl.writeLock().unlock();
                        System.out.println(String.format("%s PUT %s=%s on %s", df.format(new Date()), key, value, nodeIp));

                        // log out of the kv cluster
                        zk.logout(clientId);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // log out of the kv cluster
                        try {
                            zk.logout(clientId);
                        } catch (RemoteException re) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        // update by PUT
        Thread th1 = new Thread() {
            @Override
            public void run() {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                String clientId = UUID.randomUUID().toString();
                while (true) {
                    ZkService zk = connectMaster();
                    if (zk == null) {
                        continue;
                    }
                    try {
                        // choose a key-value pair
                        if (kvs.isEmpty()) {
                            sleep(1000);
                            continue;
                        }
                        kvsRwl.readLock().lock();
                        String[] keys;
                        try {
                            keys = kvs.keySet().toArray(new String[0]);
                        } catch (Exception e) {
                            kvsRwl.readLock().unlock();
                            throw e;
                        }
                        kvsRwl.readLock().unlock();
                        Random random = new Random();
                        String key = keys[random.nextInt(keys.length)];
                        String value = Long.toString(System.currentTimeMillis());

                        // log in of the kv cluster
                        zk.login(clientId);

                        // get node ID
                        String nodeIp = zk.getNode(key, clientId);
                        Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);

                        // PUT
                        KVService kv = (KVService) nodeRegistry.lookup("KVService");
                        ReentrantReadWriteLock rwl = rwlMap.get(key);
                        kvsRwl.writeLock().lock();
                        rwl.writeLock().lock();
                        try {
                            if (kv.put(key, value) < 0) {
                                throw(new Exception("PUT failed"));
                            }
                        } catch (Exception e) {
                            rwl.writeLock().unlock();
                            kvsRwl.writeLock().unlock();
                            throw e;
                        }
                        kvs.put(key, value);
                        rwl.writeLock().unlock();
                        kvsRwl.writeLock().unlock();
                        System.out.println(String.format("%s PUT %s=%s on %s", df.format(new Date()), key, value, nodeIp));

                        // log out of the kv cluster
                        zk.logout(clientId);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // log out of the kv cluster
                        try {
                            zk.logout(clientId);
                        } catch (RemoteException re) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        // delete by DELETE
        Thread th2 = new Thread() {
            @Override
            public void run() {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                String clientId = UUID.randomUUID().toString();
                while (true) {
                    ZkService zk = connectMaster();
                    if (zk == null) {
                        continue;
                    }
                    try {
                        // choose a key-value pair
                        if (kvs.isEmpty()) {
                            sleep(1000);
                            continue;
                        }
                        kvsRwl.readLock().lock();
                        String[] keys;
                        try {
                            keys = kvs.keySet().toArray(new String[0]);
                        } catch (Exception e) {
                            kvsRwl.readLock().unlock();
                            throw e;
                        }
                        kvsRwl.readLock().unlock();
                        Random random = new Random();
                        String key = keys[random.nextInt(keys.length)];

                        // log in of the kv cluster
                        zk.login(clientId);

                        // get node ID
                        String nodeIp = zk.getNode(key, clientId);
                        Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);
                        KVService kv = (KVService) nodeRegistry.lookup("KVService");

                        // DELETE
                        ReentrantReadWriteLock rwl = rwlMap.get(key);
                        kvsRwl.writeLock().lock();
                        rwl.writeLock().lock();
                        try {
                            if (kv.delete(key) < 0) {
                                throw(new Exception("DELETE failed"));
                            }
                        } catch (Exception e) {
                            rwl.writeLock().unlock();
                            kvsRwl.writeLock().unlock();
                            throw e;
                        }
                        kvs.remove(key);
                        rwl.writeLock().unlock();
                        kvsRwl.writeLock().unlock();
                        System.out.println(String.format("%s DELETE %s on %s", df.format(new Date()), key, nodeIp));

                        // log out of the kv cluster
                        zk.logout(clientId);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // log out of the kv cluster
                        try {
                            zk.logout(clientId);
                        } catch (RemoteException re) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        // read and assert by READ
        Thread th3 = new Thread() {
            @Override
            public void run() {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                ReentrantReadWriteLock rwl;
                String clientId = UUID.randomUUID().toString();
                while (true) {
                    ZkService zk = connectMaster();
                    if (zk == null) {
                        continue;
                    }
                    try {
                        // choose a key-value pair
                        if (kvs.isEmpty()) {
                            sleep(1000);
                            continue;
                        }
                        kvsRwl.readLock().lock();
                        String[] keys;
                        try {
                            keys = kvs.keySet().toArray(new String[0]);
                        } catch (Exception e) {
                            kvsRwl.readLock().unlock();
                            throw e;
                        }
                        kvsRwl.readLock().unlock();
                        Random random = new Random();
                        String key = keys[random.nextInt(keys.length)];

                        // log in of the kv cluster
                        zk.login(clientId);

                        // get node ID
                        String nodeIp = zk.getNode(key, clientId);
                        Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);

                        // READ and assert
                        KVService kv = (KVService) nodeRegistry.lookup("KVService");
                        rwl = rwlMap.get(key);
                        rwl.readLock().lock();
                        String remoteValue;
                        try {
                            remoteValue = kv.read(key);
                        } catch (Exception e) {
                            rwl.readLock().unlock();
                            throw e;
                        }
                        String localValue = kvs.get(key);
                        if (remoteValue != null && localValue == null) {
                            rwl.readLock().unlock();
                            throw(new Exception(String.format("expected <%s, NULL>, get <%s, %s>",
                                    key, key, remoteValue)));
                        } else if (remoteValue == null && localValue != null) {
                            rwl.readLock().unlock();
                            throw(new Exception(String.format("expected <%s, %s>, get <%s, NULL>",
                                    key, localValue, key)));
                        } else if (remoteValue != null && localValue != null && !remoteValue.equals(localValue)) {
                            rwl.readLock().unlock();
                            throw(new Exception(String.format("expected <%s, %s>, get <%s, %s>",
                                    key, localValue, key, remoteValue)));
                        }
                        rwl.readLock().unlock();
                        System.out.println(String.format("%s READ %s=%s on %s", df.format(new Date()), key, remoteValue, nodeIp));

                        // log out of the kv cluster
                        zk.logout(clientId);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // log out of the kv cluster
                        try {
                            zk.logout(clientId);
                        } catch (RemoteException re) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        // READ all keys and assert
        Thread th4 = new Thread() {
            @Override
            public void run() {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                ReentrantReadWriteLock rwl;
                String clientId = UUID.randomUUID().toString();
                while (true) {
                    ZkService zk = connectMaster();
                    if (zk == null) {
                        continue;
                    }
                    try {
                        if (kvs.isEmpty()) {
                            sleep(1000);
                            continue;
                        }

                        // log in of the kv cluster
                        zk.login(clientId);

                        kvsRwl.readLock().lock();
                        String[] keys;
                        try {
                            keys = kvs.keySet().toArray(new String[0]);
                        } catch (Exception e) {
                            kvsRwl.readLock().unlock();
                            throw e;
                        }
                        int total = keys.length;
                        int inconsist = 0;
                        for (String key : keys) {
                            // connect to data node
                            String nodeIp = zk.getNode(key, clientId);
                            Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);
                            KVService kv = (KVService) nodeRegistry.lookup("KVService");

                            // READ and assert
                            String remoteValue = kv.read(key);
                            String localValue = kvs.get(key);

                            try {
                                if (remoteValue != null && localValue == null) {
                                    throw (new Exception(String.format("expected <%s, NULL>, get <%s, %s>",
                                            key, key, remoteValue)));
                                } else if (remoteValue == null && localValue != null) {
                                    throw (new Exception(String.format("expected <%s, %s>, get <%s, NULL>",
                                            key, localValue, key)));
                                } else if (remoteValue != null && localValue != null && !remoteValue.equals(localValue)) {
                                    throw (new Exception(String.format("expected <%s, %s>, get <%s, %s>",
                                            key, localValue, key, remoteValue)));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                inconsist++;
                            }
                        }
                        kvsRwl.readLock().unlock();
                        if (inconsist > 0) {
                            throw (new Exception(String.format("%d in %d (%.2f%%) key-value is inconsistent", inconsist,
                                    total, (float)inconsist / total * 100)));
                        }

                        // log out of the kv cluster
                        zk.logout(clientId);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // log out of the kv cluster
                        try {
                            zk.logout(clientId);
                        } catch (RemoteException re) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        th0.start();
        th1.start();
        th2.start();
        th3.start();
        th4.start();

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
