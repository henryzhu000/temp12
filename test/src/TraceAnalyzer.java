

import java.util.*;

public class TraceAnalyzer {
    private final Map<String, Integer> classMap = new HashMap<>();
    private final Map<String, Integer> lineMap = new HashMap<>();
    private final Map<String, Long> startTimes = new HashMap<>();
    private final Map<String, Long> durations = new HashMap<>();
    private long totalLoopTime = 0;

    /** Record a class name (with folder/package) */
    public void recordClass(String className) {
        classMap.merge(className, 1, Integer::sum);
    }

    /** Record a class + line number combination */
    public void recordLine(String className, int lineNumber) {
        String key = className + ":" + lineNumber;
        lineMap.merge(key, 1, Integer::sum);
    }

    /** Start timing a line/class event */
    public void startTimer(String key) {
        startTimes.put(key, System.nanoTime());
    }

    /** Stop timing a line/class event */
    public void stopTimer(String key) {
        Long start = startTimes.remove(key);
        if (start != null) {
            long duration = System.nanoTime() - start;
            durations.merge(key, duration, Long::sum);
        }
    }

    /** Add total time spent in loop iteration */
    public void addLoopTime(long nanos) {
        totalLoopTime += nanos;
    }

    /** Print everything */
    public void printResults() {
        System.out.println("\n📂 Classes (with package):");
        classMap.forEach((cls, count) ->
                System.out.printf("  %s  — %d hits%n", cls, count));

        System.out.println("\n📄 Lines (class:line):");
        lineMap.forEach((line, count) ->
                System.out.printf("  %s  — %d times%n", line, count));

        System.out.println("\n⏱ Execution durations:");
        durations.forEach((key, time) ->
                System.out.printf("  %s  — %.3f ms%n", key, time / 1_000_000.0));

        System.out.printf("\n⚙ Total event loop time: %.3f ms%n",
                totalLoopTime / 1_000_000.0);
    }
}
