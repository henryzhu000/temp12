package test.test;

import java.io.*;
import java.util.*;

public class TraceAnalyzer {
    public static void main(String[] args) throws Exception {
        File logFile = new File("trace.log");
        if (!logFile.exists()) {
            System.err.println("❌ trace.log not found. Run AttachTracer first.");
            return;
        }

        Map<String, Integer> classMap = new HashMap<>();
        Map<String, Integer> lineMap = new HashMap<>();
        Map<String, Double> timeMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.contains("\"class\"")) continue; // skip connect/disconnect events
                String cls = extract(line, "\"class\":\"", "\"");
                int ln = Integer.parseInt(extract(line, "\"line\":", ",", "}"));
                double time = Double.parseDouble(extract(line, "\"time\":", "}"));

                classMap.merge(cls, 1, Integer::sum);
                String key = cls + ":" + ln;
                lineMap.merge(key, 1, Integer::sum);
                timeMap.merge(key, time, Double::sum);
            }
        }

        System.out.println("\n📂 Classes:");
        classMap.forEach((cls, count) ->
                System.out.printf("  %s — %d hits%n", cls, count));

        System.out.println("\n📄 Lines:");
        lineMap.forEach((key, count) ->
                System.out.printf("  %s — %d times%n", key, count));

        System.out.println("\n⏱ Timings (ms total):");
        timeMap.forEach((key, time) ->
                System.out.printf("  %s — %.3f ms%n", key, time));
    }

    // crude JSON substring extractor for fixed format
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
