package test2;

import java.io.*;
import java.util.*;
import java.util.stream.*;

public class TraceAnalyzer {

    private final Map<String, Integer> classMap = new HashMap<>();
    private final Map<String, Integer> lineMap = new HashMap<>();
    private final Map<String, Double> timeMap = new HashMap<>();

    public static void main(String[] args) throws Exception {
        TraceAnalyzer analyzer = new TraceAnalyzer();
        analyzer.load("trace.log");

        analyzer.printClassMap();
      //  analyzer.printLineMap();
       // analyzer.printTimeMap();
    }

    /** Loads trace.log and populates maps */
    public void load(String path) throws Exception {
        File logFile = new File(path);
        if (!logFile.exists()) {
            System.err.println("❌ trace.log not found. Run AttachTracer first.");
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.contains("\"class\"")) continue;
                String cls = extract(line, "\"class\":\"", "\"");
                int ln = Integer.parseInt(extract(line, "\"line\":", ",", "}"));
                double time = Double.parseDouble(extract(line, "\"time\":", "}"));

                classMap.merge(cls, 1, Integer::sum);
                String key = cls + ":" + ln;
                lineMap.merge(key, 1, Integer::sum);
                timeMap.merge(key, time, Double::sum);
            }
        }
    }

    /** Prints class summary sorted alphabetically */
    public void printClassMap() {
        System.out.println("\n📂 Classes (sorted by name):");
        classMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.printf("  %-60s — %d hits%n", e.getKey(), e.getValue()));
    }

    /** Prints line summary sorted alphabetically */
    public void printLineMap() {
        System.out.println("\n📄 Lines (sorted by class:line):");
        lineMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.printf("  %-60s — %d times%n", e.getKey(), e.getValue()));
    }

    /** Prints time summary sorted alphabetically by key */
    public void printTimeMap() {
        System.out.println("\n⏱ Timings (sorted by class:line):");
        timeMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.printf("  %-60s — %.3f ms%n", e.getKey(), e.getValue()));
    }

    /** Helper for JSON substring extraction */
    private static String extract(String src, String start, String... ends) {
        int i = src.indexOf(start);
        if (i == -1) return "";
        i += start.length();
        int j = src.length();
        for (String end : ends) {
            int e = src.indexOf(end, i);
            if (e != -1 && e < j) j = e;
        }
        return src.substring(i, j);
    }
}
