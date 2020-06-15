package org.sjtu.kvserver.rpc;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.sjtu.kvserver.entity.NodeInfo;
import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.service.KVService;
import org.sjtu.kvserver.service.impl.KVServiceImpl;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

import static java.lang.Thread.sleep;
import static org.sjtu.kvserver.config.Config.*;
import static org.sjtu.kvserver.log.LogManager.redo;

public class KVServer {

    private static ServerInfo serverInfo;
    private static ServerInfo masterInfo;

    // Thread for slave to sync from master
    private static Thread followTh;

    /**
     * Complete for master
     * @param path znode for master election
     */
    private static void takeMaster(String path) {
        try {
            zkClient.createEphemeral(path);
            zkClient.writeData(path, serverInfo);
            logger.warning(String.format("%s on %s takes master", serverInfo.getIp(), serverInfo.getNodeId()));
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
                            List<String> fromKeys = fromKv.getKeys();
                            for (String key : fromKeys) {
                                String value = fromKv.read(key);
                                if (value != null) {
                                    toKv.put(key, value);
                                }
                            }
                            List<String> toKeys = toKv.getKeys();
                            toKeys.removeAll(fromKeys);
                            for (String key : toKeys) {
                                toKv.delete(key);
                            }
                            logger.warning(String.format("slave on %s syncs from master", masterInfo.getNodeId()));
                            sleep(500);
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
            System.setProperty("java.rmi.server.hostname", ip);

            // connect to zookeeper cluster
            connect();

            // start kv service
            KVService kv = new KVServiceImpl();
            KVService stub = (KVService) UnicastRemoteObject.exportObject(kv, 8889);
            LocateRegistry.createRegistry(port);
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(domain, stub);
            logger.warning("KVService is online.");

            // redo by scan log
            redo(stub);

            // compete for master
            String path = String.format("%s/%s", clusterPath, nodeId);
            takeMaster(path);
            zkClient.subscribeDataChanges(path, new IZkDataListener() {
                @Override
                public void handleDataChange(String s, Object o) throws Exception {

                }

                @Override
                public void handleDataDeleted(String s) throws Exception {
                    logger.warning(String.format("master %s on %s crashes", masterInfo.getIp(), masterInfo.getNodeId()));

                    // stop syncing from master
                    if (followTh != null) {
                        followTh.interrupt();
                        followTh.join();
                    }

                    // compete for master
                    takeMaster(path);
                }
            });

            // Register this data node
            NodeInfo nodeInfo = new NodeInfo(false);
            String registerPath = String.format("%s/%s", registryPath, nodeId);
            try {
                zkClient.createPersistent(registerPath);
                zkClient.writeData(registerPath, nodeInfo);
                logger.warning(String.format("register %s", nodeId));
            } catch (ZkNodeExistsException e) {
                logger.warning(String.format("%s has registered", nodeId));
            }

            // wait for master to commit off-line
            zkClient.subscribeDataChanges(registerPath, new IZkDataListener() {
                @Override
                public void handleDataChange(String s, Object o) throws Exception {

                }

                @Override
                public void handleDataDeleted(String s) throws Exception {
                    logger.warning(String.format("%s on %s quits", serverInfo.getIp(), serverInfo.getNodeId()));
                    System.exit(0);
                }
            });

            // go off-line after some time
            if (args.length > 2) {
                int sleepInterval = Integer.valueOf(args[2]) * 1000;
                sleep(sleepInterval);
                nodeInfo.setOffline(true);
                zkClient.writeData(registerPath, nodeInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}