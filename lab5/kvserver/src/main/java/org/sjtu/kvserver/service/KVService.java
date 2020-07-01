package org.sjtu.kvserver.service;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import static org.sjtu.kvserver.config.Config.*;

public interface KVService extends Remote {

    int put(String key, String value) throws RemoteException;

    String read(String key) throws RemoteException;

    int delete(String key) throws RemoteException;

    int syncFromMaster(OpType op, int seqNum, String key, String value) throws RemoteException;

    int getLogSeqNum() throws RemoteException;

    List<String> getKeys() throws RemoteException;

}
