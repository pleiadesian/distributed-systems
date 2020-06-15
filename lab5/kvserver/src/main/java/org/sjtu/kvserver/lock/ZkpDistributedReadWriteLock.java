package org.sjtu.kvserver.lock;

import org.I0Itec.zkclient.IZkDataListener;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.sjtu.kvserver.config.Config.lockPath;
import static org.sjtu.kvserver.config.Config.zkClient;

/**
 * Distributed read-write lock implementation based on Zookeeper cluster
 */
public class ZkpDistributedReadWriteLock {
    private String thisReadLock;
    private String thisWriteLock;

    private enum LockType
    {
        READ,
        WRITE
    }

    /**
     * Sort nodes by the sequence number in the node name
     * @param nodes node list to be sorted
     */
    private void sortNodes(List<String> nodes)
    {
        nodes.sort(Comparator.comparing(o -> o.split("-")[1]));
    }

    /**
     * Acquire a read lock on the Zookeeper cluster
     */
    public void lockRead()
    {
        CountDownLatch readLatch = new CountDownLatch(1);
        String thisLockNodeBuilder = lockPath + "/" + LockType.READ + "-";
        thisReadLock = zkClient.createEphemeralSequential(thisLockNodeBuilder , "");

        List<String> tmp_nodes = zkClient.getChildren(lockPath);
        sortNodes(tmp_nodes);
        int tmp_index = 0;
        for (int i = tmp_nodes.size() - 1; i >= 0; i--) {
            if (thisReadLock.equals(lockPath + "/" + tmp_nodes.get(i))) {
                // find znode of this read lock
                tmp_index = i;
            } else if (i < tmp_index && tmp_nodes.get(i).split("-")[0].equals(LockType.WRITE.toString())) {
                // a write lock is held by another node, wait for it
                zkClient.subscribeDataChanges(lockPath + "/" + tmp_nodes.get(i), new IZkDataListener() {
                    @Override
                    public void handleDataChange(String s, Object o) throws Exception {

                    }

                    @Override
                    public void handleDataDeleted(String s) throws Exception {
                        readLatch.countDown();
                    }
                });
                try {
                    readLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    /**
     * Release a read lock on the Zookeeper cluster
     */
    public void unlockRead()
    {
        if (this.thisReadLock != null)
        {
            zkClient.delete(thisReadLock);
            thisReadLock = null;
        }
    }

    /**
     * Acquire a write lock on the Zookeeper cluster
     */
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
                    // a lock is held by another node, wait for it
                    String holderPath = lockPath + "/" + tmp_nodes.get(i - 1);
                    IZkDataListener releaseListener = new IZkDataListener() {
                        @Override
                        public void handleDataChange(String s, Object o) throws Exception {

                        }

                        @Override
                        public void handleDataDeleted(String s) throws Exception {
                            writeLatch.countDown();
                        }
                    };
                    zkClient.subscribeDataChanges(holderPath, releaseListener);
                    try {
                        while (zkClient.getChildren(lockPath).contains(tmp_nodes.get(i - 1))) {
                            writeLatch.await(500, TimeUnit.MILLISECONDS);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    zkClient.unsubscribeDataChanges(holderPath, releaseListener);
                    break;
                }
            }
        }
    }

    /**
     * Release a write lock on the Zookeeper cluster
     */
    public void unlockWrite()
    {
        if (thisWriteLock != null)
        {
            zkClient.delete(thisWriteLock);
            thisWriteLock = null;
        }
    }
}