package org.sjtu.kvserver.rpc;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.sjtu.kvserver.config.Config;
import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.service.KVService;
import org.sjtu.kvserver.service.impl.KVServiceImpl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Enumeration;

import static org.sjtu.kvserver.config.Config.*;

public class KVServer {

    public static ZkClient zkClient;
    public static ServerInfo serverInfo;

    private static Thread followTh;
    private static ServerInfo masterInfo;

    public static void takeMaster(String path) {
        try {
            zkClient.createEphemeral(path);
            zkClient.writeData(path, serverInfo);
            System.out.println(String.format("%s on %s takes master", serverInfo.getIp(), serverInfo.getNodeId()));
        } catch (ZkNodeExistsException e) {
            // follow new master
            masterInfo = zkClient.readData(path);
            followTh = new Thread() {
                @Override
                public void run() {
                    try {
                        String masterIp = masterInfo.getIp();
                        Registry fromRegistry = LocateRegistry.getRegistry(masterIp, 1099);
                        KVService fromKv = (KVService) fromRegistry.lookup("KVService");
                        Registry toRegistry = LocateRegistry.getRegistry("localhost", 1099);
                        KVService toKv = (KVService) toRegistry.lookup("KVService");
                        while (!this.isInterrupted()) {
                            for (String key : fromKv.getKeys()) {
                                toKv.put(key, fromKv.read(key));
                            }
                            System.out.println(String.format("slave on %s syncs from master", masterInfo.getNodeId()));
                            sleep(5000);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            followTh.start();
        }
    }

    public static void main(String[] args) {
        try {

            // construct this server's information
            String ip = args[0];
            String nodeId = args[1];
            int port = 1099;
            String domain = "KVService";
            serverInfo = new ServerInfo(ip, domain, nodeId, port);
            zkClient = new ZkClient(connectString, 5000, 5000, new SerializableSerializer());
            System.setProperty("java.rmi.server.hostname", ip);

            // init root node in zookeeper
            if (!zkClient.exists(clusterPath)) {
                zkClient.createPersistent(clusterPath);
            }
            if (!zkClient.exists(registryPath)) {
                zkClient.createPersistent(registryPath);
            }

            // start kv service
            KVService kv = new KVServiceImpl();
            KVService stub = (KVService) UnicastRemoteObject.exportObject(kv, 8889);
            LocateRegistry.createRegistry(port);
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(domain, stub);
            System.out.println("KVService is online.");

            // compete for master
            String path = String.format("%s/%s", clusterPath, nodeId);
            takeMaster(path);
            zkClient.subscribeDataChanges(path, new IZkDataListener() {
                @Override
                public void handleDataChange(String s, Object o) throws Exception {

                }

                @Override
                public void handleDataDeleted(String s) throws Exception {
                    System.out.println(String.format("master %s on %s crashes", masterInfo.getIp(), masterInfo.getNodeId()));

                    // stop syncing from master
                    if (followTh != null) {
                        followTh.interrupt();
                        followTh.join();
                    }

                    // compete for master
                    takeMaster(path);
                }
            });

            String registerPath = String.format("%s/%s", registryPath, nodeId);
            try {
                zkClient.createPersistent(registerPath);
                System.out.println(String.format("register %s", nodeId));
            } catch (ZkNodeExistsException e) {
                System.out.println(String.format("%s has registered", nodeId));
            }

            // todo: simulate off-line workload

            // wait for master to commit off-line
            zkClient.subscribeDataChanges(registerPath, new IZkDataListener() {
                @Override
                public void handleDataChange(String s, Object o) throws Exception {

                }

                @Override
                public void handleDataDeleted(String s) throws Exception {
                    System.out.println(String.format("%s on %s quits", serverInfo.getIp(), serverInfo.getNodeId()));
                    System.exit(0);
                }
            });
        } catch (RemoteException e) {
            System.out.println("Remote: " + e);
        } catch (AlreadyBoundException e) {
            System.out.println("Already Bound: " + e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}