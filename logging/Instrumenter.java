import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Instrumenter {

    private static final Set<String> ALLOWED_TYPES = Set.of(".java");

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
        for (String ext : ALLOWED_TYPES) if (name.endsWith(ext)) return true;
        return false;
    }

    /** Prescan for lines that should be skipped entirely */
    private static Set<Integer> prescanSkipLines(List<String> lines) {
        Set<Integer> skip = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).trim();

            // universal line skips
            if (l.contains("log") || l.contains("throws") || l.contains("throw") ||
                l.contains("for(") || l.contains("while(") ||
                l.contains("[") || l.contains("]") ||
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

                // count line before doing anything
                if (c == '\n') currentLine++;

                // completely skip forbidden lines
                if (skipLines.contains(currentLine)) {
                    out.append(c);
                    continue;
                }

                out.append(c);

                // string literal toggle
                if (c == '"' && (i == 0 || src.charAt(i - 1) != '\\')) {
                    inString = !inString;
                }
                if (inString) continue;

                // collect tokens
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

                // don't instrument before first class
                if (!insideClass) {
                    if (c == '{' && skipNextBrace) {
                        skipNextBrace = false;
                        insideClass = true;
                        braceDepth++;
                    }
                    continue;
                }

                // removed all '{ trace' injections per request
                if (c == '{') {
                    braceDepth++;
                    continue;
                }

                if (c == '}') {
                    if (braceDepth > 0) braceDepth--;
                    if (braceDepth <= 1) {
                        inMethod = false;
                        currentMethod = "block";
                    }
                } else if (c == ';') {
                    // only trace semicolons (no return/break/throw)
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
