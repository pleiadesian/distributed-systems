package org.sjtu.kvserver.rpc;

import org.junit.Before;
import org.junit.Test;
import org.sjtu.kvserver.service.KVService;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import static org.junit.Assert.*;

/**
 * Simply test the correctness of a kv service on a single kv data node
 */
public class KVServerTest {

    private static KVService kv;

    @Before
    public void setUp() throws Exception {
        try{
            // get register from localhost:1099(host:port)
            Registry registry = LocateRegistry.getRegistry("47.100.165.67", 1099);
            // get remote object by name
            kv = (KVService) registry.lookup("KVService");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testOps() {
        try {
            // PUT a=1
            assertFalse(kv.put("a", "1") < 0);
            // READ a
            assertEquals("1", kv.read("a"));
            // DELETE a
            assertFalse(kv.delete("a") < 0);
            // READ a again;
            assertNull(kv.read("a"));
        } catch (RemoteException e) {
            e.printStackTrace();
            assert(false);
        }
    }
}
