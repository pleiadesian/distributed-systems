package org.sjtu.kvserver.service.impl;

import org.sjtu.kvserver.config.Config;
import org.sjtu.kvserver.service.KVService;

import java.io.IOException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.sjtu.kvserver.config.Config.*;
import static org.sjtu.kvserver.log.LogManager.log;

public class KVServiceImpl implements KVService {

    private static Map<String, String> kv = new ConcurrentHashMap<>();

    public int put(String key, String value) throws RemoteException {
        if (wal) {
            try {
                log(OpType.PUT, key, value);
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }
        kv.put(key, value);
        System.out.println(String.format("%s PUT %s=%s", df.format(new Date()), key, value));
        return 0;
    }

    public String read(String key) throws RemoteException {
        if (wal) {
            try {
                log(OpType.READ, key, null);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        String value = kv.get(key);
        System.out.println(String.format("%s READ %s=%s", df.format(new Date()), key, value));
        return value;
    }

    public int delete(String key) throws RemoteException {
        if (wal) {
            try {
                log(OpType.DELETE, key, null);
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }
        kv.remove(key);
        System.out.println(String.format("%s DELETE %s", df.format(new Date()), key));
        return 0;
    }

    public List<String> getKeys() throws RemoteException {
        System.out.println(String.format("%s READ ALL KEYS", df.format(new Date())));
        return new ArrayList<>(kv.keySet());
    }
}
