package net.alloymc.mappings.remap;

import org.objectweb.asm.commons.Remapper;

/**
 * ASM Remapper backed by an Alloy MappingSet.
 *
 * <p>ASM calls these methods as it reads obfuscated class files. Each method returns
 * the deobfuscated name. If no mapping exists, the original name passes through unchanged.
 */
public final class AlloyRemapper extends Remapper {

    private final MappingSet mappings;

    public AlloyRemapper(MappingSet mappings) {
        this.mappings = mappings;
    }

    @Override
    public String map(String internalName) {
        return mappings.remapClass(internalName);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        return mappings.remapMethod(owner, name, descriptor);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        return mappings.remapField(owner, name, descriptor);
    }
}
