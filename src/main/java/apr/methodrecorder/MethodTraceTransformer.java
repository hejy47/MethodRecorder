package apr.methodrecorder;

import javassist.*;
import javassist.bytecode.*;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;

public class MethodTraceTransformer implements ClassFileTransformer {
    private static final String LOGGER_CLASS = MethodTraceLogger.class.getName();
    private Set<String> includes = new HashSet<>();

    public MethodTraceTransformer(String[] includes, String outputFile) {
        for (String include : includes) {
            this.includes.add(include);
        }
        MethodTraceLogger.init(outputFile);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        try {
            if (className == null || !shouldInclude(className)) {
                return classfileBuffer;
            }
            ClassPool cp = ClassPool.getDefault();
            CtClass ctClass = cp.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

            for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
                instrumentMethod(ctClass, ctMethod);
            }

            return ctClass.toBytecode();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classfileBuffer;
    }

    private void instrumentMethod(CtClass ctClass, CtMethod ctMethod) throws Exception {
        MethodInfo methodInfo = ctMethod.getMethodInfo();
        CodeAttribute codeAttr = methodInfo.getCodeAttribute();
        int startLine = -1;
        int endLine = -1;

        if (codeAttr != null) {
            LineNumberAttribute lineAttr = (LineNumberAttribute) codeAttr.getAttribute(LineNumberAttribute.tag);
            if (lineAttr != null && lineAttr.tableLength() > 0) {
                startLine = lineAttr.lineNumber(0);
                endLine = startLine;
                for (int i = 0; i < lineAttr.tableLength(); i++) {
                    int current = lineAttr.lineNumber(i);
                    startLine = Math.min(startLine, current);
                    endLine = Math.max(endLine, current);
                }
                startLine -= 1;
                endLine += 1;
            }
        }

        String className = ctClass.getName().replace('/', '.');
        String methodName = ctMethod.getName();

        // Insert start log
        String startLog = String.format(
                "%s.logStart(\"%s\", \"%s\", %d, %d);",
                LOGGER_CLASS, className, methodName, startLine, endLine
        );
        ctMethod.insertBefore(startLog);

        // Insert end log
        String endLog = String.format(
                "%s.logEnd(\"%s\", \"%s\", %d, %d);",
                LOGGER_CLASS, className, methodName, startLine, endLine
        );
        ctMethod.insertAfter(endLog, true);
    }

    private boolean shouldInclude(String className) {
        for (String include : this.includes) {
            if (matches(className, include)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String className, String include) {
        String classPath = className.replace('.', '/');

        if (include.endsWith("$*")) {
            String prefix = include.substring(0, include.length() - 2).replace('.', '/');
            return classPath.startsWith(prefix);
        } else if (include.endsWith("*")) {
            String prefix = include.substring(0, include.length() - 1).replace('.', '/');
            return classPath.startsWith(prefix);
        } else {
            return classPath.equals(include.replace('.', '/'));
        }
    }
}