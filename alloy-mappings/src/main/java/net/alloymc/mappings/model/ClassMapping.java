package net.alloymc.mappings.model;

import java.util.List;

/**
 * A complete class mapping: the class name plus all its field and method mappings.
 * All names use JVM internal format (slashes, not dots): "net/minecraft/world/level/Level"
 *
 * @param deobfuscatedName the original class name in internal format
 * @param obfuscatedName   the obfuscated class name in internal format
 * @param fields           all field mappings for this class
 * @param methods          all method mappings for this class
 */
public record ClassMapping(
        String deobfuscatedName,
        String obfuscatedName,
        List<FieldMapping> fields,
        List<MethodMapping> methods
) {}
