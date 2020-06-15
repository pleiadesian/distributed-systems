package org.sjtu.kvserver.rpc;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.service.ZkService;
import org.sjtu.kvserver.service.impl.ZkServiceImpl;
import org.sjtu.kvserver.zkp.ZkWatcher;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import static org.sjtu.kvserver.config.Config.*;
import static org.sjtu.kvserver.config.Config.zkClient;

public class ZkServer {

    private static ServerInfo serverInfo;

    /**
     * Complete for master
     * @param path znode for master election
     * @return if master is taken by this node
     */
    private static boolean takeMaster(String path) {
        try {
            zkClient.createEphemeral(path);
            zkClient.writeData(path, serverInfo);
            logger.warning(String.format("%s takes master", serverInfo.getIp()));
            return true;
        } catch (ZkNodeExistsException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        try {
            // set public IP of rmi server
            String ip = args[0];
            serverInfo = new ServerInfo(ip, "", "", 0);
            System.setProperty("java.rmi.server.hostname", ip);

            // connect to zookeeper cluster
            connect();

            // refresh zookeeper state if kv cluster restarts
            if ("init".equals(args[1])) {
                refresh();
            }

            // customize log level
            if (args.length > 2) {
                loggerLevelConfig(args[2]);
            }

            // Zookeeper watcher thread
            ZkWatcher zkWatcher = new ZkWatcher();
            Thread zkThread = new Thread(zkWatcher);

            // compete for master
            String path = masterPath;
            if (takeMaster(path)) {
                logger.warning("this node takes master");
                zkThread.start();
            }
            zkClient.subscribeDataChanges(path, new IZkDataListener() {
                @Override
                public void handleDataChange(String s, Object o) throws Exception {

                }

                @Override
                public void handleDataDeleted(String s) throws Exception {
                    logger.warning(String.format("master %s crashes", serverInfo.getIp()));
                    if (takeMaster(path)) {
                        logger.warning("this node takes master");
                        zkThread.start();
                    }
                }
            });

            // create server object
            ZkService zkService = new ZkServiceImpl();
            // export remote object stub
            ZkService stub = (ZkService) UnicastRemoteObject.exportObject(zkService, 8889);

            // open and get RMIRegistry
            LocateRegistry.createRegistry(1099);
            Registry registry = LocateRegistry.getRegistry();
            // bind name and stub, client uses the name to get corresponding object
            registry.bind("ZkService", stub);
            logger.warning("ZkService is online.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
