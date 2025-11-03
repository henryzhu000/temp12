

import java.io.*;
import java.nio.file.*;
import java.time.*;

public class tool {
    private static final Path LOG_PATH = Paths.get("D:\\eclipseWorkSpace\\test\\5\\trace.log");

    public static synchronized void print(String msg) {
        try {
            Files.createDirectories(LOG_PATH.getParent());
            long threadId = Thread.currentThread().getId();
            String line = String.format("%s [T%s] %s%s",
                    LocalDateTime.now(), threadId, msg, System.lineSeparator());
            Files.writeString(LOG_PATH, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
