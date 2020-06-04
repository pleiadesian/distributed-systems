package org.sjtu.kvclient;

import org.sjtu.kvserver.KvService;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIClient {
    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            KvService kvService = (KvService) registry.lookup( "KvService");
            String ret = kvService.sayHello();
            System.out.println( ret);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
    }
}
