package org.sjtu.kvserver.log;

import org.sjtu.kvserver.service.KVService;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;
import static org.sjtu.kvserver.config.Config.*;

public class LogManager {
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

    public static void redo(KVService localKv) throws IOException {
        File logFile = new File(logFilename);

        if (logFile.exists()) {
            System.out.println("recovering from redo log");
            wal = false;
            FileInputStream inputStream = new FileInputStream(logFilename);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            String logLine;
            while((logLine = bufferedReader.readLine()) != null) {
                String[] buf = logLine.split(" ");
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
