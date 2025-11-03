import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Instrumenter {

    private static final Set<String> ALLOWED_TYPES = Set.of(".java");

    private static final Set<String> NON_METHOD_BLOCKS = Set.of(
            "if","for","while","switch","try","catch","finally","do","else","synchronized","static","instanceof","new"
    );

    public static void main(String[] args) throws IOException {
        Path root = Path.of("D:\\eclipseWorkSpace\\test\\5\\greenMailEmailMock\\greenMail\\greenMail - Copy\\src\\main\\java");

        if (!Files.exists(root)) {
            System.err.println("Path not found: " + root);
            return;
        }

        Files.walk(root)
            .filter(Files::isRegularFile)
            .filter(Instrumenter::isAllowedType)
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

            String relPath = root.relativize(file).toString();
            String className = relPath.replace(FileSystems.getDefault().getSeparator(), ".").replaceAll("\\.java$", "");

            boolean skipNextBrace = false;
            boolean afterReturn = false;
            boolean inMethod = false;
            int braceDepth = 0;
            String currentMethod = "block";
            StringBuilder word = new StringBuilder();

            int printCounter = 0; // <-- NEW: per-class counter

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
                        inMethod = false;
                    } else {
                        String detected = detectMethodName(src, i);
                        if (detected != null && !NON_METHOD_BLOCKS.contains(detected)) {
                            currentMethod = detected;
                            inMethod = true;
                        } else if (!inMethod) {
                            currentMethod = (detected != null && !detected.isBlank()) ? detected : "block";
                        }
                        braceDepth++;
                        printCounter++;
                        out.append(" henry.tool.print(\"")
                           .append(className)
                           .append(" ")
                           .append(className).append("_").append(currentMethod)
                           .append(" { trace #").append(printCounter).append("\");");
                    }
                } else if (c == '}') {
                    if (braceDepth > 0) braceDepth--;
                    if (braceDepth <= 1) {
                        inMethod = false;
                        currentMethod = "block";
                    }
                } else if (c == ';') {
                    if (inMethod && !afterReturn) {
                        printCounter++;
                        out.append(" henry.tool.print(\"")
                           .append(className)
                           .append(" ")
                           .append(className).append("_").append(currentMethod)
                           .append(" ; trace #").append(printCounter).append("\");");
                    }
                    afterReturn = false;
                }
            }

            Files.writeString(file, out.toString());
            System.out.println("Instrumented: " + className + " (" + printCounter + " inserts)");

        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }

    private static String detectMethodName(String src, int openIdx) {
        int i = openIdx - 1;
        while (i >= 0 && Character.isWhitespace(src.charAt(i))) i--;
        int paren = -1;
        for (; i >= 0; i--) {
            char ch = src.charAt(i);
            if (ch == '(') { paren = i; break; }
            if (ch == ';' || ch == '{' || ch == '}') break;
        }
        if (paren < 0) return null;
        i = paren - 1;
        while (i >= 0 && Character.isWhitespace(src.charAt(i))) i--;
        int end = i;
        while (i >= 0 && Character.isJavaIdentifierPart(src.charAt(i))) i--;
        int start = i + 1;
        if (start <= end) {
            String name = src.substring(start, end + 1);
            if (!name.isEmpty()) return name;
        }
        return null;
    }
}
