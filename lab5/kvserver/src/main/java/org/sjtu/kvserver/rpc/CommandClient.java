package org.sjtu.kvserver.rpc;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.sjtu.kvserver.entity.ServerInfo;
import org.sjtu.kvserver.service.KVService;
import org.sjtu.kvserver.service.ZkService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import static org.sjtu.kvserver.config.Config.masterPath;
import static org.sjtu.kvserver.config.Config.publicConnectString;

public class CommandClient {

    private static ZkClient zkClient = new ZkClient(publicConnectString, 5000, 5000, new SerializableSerializer());

    /**
     * Find the master node
     * @return service on the master node
     */
    private static ZkService connectMaster() {
        try {
            String masterIp = ((ServerInfo) zkClient.readData(masterPath)).getIp();
            Registry registry = LocateRegistry.getRegistry(masterIp, 1099);
            return (ZkService) registry.lookup("ZkService");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String clientId = UUID.randomUUID().toString();
        Scanner scanner = new Scanner(System.in);
        while (true) {
            ZkService zk = connectMaster();
            while (true) {
                try {
                    String input = scanner.nextLine();
                    String[] commands = input.split(" ");
                    if (commands.length < 2) {
                        System.out.println("No such command");
                    }
                    String command = commands[0];
                    String key = commands[1];
                    String nodeIp;
                    try {
                        // log in of the kv cluster
                        zk.login(clientId);

                        // get node ID
                        nodeIp = zk.getNode(key, clientId);
                    } catch (Exception e) {
                        e.printStackTrace();
                        zk.logout(clientId);
                        break;
                    }
                    Registry nodeRegistry = LocateRegistry.getRegistry(nodeIp, 1099);
                    KVService kv = (KVService) nodeRegistry.lookup("KVService");
                    if ("READ".equals(command)) {
                        System.out.println(kv.read(key));
                    } else if ("PUT".equals(command)) {
                        String value = commands[2];
                        if (kv.put(key, value) < 0) {
                            System.out.println("Failed");
                        } else {
                            System.out.println("Succeeded");
                        }
                    } else if ("DELETE".equals(command)) {
                        if (kv.delete(key) < 0) {
                            System.out.println("Failed");
                        } else {
                            System.out.println("Succeeded");
                        }
                    } else {
                        System.out.println("No such command");
                    }
                    zk.logout(clientId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
