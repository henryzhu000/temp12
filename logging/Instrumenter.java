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

    /** Prescan for lines that should never be instrumented */
    private static Set<Integer> prescanSkipLines(String src) {
        Set<Integer> skip = new HashSet<>();
        String[] lines = src.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim();

            // existing skip rules
            if (l.contains("log") || l.contains("throws")) {
                skip.add(i);
                continue;
            }

            // skip control loops
            if (l.contains("for(") || l.contains("while(")) {
                skip.add(i);
                continue;
            }

            // skip variable declarations
            // heuristic: contains '=', not comparison, not 'return', not '=='
            if (l.contains("=") && 
                !l.contains("==") && !l.contains(">=") && !l.contains("<=") &&
                !l.startsWith("return") && !l.startsWith("if") && !l.startsWith("while") &&
                !l.startsWith("for") && !l.startsWith("@") && !l.contains("->")) {
                skip.add(i);
            }
        }
        return skip;
    }

    private static void processFile(Path file, Path root) {
        try {
            String src = Files.readString(file);
            Set<Integer> skipLines = prescanSkipLines(src);

            String relPath = root.relativize(file).toString();
            String className = relPath.replace(FileSystems.getDefault().getSeparator(), ".").replaceAll("\\.java$", "");

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
                out.append(c);
                if (c == '\n') currentLine++;

                // skip prescanned lines entirely
                if (skipLines.contains(currentLine)) continue;

                // handle string literals
                if (c == '"' && (i == 0 || src.charAt(i - 1) != '\\')) {
                    inString = !inString;
                }
                if (inString) continue;

                // collect words
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

                if (c == '{') {
                    if (skipNextBrace) {
                        skipNextBrace = false;
                        braceDepth++;
                        if (!insideClass) {
                            insideClass = true;
                            continue; // skip injection for class brace
                        }
                        inMethod = false;
                    } else if (insideClass) {
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
