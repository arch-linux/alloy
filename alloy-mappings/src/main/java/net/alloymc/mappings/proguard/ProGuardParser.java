package net.alloymc.mappings.proguard;

import net.alloymc.mappings.model.ClassMapping;
import net.alloymc.mappings.model.FieldMapping;
import net.alloymc.mappings.model.MethodMapping;
import net.alloymc.mappings.util.DescriptorUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses Mojang's official ProGuard-format mapping files.
 *
 * <p>Mojang's mappings map FROM deobfuscated (human-readable) names TO obfuscated names.
 * This parser preserves both sides so the remapper can go in the reverse direction
 * (obfuscated → deobfuscated).
 *
 * <p>Format:
 * <pre>
 *   # comment
 *   original.ClassName -> obfuscatedName:
 *       type originalFieldName -> obfuscatedFieldName
 *       lineStart:lineEnd:returnType originalMethodName(paramType1,paramType2) -> obfuscatedMethodName
 * </pre>
 */
public final class ProGuardParser {

    private ProGuardParser() {}

    /**
     * Parses a ProGuard mapping file and returns all class mappings.
     * All class names in the returned mappings use JVM internal format (slashes, not dots).
     * All type descriptors use JVM descriptor format.
     */
    public static List<ClassMapping> parse(Path mappingsFile) throws IOException {
        List<String> lines = Files.readAllLines(mappingsFile);
        List<ClassMapping> result = new ArrayList<>();

        String currentDeobfClass = null;
        String currentObfClass = null;
        List<FieldMapping> currentFields = new ArrayList<>();
        List<MethodMapping> currentMethods = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }

            if (!line.startsWith(" ")) {
                // Class line — flush the previous class if one exists
                if (currentDeobfClass != null) {
                    result.add(new ClassMapping(
                            currentDeobfClass, currentObfClass,
                            Collections.unmodifiableList(currentFields),
                            Collections.unmodifiableList(currentMethods)
                    ));
                }

                // Parse: "original.ClassName -> obfuscated:"
                int arrowIdx = line.indexOf(" -> ");
                if (arrowIdx == -1) {
                    throw new IOException("Malformed class line (no ' -> ' found): " + line);
                }

                String deobf = line.substring(0, arrowIdx);
                String obf = line.substring(arrowIdx + 4, line.length() - 1); // strip trailing ':'

                currentDeobfClass = DescriptorUtil.toInternalName(deobf);
                currentObfClass = DescriptorUtil.toInternalName(obf);
                currentFields = new ArrayList<>();
                currentMethods = new ArrayList<>();
            } else {
                // Member line — field or method
                if (currentDeobfClass == null) {
                    throw new IOException("Member line before any class declaration: " + line);
                }

                String trimmed = line.trim();
                int arrowIdx = trimmed.indexOf(" -> ");
                if (arrowIdx == -1) {
                    throw new IOException("Malformed member line (no ' -> ' found): " + trimmed);
                }

                String obfName = trimmed.substring(arrowIdx + 4);
                String leftSide = trimmed.substring(0, arrowIdx);

                // Strip line number prefix if present: "123:456:returnType ..."
                leftSide = stripLineNumbers(leftSide);

                if (leftSide.contains("(")) {
                    MethodMapping method = parseMethod(leftSide, obfName);
                    if (method != null) {
                        currentMethods.add(method);
                    }
                } else {
                    FieldMapping field = parseField(leftSide, obfName);
                    if (field != null) {
                        currentFields.add(field);
                    }
                }
            }
        }

        // Flush the last class
        if (currentDeobfClass != null) {
            result.add(new ClassMapping(
                    currentDeobfClass, currentObfClass,
                    Collections.unmodifiableList(currentFields),
                    Collections.unmodifiableList(currentMethods)
            ));
        }

        return result;
    }

    /**
     * Strips ProGuard line number prefixes.
     * "123:456:void foo()" → "void foo()"
     * "void foo()" → "void foo()" (no-op if no prefix)
     */
    private static String stripLineNumbers(String s) {
        int firstColon = s.indexOf(':');
        if (firstColon == -1) {
            return s;
        }
        // Check if the part before the first colon is numeric
        String beforeColon = s.substring(0, firstColon);
        if (!isNumeric(beforeColon)) {
            return s;
        }
        // Find the second colon
        int secondColon = s.indexOf(':', firstColon + 1);
        if (secondColon == -1) {
            return s;
        }
        return s.substring(secondColon + 1);
    }

    /**
     * Parses a method entry: "returnType methodName(paramType1,paramType2)"
     * Returns null for entries we should skip (e.g., inlined methods from other classes).
     */
    private static MethodMapping parseMethod(String left, String obfName) {
        // "returnType methodName(paramType1,paramType2)"
        int parenOpen = left.indexOf('(');
        int parenClose = left.indexOf(')');

        // Split "returnType methodName" at the last space before the paren
        String beforeParen = left.substring(0, parenOpen);
        int lastSpace = beforeParen.lastIndexOf(' ');
        if (lastSpace == -1) {
            return null; // malformed
        }

        String returnType = beforeParen.substring(0, lastSpace);
        String methodName = beforeParen.substring(lastSpace + 1);

        // Skip if method name contains a dot — that's an inlined method from another class
        if (methodName.contains(".")) {
            return null;
        }

        // Parse parameter types
        String paramStr = left.substring(parenOpen + 1, parenClose);
        List<String> paramTypes = new ArrayList<>();
        if (!paramStr.isEmpty()) {
            for (String param : paramStr.split(",")) {
                paramTypes.add(param.trim());
            }
        }

        String descriptor = DescriptorUtil.toMethodDescriptor(returnType, paramTypes);
        return new MethodMapping(methodName, obfName, descriptor);
    }

    /**
     * Parses a field entry: "type fieldName"
     */
    private static FieldMapping parseField(String left, String obfName) {
        // "type fieldName"
        int lastSpace = left.lastIndexOf(' ');
        if (lastSpace == -1) {
            return null; // malformed
        }

        String type = left.substring(0, lastSpace);
        String fieldName = left.substring(lastSpace + 1);

        String descriptor = DescriptorUtil.toDescriptor(type);
        return new FieldMapping(fieldName, obfName, descriptor);
    }

    private static boolean isNumeric(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return !s.isEmpty();
    }
}
