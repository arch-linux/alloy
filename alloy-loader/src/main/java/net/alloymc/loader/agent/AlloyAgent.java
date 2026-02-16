package net.alloymc.loader.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

/**
 * Java agent that enables bytecode transformation of Minecraft classes.
 *
 * Loaded via {@code -javaagent:alloy-loader.jar} before Minecraft starts.
 * Registers a {@link AlloyTransformer} that intercepts class loading and
 * applies ASM modifications to inject Alloy branding and hooks.
 *
 * <p>The agent also adds itself to the bootstrap classloader so that
 * ASM-injected calls to Alloy classes (e.g. {@link EventFiringHook}) are
 * resolvable from any classloader, including MC's bundler classloader
 * which does not delegate to the system classloader.
 */
public final class AlloyAgent {

    private static Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;

        // Make agent classes visible to ALL classloaders.
        // MC's bundler creates its own URLClassLoader that doesn't delegate
        // to the system classloader. Without this, ASM-injected calls to
        // EventFiringHook would throw ClassNotFoundException at runtime.
        try {
            File agentJar = new File(
                    AlloyAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            inst.appendToBootstrapClassLoaderSearch(new JarFile(agentJar));
        } catch (Exception e) {
            System.err.println("[Alloy] Warning: Could not add agent to bootstrap classloader: "
                    + e.getMessage());
            System.err.println("[Alloy] ASM hooks may fail with ClassNotFoundException.");
        }

        System.out.println("[Alloy] Agent active — bytecode transformation enabled");
        inst.addTransformer(new AlloyTransformer(), false);

        // Initialize the API and discover/load mods now, before MC's main() runs.
        // This ensures AlloyAPI is fully initialized when ASM-injected hooks fire.
        net.alloymc.loader.AlloyLoader.bootstrap();
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
