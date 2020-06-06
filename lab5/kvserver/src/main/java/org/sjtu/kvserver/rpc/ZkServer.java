package org.sjtu.kvserver.rpc;

import org.sjtu.kvserver.service.KVService;
import org.sjtu.kvserver.service.ZkService;
import org.sjtu.kvserver.service.impl.KVServiceImpl;
import org.sjtu.kvserver.service.impl.ZkServiceImpl;
import org.sjtu.kvserver.zkp.ZkWatcher;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Enumeration;

public class ZkServer {
    public static void main(String[] args) {
        try {
            // set public IP of rmi server
            String ip = IPSolver.getPublicIP();
            System.setProperty("java.rmi.server.hostname", ip);

            ZkWatcher zkWatcher = new ZkWatcher();
            Thread zkThread = new Thread(zkWatcher);
            zkThread.start();

            // create server object
            ZkService zkService = new ZkServiceImpl();
            // export remote object stub
            ZkService stub = (ZkService) UnicastRemoteObject.exportObject(zkService, 8889);

            // open and get RMIRegistry
            LocateRegistry.createRegistry(1099);
            Registry registry = LocateRegistry.getRegistry();
            // bind name and stub, client uses the name to get corresponding object
            registry.bind("ZkService", stub);
            System.out.println("ZkService is online.");
        } catch (RemoteException e) {
            System.out.println("Remote: " + e);
        } catch (AlreadyBoundException e) {
            System.out.println("Already Bound: " + e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
