package net.alloymc.mappings.proguard;

import net.alloymc.mappings.model.ClassMapping;
import net.alloymc.mappings.model.FieldMapping;
import net.alloymc.mappings.model.MethodMapping;
import net.alloymc.mappings.remap.MappingSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProGuardParserTest {

    private static final String SAMPLE_MAPPINGS = """
            # This is a comment
            net.minecraft.world.level.Level -> abc:
                int tickCount -> a
                net.minecraft.world.entity.Entity getEntity -> b
                1:1:void <init>() -> <init>
                3:7:int getTickCount() -> a
                9:22:void setEntity(net.minecraft.world.entity.Entity) -> a
            net.minecraft.world.entity.Entity -> bcd:
                java.lang.String name -> a
                double posX -> b
                1:5:void tick() -> a
                7:12:java.lang.String getName() -> b
            """;

    @Test
    void parsesClassMappings(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test_mappings.txt");
        Files.writeString(file, SAMPLE_MAPPINGS);

        List<ClassMapping> classes = ProGuardParser.parse(file);

        assertEquals(2, classes.size());

        ClassMapping level = classes.get(0);
        assertEquals("net/minecraft/world/level/Level", level.deobfuscatedName());
        assertEquals("abc", level.obfuscatedName());

        ClassMapping entity = classes.get(1);
        assertEquals("net/minecraft/world/entity/Entity", entity.deobfuscatedName());
        assertEquals("bcd", entity.obfuscatedName());
    }

    @Test
    void parsesFieldMappings(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test_mappings.txt");
        Files.writeString(file, SAMPLE_MAPPINGS);

        List<ClassMapping> classes = ProGuardParser.parse(file);
        ClassMapping level = classes.get(0);

        assertEquals(2, level.fields().size());

        FieldMapping tickCount = level.fields().get(0);
        assertEquals("tickCount", tickCount.deobfuscatedName());
        assertEquals("a", tickCount.obfuscatedName());
        assertEquals("I", tickCount.descriptor()); // int → I

        FieldMapping getEntity = level.fields().get(1);
        assertEquals("getEntity", getEntity.deobfuscatedName());
        assertEquals("b", getEntity.obfuscatedName());
        assertEquals("Lnet/minecraft/world/entity/Entity;", getEntity.descriptor());
    }

    @Test
    void parsesMethodMappings(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test_mappings.txt");
        Files.writeString(file, SAMPLE_MAPPINGS);

        List<ClassMapping> classes = ProGuardParser.parse(file);
        ClassMapping level = classes.get(0);

        // <init>, getTickCount, setEntity = 3 methods
        assertEquals(3, level.methods().size());

        MethodMapping getTickCount = level.methods().get(1);
        assertEquals("getTickCount", getTickCount.deobfuscatedName());
        assertEquals("a", getTickCount.obfuscatedName());
        assertEquals("()I", getTickCount.descriptor());

        MethodMapping setEntity = level.methods().get(2);
        assertEquals("setEntity", setEntity.deobfuscatedName());
        assertEquals("a", setEntity.obfuscatedName());
        assertEquals("(Lnet/minecraft/world/entity/Entity;)V", setEntity.descriptor());
    }

    @Test
    void mappingSetLooksUpByObfuscatedName(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test_mappings.txt");
        Files.writeString(file, SAMPLE_MAPPINGS);

        List<ClassMapping> classes = ProGuardParser.parse(file);
        MappingSet mappings = MappingSet.build(classes);

        // Class lookup
        assertEquals("net/minecraft/world/level/Level", mappings.remapClass("abc"));
        assertEquals("net/minecraft/world/entity/Entity", mappings.remapClass("bcd"));
        assertEquals("some/unknown/Class", mappings.remapClass("some/unknown/Class"));

        // Method lookup (descriptors must be in obfuscated namespace)
        // setEntity: deobf desc = (Lnet/minecraft/world/entity/Entity;)V
        //            obf desc   = (Lbcd;)V  (because Entity → bcd)
        assertEquals("setEntity", mappings.remapMethod("abc", "a", "(Lbcd;)V"));

        // getTickCount: desc = ()I — no class refs, so obf desc = deobf desc
        assertEquals("getTickCount", mappings.remapMethod("abc", "a", "()I"));

        // Unknown method falls through
        assertEquals("x", mappings.remapMethod("abc", "x", "()V"));
    }

    @Test
    void mappingSetFieldLookup(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test_mappings.txt");
        Files.writeString(file, SAMPLE_MAPPINGS);

        List<ClassMapping> classes = ProGuardParser.parse(file);
        MappingSet mappings = MappingSet.build(classes);

        // tickCount: int field → descriptor "I" (no class refs to remap)
        assertEquals("tickCount", mappings.remapField("abc", "a", "I"));

        // getEntity field: type is Entity → obf descriptor = "Lbcd;"
        assertEquals("getEntity", mappings.remapField("abc", "b", "Lbcd;"));
    }

    @Test
    void handlesEmptyMappings(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "# empty mappings file\n");

        List<ClassMapping> classes = ProGuardParser.parse(file);
        assertEquals(0, classes.size());

        MappingSet mappings = MappingSet.build(classes);
        assertNotNull(mappings);
        assertEquals("unchanged", mappings.remapClass("unchanged"));
    }

    @Test
    void handlesArrayTypes(@TempDir Path tempDir) throws Exception {
        String mappings = """
                net.minecraft.Test -> xyz:
                    int[] values -> a
                    5:10:void process(int[],net.minecraft.Thing[]) -> b
                net.minecraft.Thing -> qrs:
                """;

        Path file = tempDir.resolve("array_mappings.txt");
        Files.writeString(file, mappings);

        List<ClassMapping> classes = ProGuardParser.parse(file);
        ClassMapping test = classes.get(0);

        FieldMapping values = test.fields().get(0);
        assertEquals("[I", values.descriptor());

        MethodMapping process = test.methods().get(0);
        assertEquals("([I[Lnet/minecraft/Thing;)V", process.descriptor());

        // Verify MappingSet converts the descriptor to obf namespace
        MappingSet ms = MappingSet.build(classes);
        // obf desc: ([I[Lqrs;)V — because Thing → qrs
        assertEquals("process", ms.remapMethod("xyz", "b", "([I[Lqrs;)V"));
    }
}
