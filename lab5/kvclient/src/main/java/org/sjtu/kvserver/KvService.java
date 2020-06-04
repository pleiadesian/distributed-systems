package org.sjtu.kvserver;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface KvService extends Remote {
    String sayHello() throws RemoteException;
}