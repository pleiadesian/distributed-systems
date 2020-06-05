package org.sjtu.kvserver.dht;

import java.util.*;

public class ConsistentHashing {
    private final int VIRTUAL_COPIES = 1048576;
    private TreeMap<Long, String> virtualNodes = new TreeMap<>();

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

    public void addPhysicalNode(String nodeIp) {
        for (int idx = 0; idx < VIRTUAL_COPIES; ++idx) {
            long hash = FNVHash(nodeIp + "#" + idx);
            virtualNodes.put(hash, nodeIp);
        }
    }

    public void removePhysicalNode(String nodeIp) {
        for (int idx = 0; idx < VIRTUAL_COPIES; ++idx) {
            long hash = FNVHash(nodeIp + "#" + idx);
            virtualNodes.remove(hash);
        }
    }

    public String getObjectNode(String object) {
        long hash = FNVHash(object);
        SortedMap<Long, String> tailMap = virtualNodes.tailMap(hash);
        Long key = tailMap.isEmpty() ? virtualNodes.firstKey() : tailMap.firstKey();
        return virtualNodes.get(key);
    }

//    // 统计对象与节点的映射关系
//    public void dumpObjectNodeMap(String label, int objectMin, int objectMax) {
//        // 统计
//        Map<String, Integer> objectNodeMap = new TreeMap<>(); // IP => COUNT
//        for (int object = objectMin; object <= objectMax; ++object) {
//            String nodeIp = getObjectNode(Integer.toString(object));
//            Integer count = objectNodeMap.get(nodeIp);
//            objectNodeMap.put(nodeIp, (count == null ? 0 : count + 1));
//        }
//
//        // 打印
//        double totalCount = objectMax - objectMin + 1;
//        System.out.println("======== " + label + " ========");
//        for (Map.Entry<String, Integer> entry : objectNodeMap.entrySet()) {
//            long percent = (int) (100 * entry.getValue() / totalCount);
//            System.out.println("IP=" + entry.getKey() + ": RATE=" + percent + "%");
//        }
//    }

//    public static void main(String[] args) {
//        ConsistentHashing ch = new ConsistentHashing();
//
//        ch.dumpObjectNodeMap("初始情况", 0, 65536);
//
//        ch.removePhysicalNode("192.168.1.103");
//        ch.dumpObjectNodeMap("删除物理节点", 0, 65536);
//
//        ch.addPhysicalNode("192.168.1.108");
//        ch.dumpObjectNodeMap("添加物理节点", 0, 65536);
//    }
}
