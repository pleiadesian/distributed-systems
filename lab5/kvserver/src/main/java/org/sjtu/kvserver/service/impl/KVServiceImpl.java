package org.sjtu.kvserver.service.impl;

import org.sjtu.kvserver.service.KVService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.sjtu.kvserver.config.Config.*;
import static org.sjtu.kvserver.log.LogManager.log;

public class KVServiceImpl implements KVService {

    private static Map<String, String> kv = new ConcurrentHashMap<>();

    /**
     * PUT
     * @param key key to PUT
     * @param value value of the key
     * @return 0 when succeeded, -1 when failed
     */
    public int put(String key, String value) {
        // write-after-log
        if (wal) {
            try {
                log(OpType.PUT, key, value);
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }
        kv.put(key, value);
        logger.info(String.format("%s PUT %s=%s", df.format(new Date()), key, value));
        return 0;
    }

    /**
     * READ
     * @param key key to READ
     * @return value when succeeded, null when failed
     */
    public String read(String key) {
        // write-after-log
        if (wal) {
            try {
                log(OpType.READ, key, null);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        String value = kv.get(key);
        logger.info(String.format("%s READ %s=%s", df.format(new Date()), key, value));
        return value;
    }

    /**
     * DELETE
     * @param key key to DELETE
     * @return 0 when succeeded, -1 when failed
     */
    public int delete(String key) {
        // write-after-log
        if (wal) {
            try {
                log(OpType.DELETE, key, null);
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }
        kv.remove(key);
        logger.info(String.format("%s DELETE %s", df.format(new Date()), key));
        return 0;
    }

    /**
     * get all keys when migrating data from nodes to nodes
     * @return list of keys on this node
     */
    public List<String> getKeys() {
        logger.info(String.format("%s READ ALL KEYS", df.format(new Date())));
        return new ArrayList<>(kv.keySet());
    }
}
