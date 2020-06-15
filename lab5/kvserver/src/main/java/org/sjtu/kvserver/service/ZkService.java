package org.sjtu.kvserver.service;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ZkService extends Remote {

    String getNode(String key, String clientId) throws RemoteException;

    void login(String clientId) throws RemoteException;

    void logout(String clientId) throws RemoteException;

}
