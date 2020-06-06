package org.sjtu.kvserver.rpc;

import org.sjtu.kvserver.service.KVService;
import org.sjtu.kvserver.service.ZkService;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.TreeMap;

public class ZkClient {

    public static void main(String[] args) {
        try{
            // get register from localhost:1099(host:port)
            Registry registry = LocateRegistry.getRegistry("139.196.33.196", 1099);
            // get remote object by name
            ZkService zk = (ZkService) registry.lookup("ZkService");
            // call remote object's method
            Map<String, Integer> objectNodeMap = new TreeMap<>(); // IP => COUNT
            for (int object = 0; object <= 999; ++object) {
                String nodeIp = zk.getNode(Integer.toString(object));
                Integer count = objectNodeMap.get(nodeIp);
                objectNodeMap.put(nodeIp, (count == null ? 0 : count + 1));
            }

            double totalCount = 1000;
            for (Map.Entry<String, Integer> entry : objectNodeMap.entrySet()) {
                long percent = (int) (100 * entry.getValue() / totalCount);
                System.out.println("IP=" + entry.getKey() + ": RATE=" + percent + "%");
            }
        } catch (RemoteException e) {
            System.out.println("Remote: " + e);
        } catch (NotBoundException e) {
            System.out.println("Not Bound: " + e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
