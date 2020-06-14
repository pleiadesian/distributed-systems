package org.sjtu.kvserver.lock;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.sjtu.kvserver.config.Config.lockPath;
import static org.sjtu.kvserver.config.Config.zkClient;

public class ZkpDistributedReadWriteLock {
    private String thisReadLock;
    private String thisWriteLock;

    public void lockRead()
    {
        CountDownLatch readLatch = new CountDownLatch(1);
        String thisLockNodeBuilder = lockPath + "/" + LockType.READ + "-";
        thisReadLock = zkClient.createEphemeralSequential(thisLockNodeBuilder , "");

        List<String> tmp_nodes = zkClient.getChildren(lockPath);
        sortNodes(tmp_nodes);
        tmp_nodes.forEach(System.out::println);
        int tmp_index = 0;
        for (int i = tmp_nodes.size() - 1; i >= 0; i--) {
            if (thisReadLock.equals(lockPath + "/" + tmp_nodes.get(i))) {
                tmp_index = i;
            } else if (i < tmp_index && tmp_nodes.get(i).split("-")[0].equals(LockType.WRITE.toString())) {
                zkClient.subscribeChildChanges(lockPath + "/" + tmp_nodes.get(i) , (parentPath , currentChilds) -> readLatch.countDown());
                try {
                    readLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    public void unlockRead()
    {
        if (this.thisReadLock != null)
        {
            zkClient.delete(thisReadLock);
            thisReadLock = null;
        }
    }

    public void lockWrite()
    {
        CountDownLatch writeLatch = new CountDownLatch(1);
        String thisLockNodeBuilder = lockPath + "/" + LockType.WRITE + "-";
        thisWriteLock = zkClient.createEphemeralSequential(thisLockNodeBuilder , "");

        List<String> tmp_nodes = zkClient.getChildren(lockPath);
        sortNodes(tmp_nodes);
        for (int i = tmp_nodes.size() - 1; i >= 0; i--) {
            if (thisWriteLock.equals(lockPath + "/" + tmp_nodes.get(i))) {
                if (i > 0) {
                    zkClient.subscribeChildChanges(lockPath + "/" + tmp_nodes.get(i - 1) , (parentPath , currentChilds) -> writeLatch.countDown());
                    try {
                        writeLatch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }

    public void unlockWrite()
    {
        if (thisWriteLock != null)
        {
            zkClient.delete(thisWriteLock);
            thisWriteLock = null;
        }
    }

    private void sortNodes(List<String> nodes)
    {
        nodes.sort(Comparator.comparing(o -> o.split("-")[1]));
    }

    private enum LockType
    {
        READ,
        WRITE
    }
}