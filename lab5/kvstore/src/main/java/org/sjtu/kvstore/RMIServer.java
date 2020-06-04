package org.sjtu.kvstore;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class RMIServer {
    public static void main(String[] args) {
        try {
            KvService kvService = new KvServiceImpl();
            KvService stub=(KvService) UnicastRemoteObject.exportObject(kvService, 2888);
            LocateRegistry.createRegistry(1099);
            Registry registry= LocateRegistry.getRegistry();
            registry.bind("KvService", stub);
            System.out.println("绑定成功!");
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (AlreadyBoundException e) {
            e.printStackTrace();
        }
    }
}
