package org.sjtu.kvserver;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ComputingService extends Remote {

    /**
     * add two integer to a sum
     * @param a an integer
     * @param b an integer
     * @return the sum of a and b
     * @throws RemoteException if call fails
     */
    int add(int a, int b) throws RemoteException;

}
