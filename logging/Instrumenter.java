import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Instrumenter {

    private static final Set<String> ALLOWED_TYPES = Set.of(".java");
    private static final Set<String> NON_METHOD_BLOCKS = Set.of(
            "if","for","while","try","catch","finally",
            "do","else","synchronized","static","instanceof","new"
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
        String n = path.getFileName().toString().toLowerCase();
        for (String ext : ALLOWED_TYPES) if (n.endsWith(ext)) return true;
        return false;
    }

    /** Detects lines that must never be instrumented */
    private static Set<Integer> prescanSkipLines(List<String> lines) {
        Set<Integer> skip = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).trim();

            // always skip obvious structures or decls
            if (l.contains("log") || l.contains("throws") || l.contains("throw") ||
                l.contains("for(") || l.contains("while(") ||
                l.matches(".*\\b(if|else if)\\s*\\(.*\\).*") ||
                l.matches(".*\\b[a-zA-Z_][a-zA-Z0-9_<>\\[\\]]*\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*=.*;.*")) {
                skip.add(i);
            }
        }
        return skip;
    }

    private static void processFile(Path file, Path root) {
        try {
            List<String> lines = Files.readAllLines(file);
            Set<Integer> skipLines = prescanSkipLines(lines);
            String src = String.join("\n", lines);

            String rel = root.relativize(file).toString();
            String className = rel.replace(FileSystems.getDefault().getSeparator(), ".").replaceAll("\\.java$", "");

            StringBuilder out = new StringBuilder();

            boolean skipNextBrace = false;
            boolean afterReturn = false;
            boolean afterBreak = false;
            boolean afterThrow = false;
            boolean inMethod = false;
            boolean inString = false;
            boolean insideClass = false;

            int braceDepth = 0;
            int printCounter = 0;
            String currentMethod = "block";
            StringBuilder word = new StringBuilder();
            int currentLine = 0;

            for (int i = 0; i < src.length(); i++) {
                char c = src.charAt(i);

                // when newline reached, increment line counter AFTER writing the char
                if (c == '\n') currentLine++;
                // if the entire line is to be skipped, just copy and continue
                if (skipLines.contains(currentLine)) {
                    out.append(c);
                    continue;
                }

                out.append(c);

                // handle string literals
                if (c == '"' && (i == 0 || src.charAt(i - 1) != '\\')) {
                    inString = !inString;
                }
                if (inString) continue;

                // collect identifiers
                if (Character.isJavaIdentifierPart(c)) {
                    word.append(c);
                } else {
                    String token = word.toString();
                    word.setLength(0);

                    if (token.equals("class") || token.equals("interface") ||
                        token.equals("enum") || token.equals("record") ||
                        token.equals("switch")) {
                        skipNextBrace = true;
                    } else if (token.equals("return")) {
                        afterReturn = true;
                    } else if (token.equals("break")) {
                        afterBreak = true;
                    } else if (token.equals("throw")) {
                        afterThrow = true;
                    }
                }

                // inside-class instrumentation only
                if (!insideClass) {
                    if (c == '{' && skipNextBrace) {
                        skipNextBrace = false;
                        insideClass = true;
                        braceDepth++;
                    }
                    continue;
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
                        }
                        braceDepth++;
                        printCounter++;
                        out.append(" henry.tool.print(\"")
                           .append(className).append(" ")
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
                    if (insideClass && inMethod &&
                        !afterReturn && !afterBreak && !afterThrow) {
                        printCounter++;
                        out.append(" henry.tool.print(\"")
                           .append(className).append(" ")
                           .append(className).append("_").append(currentMethod)
                           .append(" ; trace #").append(printCounter).append("\");");
                    }
                    afterReturn = false;
                    afterBreak = false;
                    afterThrow = false;
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
