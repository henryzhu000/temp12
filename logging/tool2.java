

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class tool2 {

    private static final Path LOG_PATH = Paths.get("D:\\eclipseWorkSpace\\test\\5\\trace.log");
    private static final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * Message format expected:
     *   "<FullyQualifiedClassName> <ClassName>_<methodName> { trace"  OR  "; trace"
     *
     * Counter is maintained per *class* (first token).
     */
    public static synchronized void print(String msg) {
        try {
            // First token is the class key; rest is message (e.g., Class_method { trace)
            String[] parts = msg.split(" ", 2);
            String classKey = parts.length > 0 ? parts[0] : "UnknownClass";
            String rest = parts.length > 1 ? parts[1] : msg;

            long threadId = Thread.currentThread().getId();
            int count = counters.computeIfAbsent(classKey, k -> new AtomicInteger(0)).incrementAndGet();

            String line = String.format(
                "%s [T%s] #%d %s %s%s",
                LocalDateTime.now(),
                threadId,
                count,
                classKey,
                rest,
                System.lineSeparator()
            );

            Files.createDirectories(LOG_PATH.getParent());
            Files.writeString(LOG_PATH, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
