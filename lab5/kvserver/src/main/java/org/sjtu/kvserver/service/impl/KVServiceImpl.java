package org.sjtu.kvserver.service.impl;

import org.sjtu.kvserver.service.KVService;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KVServiceImpl implements KVService {

    private static Map<String, String> kv = new HashMap<>();

    public int put(String key, String value) throws RemoteException {
        System.out.println();
        kv.put(key, value);
        return 0;
    }

    public String read(String key) throws RemoteException {
        return kv.get(key);
    }

    public int delete(String key) throws RemoteException {
        kv.remove(key);
        return 0;
    }

    public List<String> getKeys() throws RemoteException {
        return new ArrayList<>(kv.keySet());
    }
}
