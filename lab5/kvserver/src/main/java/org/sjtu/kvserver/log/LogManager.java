package org.sjtu.kvserver.log;

import org.sjtu.kvserver.service.KVService;

import java.io.*;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.CRC32;

import static java.lang.Thread.sleep;
import static org.sjtu.kvserver.config.Config.*;

public class LogManager {

    // protect concurrent log operation
    private static ReentrantLock logLock = new ReentrantLock();

    // log sequence number
    private static int logSeqNum = 0;

    public static int getLogSeqNum() {
        return logSeqNum;
    }

    public static void setLogSeqNum(int logSeqNum) {
        LogManager.logSeqNum = logSeqNum;
    }

    /**
     * Calculate CRC32 for value
     * @param value value
     * @return CRC32
     */
    private static String getCRC32(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(value.getBytes());
        return String.valueOf(crc32.getValue());
    }

    /**
     * Log a kv request processed by the kv service
     * @param op operation type
     * @param key operated key
     * @param value value of the key
     * @throws IOException exception may occur when creating log file or write a new log
     */
    public static int log(OpType op, String key, String value, int seqNum) throws IOException {
        File logFile = new File(logFilename);

        if(!logFile.exists()) {
            logFile.createNewFile();
        }

        String record;
        if (op == OpType.PUT) {
            record = op + " " + key + " " + value;
        } else {
            record = op + " " + key;
        }

        String crc = getCRC32(record);
        String logLine = crc + " " + record;

        if (seqNum > 0) {
            // log on slave, discard duplicated sync operation
            if (seqNum < logSeqNum) {
                return seqNum;
            }
            // simple ticket lock implementation, wait until all previous operations to be done
            int retry = 0;
            while (seqNum != logSeqNum) {
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                retry++;
                // timeout, master is down, do not wait
                if (retry > 30) {
                    throw new IOException();
                }
            }
            logger.info(String.format("%s commit seqnum=%d", df.format(new Date()), seqNum));
            Writer logWriter = new FileWriter(logFile, true);
            logWriter.write(logLine + "\n");
            logWriter.flush();
            logWriter.close();
            logSeqNum++;
            return seqNum;
        } else {
            // log on master
            logLock.lock();
            int currSeqNum = logSeqNum;
            logSeqNum++;
            Writer logWriter = new FileWriter(logFile, true);
            logWriter.write(logLine + "\n");
            logWriter.flush();
            logWriter.close();
            logLock.unlock();
            return currSeqNum;
        }
    }

    /**
     * Crash recovery for a kv service
     * @param localKv local kv service
     * @throws IOException exception may occur when reading logs
     */
    public static void redo(KVService localKv) throws IOException {
        File logFile = new File(logFilename);

        if (logFile.exists()) {
            logger.warning("recovering from redo log");

            // do not log any operations when recovering
            wal = false;

            FileInputStream inputStream = new FileInputStream(logFilename);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            String logLine;
            while((logLine = bufferedReader.readLine()) != null) {
                // crc check, discard this record when failed
                String[] buf = logLine.split(" ", 2);
                String crc = buf[0];
                String record = buf[1];
                if (!getCRC32(record).equals(crc)) {
                    logger.severe(String.format("detect log record corruption: %s", record));
                    continue;
                }

                // READ operations have no effect on the state of the kv service, do nothing with them
                String[] recordBuf = record.split(" ");
                if ("PUT".equals(recordBuf[0])) {
                    String key = recordBuf[1];
                    String value = recordBuf[2];
                    localKv.put(key, value);
                } else if ("DELETE".equals(recordBuf[0])) {
                    String key = recordBuf[1];
                    localKv.delete(key);
                }
            }

            inputStream.close();
            bufferedReader.close();
            wal = true;
            logger.warning("recovery completed");
        }
    }

}
