package org.sjtu.kvserver;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface KVService extends Remote {

    int put(String key, String value) throws RemoteException;

    String read(String key) throws RemoteException;

    int delete(String key) throws RemoteException;

}
