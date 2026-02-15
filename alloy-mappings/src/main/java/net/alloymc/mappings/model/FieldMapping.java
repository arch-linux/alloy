package net.alloymc.mappings.model;

/**
 * A single field mapping: maps an obfuscated field name to its original human-readable name.
 *
 * @param deobfuscatedName the original field name (e.g., "health")
 * @param obfuscatedName   the obfuscated field name (e.g., "a")
 * @param descriptor       the field's type descriptor in deobfuscated namespace
 *                         (e.g., "I" for int, "Lnet/minecraft/world/level/Level;" for a class)
 */
public record FieldMapping(
        String deobfuscatedName,
        String obfuscatedName,
        String descriptor
) {}
