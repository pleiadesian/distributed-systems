package org.sjtu.kvserver;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIClient {

    public static void main(String[] args) {
        try{
            // get register from localhost:1099(host:port)
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            // get remote object by name
            ComputingService computingObj = (ComputingService) registry.lookup("rmi://localhost:1099/ComputingService");
            // call remote object's method
            System.out.println(computingObj.add(5, 6));
        } catch (RemoteException e) {
            System.out.println("Remote: " + e);
        } catch (NotBoundException e) {
            System.out.println("Not Bound: " + e);
        }
    }
}
