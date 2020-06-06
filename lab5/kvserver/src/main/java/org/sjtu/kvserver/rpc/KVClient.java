package org.sjtu.kvserver.rpc;

import org.sjtu.kvserver.service.KVService;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class KVClient {

    public static void main(String[] args) {
        try{
            // get register from localhost:1099(host:port)
            Registry registry = LocateRegistry.getRegistry("106.14.124.46", 1099);
            // get remote object by name
            KVService kv = (KVService) registry.lookup("KVService");
            // call remote object's method
            System.out.println("PUT a=1");
            if (kv.put("a", "1") < 0)
                throw(new Exception("put failed"));
            System.out.println("READ a: " + kv.read("a"));
            System.out.println("DELETE a");
            if (kv.delete("a") < 0)
                throw(new Exception("delete failed"));
            System.out.println("READ a again");
            if (kv.read("a") != null)
                throw(new Exception("delete error"));
        } catch (RemoteException e) {
            System.out.println("Remote: " + e);
        } catch (NotBoundException e) {
            System.out.println("Not Bound: " + e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}