package org.sjtu.kvserver;

import java.rmi.RemoteException;

public class ComputingServiceImpl implements ComputingService {

    public int add(int a, int b) throws RemoteException {
        return a + b;
    }

}
