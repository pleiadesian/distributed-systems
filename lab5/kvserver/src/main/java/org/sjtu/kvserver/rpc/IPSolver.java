package org.sjtu.kvserver.rpc;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class IPSolver {
    public static String getPublicIP() throws Exception {
        String ip = InetAddress.getLocalHost().getHostAddress();
        Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();
        InetAddress inetAddr;
        boolean finded = false;
        while (netInterfaces.hasMoreElements() && !finded) {
            NetworkInterface ni = netInterfaces.nextElement();
            Enumeration<InetAddress> address = ni.getInetAddresses();
            while (address.hasMoreElements()) {
                inetAddr = address.nextElement();
                if (!inetAddr.isSiteLocalAddress() && !inetAddr.isLoopbackAddress() &&
                        !inetAddr.getHostAddress().contains(":")) {
                    ip = inetAddr.getHostAddress();
                    finded = true;
                    break;
                }
            }
        }
        if (!finded) {
            throw(new Exception("public IP not found"));
        }

        return ip;
    }
}
