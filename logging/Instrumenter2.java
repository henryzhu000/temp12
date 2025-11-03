import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Instrumenter2 {

    private static final Set<String> ALLOWED_TYPES = Set.of(".java");

    public static void main(String[] args) throws IOException {
        Path root = Path.of("D:\\eclipseWorkSpace\\test\\5\\greenMailEmailMock\\greenMail\\greenMail - Copy\\src\\main\\java");

        if (!Files.exists(root)) {
            System.err.println("Path not found: " + root);
            return;
        }

        Files.walk(root)
            .filter(Files::isRegularFile)
            .filter(Instrumenter2::isAllowedType)
            .forEach(file -> processFile(file, root));

        System.out.println("Instrumentation complete.");
    }

    private static boolean isAllowedType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        for (String ext : ALLOWED_TYPES) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    private static void processFile(Path file, Path root) {
        try {
            String src = Files.readString(file);
            StringBuilder out = new StringBuilder();

            // derive fully qualified class name from path
            String relPath = root.relativize(file).toString();
            String className = relPath
                    .replace(FileSystems.getDefault().getSeparator(), ".")
                    .replaceAll("\\.java$", "");

            boolean skipNextBrace = false;
            boolean afterReturn = false;
            boolean inMethod = false;
            int braceDepth = 0;
            StringBuilder word = new StringBuilder();

            for (int i = 0; i < src.length(); i++) {
                char c = src.charAt(i);
                out.append(c);

                if (Character.isJavaIdentifierPart(c)) {
                    word.append(c);
                } else {
                    String token = word.toString();
                    if (token.equals("class") || token.equals("interface") || token.equals("enum") || token.equals("record")) {
                        skipNextBrace = true;
                    } else if (token.equals("return")) {
                        afterReturn = true;
                    }
                    word.setLength(0);
                }

                if (c == '{') {
                    if (skipNextBrace) {
                        skipNextBrace = false;
                        braceDepth++;
                    } else {
                        braceDepth++;
                        inMethod = true;
                        out.append(" henry.tool.print(\"" + className + " { trace\");");
                    }
                } else if (c == '}') {
                    if (braceDepth > 0) braceDepth--;
                    if (braceDepth <= 1) inMethod = false;
                } else if (c == ';') {
                    if (inMethod && !afterReturn) {
                        out.append(" henry.tool.print(\"" + className + " ; trace\");");
                    }
                    afterReturn = false;
                }
            }

            Files.writeString(file, out.toString());
            System.out.println("Instrumented: " + className);

        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
}
