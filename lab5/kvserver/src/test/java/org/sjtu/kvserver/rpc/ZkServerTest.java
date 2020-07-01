package org.sjtu.kvserver.rpc;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sjtu.kvserver.service.ZkService;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Simply test the load balance of consistent hashing on a master node
 */
public class ZkServerTest {

    private static String clientId;
    private static ZkService zk;

    @Before
    public void setUp() throws Exception {
        try{
            // get register from localhost:1099(host:port)
            Registry registry = LocateRegistry.getRegistry("101.132.122.146", 1099);
            // get remote object by name
            zk = (ZkService) registry.lookup("ZkService");
            // generate a clientID
            clientId = UUID.randomUUID().toString();
            zk.login(clientId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testLoadBalance() {
        try {
            // get corresponding node ID of objects
            Map<String, Integer> objectNodeMap = new TreeMap<>();
            for (int object = 0; object <= 999; ++object) {
                String nodeIp = zk.getNode(Integer.toString(object), clientId);
                Integer count = objectNodeMap.get(nodeIp);
                objectNodeMap.put(nodeIp, (count == null ? 1 : count + 1));
            }

            // dump distribution
            double totalCount = 1000;
            for (Map.Entry<String, Integer> entry : objectNodeMap.entrySet()) {
                double percent = (100 * entry.getValue() / totalCount);
                System.out.println(String.format("IP=%s: RATE=%.3f%%", entry.getKey(), percent));
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            assert(false);
        }
    }

    @After
    public void tearDown() throws Exception {
        zk.logout(clientId);
    }
}
