package apr.methodrecorder;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class MethodTraceLogger {
    private static PrintWriter writer;
    private static String logFile;

    public static synchronized void init(String fileName) {
        if (writer != null) return;

        logFile = fileName;
        try {
            writer = new PrintWriter(new FileWriter(logFile, true));
        } catch (IOException e) {
            System.err.println("[MethodTrace] Failed to init log file: " + e.getMessage());
        }
    }

    public static void logStart(String className, String methodName, int startLine, int endLine) {
        if (writer == null) return;

        synchronized (MethodTraceLogger.class) {
            writer.printf("[START] %s.%s:%d-%d%n",
                    className, methodName, startLine, endLine);
            writer.flush();
        }
    }

    public static void logEnd(String className, String methodName, int startLine, int endLine) {
        if (writer == null) return;

        synchronized (MethodTraceLogger.class) {
            writer.printf("[END] %s.%s:%d-%d%n",
                    className, methodName, startLine, endLine);
            writer.flush();
        }
    }
}