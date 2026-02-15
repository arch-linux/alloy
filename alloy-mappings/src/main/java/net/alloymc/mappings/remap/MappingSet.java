package net.alloymc.mappings.remap;

import net.alloymc.mappings.model.ClassMapping;
import net.alloymc.mappings.model.FieldMapping;
import net.alloymc.mappings.model.MethodMapping;
import net.alloymc.mappings.util.DescriptorUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The complete set of name mappings, indexed for fast lookup during remapping.
 *
 * <p>ASM's Remapper asks for mappings in the obfuscated namespace — it reads obfuscated
 * class files and needs to know what the deobfuscated names are. So all lookups here
 * are keyed by obfuscated coordinates.
 *
 * <p>Descriptors from the ProGuard file use deobfuscated class names. During construction,
 * this class converts them to obfuscated-namespace descriptors so they match what ASM provides.
 */
public final class MappingSet {

    private record MemberKey(String owner, String name, String descriptor) {}

    private final Map<String, String> classMap;      // obfuscated → deobfuscated
    private final Map<MemberKey, String> methodMap;   // (obf owner, obf name, obf desc) → deobf name
    private final Map<MemberKey, String> fieldMap;    // (obf owner, obf name, obf desc) → deobf name
    private final int classCount;
    private final int methodCount;
    private final int fieldCount;

    private MappingSet(Map<String, String> classMap,
                       Map<MemberKey, String> methodMap,
                       Map<MemberKey, String> fieldMap) {
        this.classMap = classMap;
        this.methodMap = methodMap;
        this.fieldMap = fieldMap;
        this.classCount = classMap.size();
        this.methodCount = methodMap.size();
        this.fieldCount = fieldMap.size();
    }

    /**
     * Builds a MappingSet from parsed class mappings.
     *
     * <p>This is a two-phase process:
     * <ol>
     *   <li>Build the class name mapping table (deobf ↔ obf)</li>
     *   <li>Convert all member descriptors from deobfuscated namespace to obfuscated namespace,
     *       so lookups during remapping work correctly</li>
     * </ol>
     */
    public static MappingSet build(List<ClassMapping> classes) {
        // Phase 1: class name maps
        Map<String, String> obfToDeobf = new HashMap<>();
        Map<String, String> deobfToObf = new HashMap<>();

        for (ClassMapping cm : classes) {
            obfToDeobf.put(cm.obfuscatedName(), cm.deobfuscatedName());
            deobfToObf.put(cm.deobfuscatedName(), cm.obfuscatedName());
        }

        // Phase 2: member maps with descriptors converted to obfuscated namespace
        Map<MemberKey, String> methodMap = new HashMap<>();
        Map<MemberKey, String> fieldMap = new HashMap<>();

        for (ClassMapping cm : classes) {
            for (MethodMapping mm : cm.methods()) {
                // Convert descriptor from deobf class names to obf class names
                String obfDescriptor = DescriptorUtil.remapDescriptor(mm.descriptor(), deobfToObf);
                MemberKey key = new MemberKey(cm.obfuscatedName(), mm.obfuscatedName(), obfDescriptor);
                methodMap.put(key, mm.deobfuscatedName());
            }

            for (FieldMapping fm : cm.fields()) {
                String obfDescriptor = DescriptorUtil.remapDescriptor(fm.descriptor(), deobfToObf);
                MemberKey key = new MemberKey(cm.obfuscatedName(), fm.obfuscatedName(), obfDescriptor);
                fieldMap.put(key, fm.deobfuscatedName());
            }
        }

        return new MappingSet(obfToDeobf, methodMap, fieldMap);
    }

    /** Returns the deobfuscated class name, or the input unchanged if no mapping exists. */
    public String remapClass(String obfuscatedInternalName) {
        return classMap.getOrDefault(obfuscatedInternalName, obfuscatedInternalName);
    }

    /** Returns the deobfuscated method name, or the input unchanged if no mapping exists. */
    public String remapMethod(String owner, String name, String descriptor) {
        MemberKey key = new MemberKey(owner, name, descriptor);
        return methodMap.getOrDefault(key, name);
    }

    /** Returns the deobfuscated field name, or the input unchanged if no mapping exists. */
    public String remapField(String owner, String name, String descriptor) {
        MemberKey key = new MemberKey(owner, name, descriptor);
        return fieldMap.getOrDefault(key, name);
    }

    public int getClassCount() { return classCount; }
    public int getMethodCount() { return methodCount; }
    public int getFieldCount() { return fieldCount; }
}
