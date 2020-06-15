package org.sjtu.kvserver.dht;

import java.util.*;

public class ConsistentHashing {
    // virtual nodes
    private final int VIRTUAL_COPIES = 1048576;
    private TreeMap<Long, String> virtualNodes = new TreeMap<>();

    /**
     * Hash function
     * @param key key to hash
     * @return hash value of key
     */
    private static Long FNVHash(String key) {
        final int p = 16777619;
        Long hash = 2166136261L;
        for (int idx = 0, num = key.length(); idx < num; ++idx) {
            hash = (hash ^ key.charAt(idx)) * p;
        }
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;

        if (hash < 0) {
            hash = Math.abs(hash);
        }
        return hash;
    }

    /**
     * Add a new node to cluster
     * @param nodeIp IP of new node
     */
    public void addPhysicalNode(String nodeIp) {
        for (int idx = 0; idx < VIRTUAL_COPIES; ++idx) {
            long hash = FNVHash(nodeIp + "#" + idx);
            virtualNodes.put(hash, nodeIp);
        }
    }

    /**
     * Remove a node in the cluster
     * @param nodeIp IP of the node to be removed
     */
    public void removePhysicalNode(String nodeIp) {
        for (int idx = 0; idx < VIRTUAL_COPIES; ++idx) {
            long hash = FNVHash(nodeIp + "#" + idx);
            virtualNodes.remove(hash);
        }
    }

    /**
     * Find which node the object belongs to by consistent hashing
     * @param object object to be found
     * @return IP of the node which the object belongs to
     */
    public String getObjectNode(String object) {
        long hash = FNVHash(object);
        SortedMap<Long, String> tailMap = virtualNodes.tailMap(hash);
        Long key = tailMap.isEmpty() ? virtualNodes.firstKey() : tailMap.firstKey();
        return virtualNodes.get(key);
    }
}
