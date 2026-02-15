package net.alloymc.mappings.util;

import java.util.List;
import java.util.Map;

/**
 * Converts between Java source type names (as found in ProGuard mappings)
 * and JVM type descriptors (as used by ASM and the class file format).
 */
public final class DescriptorUtil {

    private DescriptorUtil() {}

    /**
     * Converts a Java source type name to a JVM type descriptor.
     * <pre>
     *   "int"                                → "I"
     *   "void"                               → "V"
     *   "net.minecraft.world.level.Level"    → "Lnet/minecraft/world/level/Level;"
     *   "int[]"                              → "[I"
     *   "net.minecraft.Thing[][]"            → "[[Lnet/minecraft/Thing;"
     * </pre>
     */
    public static String toDescriptor(String sourceType) {
        if (sourceType.endsWith("[]")) {
            return "[" + toDescriptor(sourceType.substring(0, sourceType.length() - 2));
        }
        return switch (sourceType) {
            case "void"    -> "V";
            case "boolean" -> "Z";
            case "byte"    -> "B";
            case "char"    -> "C";
            case "short"   -> "S";
            case "int"     -> "I";
            case "long"    -> "J";
            case "float"   -> "F";
            case "double"  -> "D";
            default        -> "L" + sourceType.replace('.', '/') + ";";
        };
    }

    /**
     * Builds a JVM method descriptor from return type and parameter types (all in source format).
     * <pre>
     *   ("void", ["int", "net.minecraft.Entity"]) → "(ILnet/minecraft/Entity;)V"
     * </pre>
     */
    public static String toMethodDescriptor(String returnType, List<String> paramTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (String param : paramTypes) {
            sb.append(toDescriptor(param));
        }
        sb.append(")");
        sb.append(toDescriptor(returnType));
        return sb.toString();
    }

    /**
     * Converts a source name (dots) to JVM internal format (slashes).
     * "net.minecraft.world.level.Level" → "net/minecraft/world/level/Level"
     */
    public static String toInternalName(String sourceName) {
        return sourceName.replace('.', '/');
    }

    /**
     * Remaps all class references within a JVM descriptor from one namespace to another.
     * Non-class references (primitives, array markers, parens) are passed through unchanged.
     * Class names not found in the mapping are also passed through unchanged.
     *
     * <pre>
     *   "(Lnet/minecraft/Foo;I)Lnet/minecraft/Bar;" with {Foo→a, Bar→b}
     *   → "(La;I)Lb;"
     * </pre>
     */
    public static String remapDescriptor(String descriptor, Map<String, String> classMapping) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int semicolon = descriptor.indexOf(';', i);
                String className = descriptor.substring(i + 1, semicolon);
                String remapped = classMapping.getOrDefault(className, className);
                result.append('L').append(remapped).append(';');
                i = semicolon + 1;
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }
}
