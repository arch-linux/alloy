package net.alloymc.mappings.model;

/**
 * A single method mapping: maps an obfuscated method name to its original human-readable name.
 *
 * @param deobfuscatedName the original method name (e.g., "getHealth")
 * @param obfuscatedName   the obfuscated method name (e.g., "a")
 * @param descriptor       the method's full descriptor in deobfuscated namespace
 *                         (e.g., "(Lnet/minecraft/world/entity/Entity;)V")
 */
public record MethodMapping(
        String deobfuscatedName,
        String obfuscatedName,
        String descriptor
) {}
