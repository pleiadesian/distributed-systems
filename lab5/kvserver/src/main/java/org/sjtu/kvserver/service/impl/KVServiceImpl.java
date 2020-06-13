package org.sjtu.kvserver.service.impl;

import org.sjtu.kvserver.service.KVService;

import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;

public class KVServiceImpl implements KVService {

    private static Map<String, String> kv = new HashMap<>();
    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public int put(String key, String value) throws RemoteException {
        kv.put(key, value);
        System.out.println(String.format("%s PUT %s=%s", df.format(new Date()), key, value));
        return 0;
    }

    public String read(String key) throws RemoteException {
        String value = kv.get(key);
        System.out.println(String.format("%s READ %s=%s", df.format(new Date()), key, value));
        return value;
    }

    public int delete(String key) throws RemoteException {
        kv.remove(key);
        System.out.println(String.format("%s DELETE %s", df.format(new Date()), key));
        return 0;
    }

    public List<String> getKeys() throws RemoteException {
        System.out.println(String.format("%s READ ALL KEYS", df.format(new Date())));
        return new ArrayList<>(kv.keySet());
    }
}
