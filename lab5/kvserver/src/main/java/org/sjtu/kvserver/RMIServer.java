package org.sjtu.kvserver;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class RMIServer {

    public static void main(String[] args) {

        try {
            // create server object
            ComputingService computingServant = new ComputingServiceImpl();
            // export remote object stub
            ComputingService stub = (ComputingService) UnicastRemoteObject.exportObject(computingServant, 8889);

            // open and get RMIRegistry
            LocateRegistry.createRegistry(1099);
            Registry registry = LocateRegistry.getRegistry();
            // bind name and stub, client uses the name to get corresponding object
            registry.bind("rmi://localhost:1099/ComputingService", stub);
            System.out.println("ComputingService is online.");
        } catch (RemoteException e) {
            System.out.println("Remote: " + e);
        } catch (AlreadyBoundException e) {
            System.out.println("Already Bound: " + e);
        }
    }
}