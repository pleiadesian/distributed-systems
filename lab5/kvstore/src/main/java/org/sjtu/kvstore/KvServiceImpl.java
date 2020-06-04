package org.sjtu.kvstore;

import java.rmi.RemoteException;

public class KvServiceImpl implements KvService {
    @Override
    public String sayHello() throws RemoteException {
        return "Hello Word!";
    }
}
