package net.alloymc.mappings.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Remaps an entire JAR file: reads every .class file, applies name mappings via ASM,
 * and writes a new JAR with deobfuscated class names, method names, and field names.
 *
 * <p>Non-class files (resources, META-INF, etc.) are copied unchanged.
 */
public final class JarRemapper {

    private JarRemapper() {}

    /**
     * Remaps all classes in the input JAR and writes the result to the output path.
     *
     * @param inputJar  the obfuscated Minecraft client JAR
     * @param outputJar the path to write the deobfuscated JAR
     * @param mappings  the mapping set to apply
     */
    public static void remap(Path inputJar, Path outputJar, MappingSet mappings) throws IOException {
        AlloyRemapper remapper = new AlloyRemapper(mappings);
        int classesRemapped = 0;
        int resourcesCopied = 0;

        try (JarFile jarIn = new JarFile(inputJar.toFile());
             OutputStream fileOut = Files.newOutputStream(outputJar);
             JarOutputStream jarOut = new JarOutputStream(fileOut)) {

            Enumeration<JarEntry> entries = jarIn.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                // Skip directory entries
                if (entry.isDirectory()) {
                    continue;
                }

                try (InputStream is = jarIn.getInputStream(entry)) {
                    if (entry.getName().endsWith(".class")) {
                        // Remap the class file
                        byte[] remapped = remapClass(is, remapper);

                        // The output path must match the remapped class name
                        String originalClassName = entry.getName()
                                .substring(0, entry.getName().length() - 6); // strip ".class"
                        String newClassName = remapper.map(originalClassName);
                        String newEntryName = newClassName + ".class";

                        jarOut.putNextEntry(new JarEntry(newEntryName));
                        jarOut.write(remapped);
                        jarOut.closeEntry();
                        classesRemapped++;
                    } else {
                        // Copy non-class files as-is
                        jarOut.putNextEntry(new JarEntry(entry.getName()));
                        is.transferTo(jarOut);
                        jarOut.closeEntry();
                        resourcesCopied++;
                    }
                }
            }
        }

        System.out.println("[Alloy] Remapped " + classesRemapped + " classes, "
                + resourcesCopied + " resources copied");
    }

    private static byte[] remapClass(InputStream classData, AlloyRemapper remapper)
            throws IOException {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = new ClassRemapper(writer, remapper);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }
}
