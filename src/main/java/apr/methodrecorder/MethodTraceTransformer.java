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
        if (className == null || !shouldInclude(className)) {
            return classfileBuffer;
        }
        ClassPool cp = ClassPool.getDefault();
        CtClass ctClass = null;
        try {
            ctClass = cp.get(className.replace('/', '.'));
            if (ctClass.isFrozen()) {
                if (ctClass.isPrimitive() || className.startsWith("java.")) {
                    return classfileBuffer;
                }
            	ctClass.defrost();
            }

            if (ctClass.isInterface() || ctClass.isAnnotation()) {
                return classfileBuffer;
            }

            for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
            	if (Modifier.isAbstract(ctMethod.getModifiers())) {
            		continue;
            	}
                instrumentMethod(ctClass, ctMethod);
            }

            for (CtConstructor ctConstructor : ctClass.getDeclaredConstructors()) {
                instrumentMethod(ctClass, ctConstructor);
            }

            return ctClass.toBytecode();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ctClass != null) {
                ctClass.detach();
            }
        }
        return classfileBuffer;
    }

    private void instrumentMethod(CtClass ctClass, CtBehavior ctBehavior) throws Exception {
        MethodInfo methodInfo = ctBehavior.getMethodInfo();
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
        String methodName = ctBehavior.getName();

        // Insert start log
        String startLog = String.format(
                "%s.logStart(\"%s\", \"%s\", %d, %d);",
                LOGGER_CLASS, className, methodName, startLine, endLine
        );
        ctBehavior.insertBefore(startLog);

        // Insert end log
        String endLog = String.format(
                "%s.logEnd(\"%s\", \"%s\", %d, %d);",
                LOGGER_CLASS, className, methodName, startLine, endLine
        );
        ctBehavior.insertAfter(endLog, true);
    }

    private boolean shouldInclude(String className) {
        for (String include : this.includes) {
            if (matches(className, include)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String classPath, String include) {
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