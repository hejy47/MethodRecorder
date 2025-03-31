package apr.methodrecorder;

import java.lang.instrument.Instrumentation;

public class MethodTraceAgent {
    private static String[] includes;
    private static String outputFile;

    public static void premain(String agentArgs, Instrumentation inst) {
        if (agentArgs == null || agentArgs.equals("")) {
            return;
        }
        for (String entry : agentArgs.split(",")) {
            if (entry.startsWith("includes=")) {
                includes = entry.split("=")[1].split(":");
            }
            if (entry.startsWith("output=")) {
                outputFile = entry.split("=")[1];
            }
        }
        if (outputFile == null) {
            outputFile = "method_trace.log";
        }
        if (includes == null) {
            return;
        }

        inst.addTransformer(new MethodTraceTransformer(includes, outputFile));
    }
}