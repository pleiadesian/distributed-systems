package org.sjtu.kvserver;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

public class KVServiceImpl implements KVService {

    private static Map<String, String> kv = new HashMap<String, String>();

    public int put(String key, String value) throws RemoteException {
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
}
