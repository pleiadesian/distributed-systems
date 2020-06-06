package org.sjtu.kvserver.rpc;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.service.KVService;
import org.sjtu.kvserver.service.impl.KVServiceImpl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class KVServer {

    public static void main(String[] args) {
        try {
            // register to zookeeper
            String connectString = "172.19.44.153:2181,172.19.44.155:2181,172.19.44.158:2181";
            ZkClient zkClient = new ZkClient(connectString, 5000, 5000, new SerializableSerializer());
            String clusterPath = "/clusterServer";

            int port = 1099;
            String ip = InetAddress.getLocalHost().getHostAddress();
            String domain = "KVService";
            ServerInfo serverInfo = new ServerInfo(ip, domain, port);

            String path = String.format("%s/%s", clusterPath, ip);
            if (zkClient.exists(path)) {
                zkClient.delete(path);
            }
            zkClient.createEphemeral(path);
            zkClient.writeData(path, serverInfo);

            // create server object
            KVService kv = new KVServiceImpl();
            // export remote object stub
            KVService stub = (KVService) UnicastRemoteObject.exportObject(kv, 8889);

            // open and get RMIRegistry
            LocateRegistry.createRegistry(port);
            Registry registry = LocateRegistry.getRegistry();
            // bind name and stub, client uses the name to get corresponding object
            registry.bind(domain, stub);
            System.out.println("KVService is online.");
        } catch (RemoteException e) {
            System.out.println("Remote: " + e);
        } catch (AlreadyBoundException e) {
            System.out.println("Already Bound: " + e);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}