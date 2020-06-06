package org.sjtu.kvserver.service;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface KVService extends Remote {

    int put(String key, String value) throws RemoteException;

    String read(String key) throws RemoteException;

    int delete(String key) throws RemoteException;

    List<String> getKeys() throws RemoteException;

}
