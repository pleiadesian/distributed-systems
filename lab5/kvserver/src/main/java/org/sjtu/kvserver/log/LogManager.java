package org.sjtu.kvserver.log;

import org.sjtu.kvserver.service.KVService;

import java.io.*;

import static org.sjtu.kvserver.config.Config.*;

public class LogManager {

    /**
     * Log a kv request processed by the kv service
     * @param op operation type
     * @param key operated key
     * @param value value of the key
     * @throws IOException exception may occur when creating log file or write a new log
     */
    public static void log(OpType op, String key, String value) throws IOException {
        File logFile = new File(logFilename);

        if(!logFile.exists()) {
            logFile.createNewFile();
        }

        String logLine;
        if (op == OpType.PUT) {
            logLine = op + " " + key + " " + value;
        } else {
            logLine = op + " " + key;
        }

        Writer logWriter = new FileWriter(logFile,true);
        logWriter.write(logLine + "\n");
        logWriter.flush();
        logWriter.close();
    }

    /**
     * Crash recovery for a kv service
     * @param localKv local kv service
     * @throws IOException exception may occur when reading logs
     */
    public static void redo(KVService localKv) throws IOException {
        File logFile = new File(logFilename);

        if (logFile.exists()) {
            System.out.println("recovering from redo log");

            // do not log any operations when recovering
            wal = false;

            FileInputStream inputStream = new FileInputStream(logFilename);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            String logLine;
            while((logLine = bufferedReader.readLine()) != null) {
                String[] buf = logLine.split(" ");

                // READ operations have no effect on the state of the kv service, do nothing with them
                if ("PUT".equals(buf[0])) {
                    String key = buf[1];
                    String value = buf[2];
                    localKv.put(key, value);
                } else if ("DELETE".equals(buf[1])) {
                    String key = buf[1];
                    localKv.delete(key);
                }
            }

            inputStream.close();
            bufferedReader.close();
            wal = true;
            System.out.println("recovery completed");
        }
    }

}
