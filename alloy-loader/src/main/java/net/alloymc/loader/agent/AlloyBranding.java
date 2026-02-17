package net.alloymc.loader.agent;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Static helpers for Alloy branding: window title, icon, and logo rendering.
 *
 * At runtime these are called from bytecode injected by {@link AlloyTransformer}.
 * LWJGL and java-objc-bridge are on the Minecraft classpath — they are compileOnly
 * dependencies of alloy-loader.
 *
 * Logo rendering uses reflection to call MC's obfuscated rendering APIs
 * (NativeImage, DynamicTexture, TextureManager, GuiGraphics.blit).
 */
public final class AlloyBranding {

    public static final String TITLE = "AlloyMC 1.21.11 \u00b7 Alloy v0.1.0";

    // Logo texture state
    private static boolean logoInitialized;
    private static boolean logoAvailable;
    private static Object logoIdentifier;   // amo (Identifier) instance
    private static int logoRenderWidth;
    private static int logoRenderHeight;

    private static final int LOGO_TARGET_HEIGHT = 48;
    private static final int LOGO_Y = 15;

    // Cached reflection handles
    private static Method blitMethod;
    private static Object guiTexturedPipeline;  // RenderPipelines.GUI_TEXTURED (hpa.at)
    private static int logoTexWidth;
    private static int logoTexHeight;
    private static Method drawCenteredStringMethod;
    private static boolean blitErrorLogged;

    // MC classloader — captured from the first MC object we see.
    // AlloyBranding lives on the bootstrap classpath, so Class.forName()
    // defaults to the bootstrap CL which can't see MC classes.
    // We need MC's classloader for all MC class lookups.
    private static volatile ClassLoader mcClassLoader;

    private AlloyBranding() {}

    /**
     * Loads a class using MC's classloader instead of the bootstrap CL.
     * Falls back to Thread context CL and then default Class.forName.
     */
    private static Class<?> mcClass(String name) throws ClassNotFoundException {
        if (mcClassLoader != null) {
            return Class.forName(name, true, mcClassLoader);
        }
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null) {
            return Class.forName(name, true, ctx);
        }
        return Class.forName(name);
    }

    // ============================== Window Icon ==============================

    /**
     * Replaces the GLFW window icon with Alloy's logo.
     *
     * @param windowHandle the GLFW window handle ({@code Window.handle} / field {@code g})
     */
    public static void setWindowIcon(long windowHandle) {
        String iconPath = System.getProperty("alloy.icon.path");
        if (iconPath == null || iconPath.isBlank()) {
            System.err.println("[Alloy] alloy.icon.path not set \u2014 skipping icon override");
            return;
        }

        Path path = Path.of(iconPath);
        if (!Files.exists(path)) {
            System.err.println("[Alloy] Icon file not found: " + iconPath);
            return;
        }

        try {
            byte[] pngBytes = Files.readAllBytes(path);
            ByteBuffer pngBuffer = ByteBuffer.allocateDirect(pngBytes.length);
            pngBuffer.put(pngBytes).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer widthBuf = stack.mallocInt(1);
                IntBuffer heightBuf = stack.mallocInt(1);
                IntBuffer channelsBuf = stack.mallocInt(1);

                ByteBuffer pixels = STBImage.stbi_load_from_memory(
                        pngBuffer, widthBuf, heightBuf, channelsBuf, 4);

                if (pixels == null) {
                    System.err.println("[Alloy] Failed to decode icon: "
                            + STBImage.stbi_failure_reason());
                    return;
                }

                try {
                    GLFWImage.Buffer images = GLFWImage.malloc(1, stack);
                    images.position(0)
                            .width(widthBuf.get(0))
                            .height(heightBuf.get(0))
                            .pixels(pixels);
                    images.position(0);

                    GLFW.glfwSetWindowIcon(windowHandle, images);
                    System.out.println("[Alloy] Window icon set from " + iconPath);
                } finally {
                    STBImage.stbi_image_free(pixels);
                }
            }

            setMacOsDockIcon(iconPath);
        } catch (IOException e) {
            System.err.println("[Alloy] Failed to load icon: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[Alloy] Icon setup error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============================= Title Logo ================================

    /**
     * Renders the Alloy logo (or "ALLOY" text fallback) and tagline on the title screen.
     * Called from ASM-injected bytecode in TitleScreen.render.
     *
     * @param guiGraphics the GuiGraphics instance (gir)
     * @param font        the Font instance (gio)
     * @param screenWidth the screen width
     */
    public static void renderLogo(Object guiGraphics, Object font, int screenWidth) {
        // Capture MC's classloader from the first MC object we receive
        if (mcClassLoader == null && guiGraphics != null) {
            mcClassLoader = guiGraphics.getClass().getClassLoader();
        }
        if (!logoInitialized) {
            initializeLogo();
        }

        int taglineY;
        if (logoAvailable) {
            try {
                int x = (screenWidth - logoRenderWidth) / 2;
                blitLogo(guiGraphics, x, LOGO_Y, logoRenderWidth, logoRenderHeight);
                taglineY = LOGO_Y + logoRenderHeight + 5;
            } catch (Exception e) {
                if (!blitErrorLogged) {
                    System.err.println("[Alloy] Logo blit failed, falling back to text: " + e.getMessage());
                    e.printStackTrace();
                    blitErrorLogged = true;
                }
                logoAvailable = false;
                drawCenteredText(guiGraphics, font, "ALLOY", screenWidth / 2, 30, 0xFFFF6B00);
                taglineY = 45;
            }
        } else {
            drawCenteredText(guiGraphics, font, "ALLOY", screenWidth / 2, 30, 0xFFFF6B00);
            taglineY = 45;
        }

        // Tagline below logo/text
        drawCenteredText(guiGraphics, font, "Forged with Alloy", screenWidth / 2, taglineY, 0xFFA8A29E);
    }

    private static void blitLogo(Object guiGraphics, int x, int y, int w, int h) throws Exception {
        if (blitMethod == null) {
            List<String> blitDebug = new ArrayList<>();
            blitDebug.add("[Alloy Blit Search] " + java.time.Instant.now());

            // Get RenderPipelines.GUI_TEXTURED -> hpa.at
            Class<?> renderPipelinesClass = mcClass("hpa");
            Class<?> renderPipelineClass = mcClass("com.mojang.blaze3d.pipeline.RenderPipeline");
            guiTexturedPipeline = renderPipelinesClass.getField("at").get(null);
            blitDebug.add("GUI_TEXTURED pipeline: " + guiTexturedPipeline);

            // Find scaled blit: 12 params
            // blit(RenderPipeline, Identifier, int, int, float, float, int, int, int, int, int, int)
            for (Method m : guiGraphics.getClass().getMethods()) {
                if ("a".equals(m.getName()) && m.getParameterCount() == 12) {
                    Class<?>[] params = m.getParameterTypes();
                    if (renderPipelineClass.isAssignableFrom(params[0])
                            && !params[1].isPrimitive()
                            && params[2] == int.class && params[3] == int.class
                            && params[4] == float.class && params[5] == float.class
                            && params[6] == int.class && params[7] == int.class
                            && params[8] == int.class && params[9] == int.class
                            && params[10] == int.class && params[11] == int.class) {
                        blitMethod = m;
                        blitDebug.add("FOUND 12-param: " + m);
                        break;
                    }
                }
            }

            if (blitMethod == null) {
                blitDebug.add("12-param NOT FOUND, falling back to 10-param");
                // Fallback: 10-param non-scaled blit
                for (Method m : guiGraphics.getClass().getMethods()) {
                    if ("a".equals(m.getName()) && m.getParameterCount() == 10) {
                        Class<?>[] params = m.getParameterTypes();
                        if (renderPipelineClass.isAssignableFrom(params[0])
                                && !params[1].isPrimitive()
                                && params[2] == int.class && params[3] == int.class
                                && params[4] == float.class && params[5] == float.class
                                && params[6] == int.class && params[7] == int.class
                                && params[8] == int.class && params[9] == int.class) {
                            blitMethod = m;
                            blitDebug.add("FOUND 10-param fallback: " + m);
                            break;
                        }
                    }
                }
            }

            try {
                Path debugFile = Path.of(System.getProperty("user.dir", "."), "alloy-blit-debug.txt");
                Files.writeString(debugFile, String.join("\n", blitDebug) + "\n");
            } catch (Exception ignored) {}

            if (blitMethod == null) {
                throw new NoSuchMethodException("GuiGraphics.blit not found");
            }
        }

        if (blitMethod.getParameterCount() == 12) {
            // Scaled blit: (pipeline, texture, x, y, u, v, renderW, renderH, regionW, regionH, texW, texH)
            blitMethod.invoke(guiGraphics, guiTexturedPipeline, logoIdentifier,
                    x, y, 0.0f, 0.0f, w, h, logoTexWidth, logoTexHeight, logoTexWidth, logoTexHeight);
        } else {
            // Non-scaled blit: render at full texture size
            blitMethod.invoke(guiGraphics, guiTexturedPipeline, logoIdentifier,
                    x, y, 0.0f, 0.0f, logoTexWidth, logoTexHeight, logoTexWidth, logoTexHeight);
        }
    }

    private static void drawCenteredText(Object guiGraphics, Object font,
                                         String text, int x, int y, int color) {
        try {
            if (drawCenteredStringMethod == null) {
                // drawCenteredString: a(Lgio;Ljava/lang/String;III)V
                drawCenteredStringMethod = guiGraphics.getClass().getMethod("a",
                        font.getClass(), String.class, int.class, int.class, int.class);
            }
            drawCenteredStringMethod.invoke(guiGraphics, font, text, x, y, color);
        } catch (Exception e) {
            // Silent — text won't render but game continues
        }
    }

    private static synchronized void initializeLogo() {
        if (logoInitialized) return;
        logoInitialized = true;

        List<String> debug = new ArrayList<>();
        debug.add("[Alloy Logo Init] " + java.time.Instant.now());

        String iconPath = System.getProperty("alloy.icon.path");
        debug.add("icon.path = " + iconPath);
        if (iconPath == null || iconPath.isBlank()) {
            debug.add("ABORT: alloy.icon.path not set");
            writeDebugLog(debug);
            return;
        }

        Path path = Path.of(iconPath);
        if (!Files.exists(path)) {
            debug.add("ABORT: file not found");
            writeDebugLog(debug);
            return;
        }

        try {
            byte[] pngBytes = Files.readAllBytes(path);
            debug.add("PNG bytes: " + pngBytes.length);

            // Step 1: NativeImage.read(byte[]) -> fyh.a(byte[])
            Class<?> nativeImageClass = mcClass("fyh");
            debug.add("NativeImage class: " + nativeImageClass);
            Method readMethod = nativeImageClass.getMethod("a", byte[].class);
            debug.add("read method: " + readMethod + " (static=" + java.lang.reflect.Modifier.isStatic(readMethod.getModifiers()) + ")");
            Object nativeImage = readMethod.invoke(null, (Object) pngBytes);
            debug.add("NativeImage created: " + nativeImage);

            // Step 2: getWidth/getHeight
            int imgWidth = ((Number) nativeImageClass.getMethod("a").invoke(nativeImage)).intValue();
            int imgHeight = ((Number) nativeImageClass.getMethod("b").invoke(nativeImage)).intValue();
            debug.add("Image size: " + imgWidth + "x" + imgHeight);

            logoTexWidth = imgWidth;
            logoTexHeight = imgHeight;
            double scale = (double) LOGO_TARGET_HEIGHT / imgHeight;
            logoRenderWidth = (int) (imgWidth * scale);
            logoRenderHeight = LOGO_TARGET_HEIGHT;
            debug.add("Render size: " + logoRenderWidth + "x" + logoRenderHeight);

            // Step 3: DynamicTexture
            Class<?> dynamicTextureClass = mcClass("ilc");
            debug.add("DynamicTexture class: " + dynamicTextureClass);
            Constructor<?> texCtor = dynamicTextureClass.getConstructor(
                    Supplier.class, nativeImageClass);
            debug.add("DynTex constructor: " + texCtor);
            Object dynamicTexture = texCtor.newInstance(
                    (Supplier<String>) () -> "alloy_logo", nativeImage);
            debug.add("DynamicTexture created: " + dynamicTexture);

            // Step 4: Identifier
            Class<?> identifierClass = mcClass("amo");
            Method fromNsPath = identifierClass.getMethod("a", String.class, String.class);
            debug.add("Identifier.a method: " + fromNsPath + " (static=" + java.lang.reflect.Modifier.isStatic(fromNsPath.getModifiers()) + ")");
            logoIdentifier = fromNsPath.invoke(null, "alloy", "logo");
            debug.add("Identifier: " + logoIdentifier);

            // Step 5: Minecraft + TextureManager
            Class<?> minecraftClass = mcClass("gfj");
            Method getInstance = minecraftClass.getMethod("V");
            Object minecraft = getInstance.invoke(null);
            debug.add("Minecraft instance: " + (minecraft != null ? "OK" : "NULL"));

            Method getTexManager = minecraftClass.getMethod("af");
            Object texManager = getTexManager.invoke(minecraft);
            debug.add("TextureManager: " + (texManager != null ? texManager.getClass().getName() : "NULL"));

            // Step 6: Find register method
            Method registerMethod = null;
            for (Method m : texManager.getClass().getMethods()) {
                if ("a".equals(m.getName()) && m.getParameterCount() == 2) {
                    Class<?>[] params = m.getParameterTypes();
                    debug.add("  candidate: a(" + params[0].getName() + ", " + params[1].getName() + ")");
                    if (params[0].isAssignableFrom(identifierClass)
                            && params[1].isAssignableFrom(dynamicTextureClass)) {
                        registerMethod = m;
                        debug.add("  -> MATCHED");
                        break;
                    }
                }
            }
            if (registerMethod == null) {
                debug.add("FAIL: No register method found");
                writeDebugLog(debug);
                return;
            }

            registerMethod.invoke(texManager, logoIdentifier, dynamicTexture);
            debug.add("Texture registered OK");

            logoAvailable = true;
            debug.add("SUCCESS: logoAvailable = true");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            debug.add("EXCEPTION: " + sw);
        }

        writeDebugLog(debug);
    }

    private static void writeDebugLog(List<String> lines) {
        try {
            Path debugFile = Path.of(System.getProperty("user.dir", "."), "alloy-logo-debug.txt");
            Files.writeString(debugFile, String.join("\n", lines) + "\n");
        } catch (Exception ignored) {}
    }

    // ========================= Button Background ============================

    // Cached reflection handles for button rendering
    private static Method btnGetX, btnGetY, btnGetWidth, btnGetHeight;
    private static Method btnIsActive, btnIsHoveredOrFocused;
    private static Method gfxFill, gfxFillGradient;
    private static boolean buttonReflectionInit;

    /**
     * Renders a glass orange/black button background. Called from ASM-injected
     * bytecode that replaces AbstractButton.renderDefaultSprite.
     *
     * @param guiGraphics the GuiGraphics instance (gir)
     * @param button      the AbstractButton instance (giu)
     */
    public static void renderButtonBackground(Object guiGraphics, Object button) {
        try {
            if (mcClassLoader == null && guiGraphics != null) {
                mcClassLoader = guiGraphics.getClass().getClassLoader();
            }
            if (!buttonReflectionInit) {
                initButtonReflection(guiGraphics, button);
            }
            if (gfxFill == null || gfxFillGradient == null) return;

            int x = ((Number) btnGetX.invoke(button)).intValue();
            int y = ((Number) btnGetY.invoke(button)).intValue();
            int w = ((Number) btnGetWidth.invoke(button)).intValue();
            int h = ((Number) btnGetHeight.invoke(button)).intValue();
            boolean active = (boolean) btnIsActive.invoke(button);
            boolean hovered = (boolean) btnIsHoveredOrFocused.invoke(button);

            int bgTop, bgBottom, border;
            if (!active) {
                // Disabled
                bgTop    = 0x80151515;
                bgBottom = 0x80101010;
                border   = 0x30404040;
            } else if (hovered) {
                // Hovered / focused
                bgTop    = 0xC0252525;
                bgBottom = 0xC0181818;
                border   = 0x80FF6B00;
            } else {
                // Normal (active)
                bgTop    = 0xC01A1A1A;
                bgBottom = 0xC0101010;
                border   = 0x40FF6B00;
            }

            // Gradient background
            gfxFillGradient.invoke(guiGraphics, x, y, x + w, y + h, bgTop, bgBottom);

            // 1px border: top, bottom, left, right
            gfxFill.invoke(guiGraphics, x, y, x + w, y + 1, border);
            gfxFill.invoke(guiGraphics, x, y + h - 1, x + w, y + h, border);
            gfxFill.invoke(guiGraphics, x, y + 1, x + 1, y + h - 1, border);
            gfxFill.invoke(guiGraphics, x + w - 1, y + 1, x + w, y + h - 1, border);
        } catch (Exception e) {
            // Silent — button won't have custom background but game continues
        }
    }

    private static synchronized void initButtonReflection(Object guiGraphics, Object button) {
        if (buttonReflectionInit) return;
        buttonReflectionInit = true;

        try {
            Class<?> btn = button.getClass();
            btnGetX      = findNoArgMethod(btn, "aT_", int.class);
            btnGetY      = findNoArgMethod(btn, "aU_", int.class);
            btnGetWidth  = findNoArgMethod(btn, "aS_", int.class);
            btnGetHeight = findNoArgMethod(btn, "aR_", int.class);
            btnIsActive  = findNoArgMethod(btn, "b", boolean.class);
            btnIsHoveredOrFocused = findNoArgMethod(btn, "D", boolean.class);

            Class<?> gfx = guiGraphics.getClass();
            for (Method m : gfx.getMethods()) {
                if (!"a".equals(m.getName()) || m.getReturnType() != void.class) continue;
                Class<?>[] p = m.getParameterTypes();
                if (!allInts(p)) continue;
                if (p.length == 5 && gfxFill == null) gfxFill = m;
                if (p.length == 6 && gfxFillGradient == null) gfxFillGradient = m;
            }

            System.out.println("[Alloy] Button reflection init: fill=" + gfxFill
                    + ", fillGradient=" + gfxFillGradient);
        } catch (Exception e) {
            System.err.println("[Alloy] Button reflection init failed: " + e.getMessage());
        }
    }

    private static Method findNoArgMethod(Class<?> cls, String name, Class<?> returnType) {
        for (Method m : cls.getMethods()) {
            if (name.equals(m.getName()) && m.getParameterCount() == 0
                    && m.getReturnType() == returnType) {
                return m;
            }
        }
        return null;
    }

    private static boolean allInts(Class<?>[] types) {
        for (Class<?> t : types) {
            if (t != int.class) return false;
        }
        return true;
    }

    // =========================== Slider Rendering =============================

    // Cached reflection handles for slider rendering
    private static java.lang.reflect.Field sliderValueField;
    private static Method sliderGetMessage;
    private static Method componentGetString;
    private static Object mcFont; // cached Minecraft.getInstance().font
    private static boolean sliderReflectionInit;

    /**
     * Renders a glass orange/black slider with custom track and handle.
     * Called from ASM-injected bytecode that replaces AbstractSliderButton.renderWidget.
     */
    public static void renderSliderWidget(Object guiGraphics, Object slider,
                                           int mouseX, int mouseY, float delta) {
        try {
            if (!buttonReflectionInit) {
                initButtonReflection(guiGraphics, slider);
            }
            if (!sliderReflectionInit) {
                initSliderReflection(slider);
            }
            if (gfxFill == null || gfxFillGradient == null) return;

            // Ensure drawCenteredStringMethod is initialized for slider text.
            // It may be null if renderLogo() on the title screen hasn't run yet.
            if (drawCenteredStringMethod == null && mcFont != null) {
                try {
                    drawCenteredStringMethod = guiGraphics.getClass().getMethod("a",
                            mcFont.getClass(), String.class, int.class, int.class, int.class);
                } catch (Exception ignored) {}
            }

            int x = ((Number) btnGetX.invoke(slider)).intValue();
            int y = ((Number) btnGetY.invoke(slider)).intValue();
            int w = ((Number) btnGetWidth.invoke(slider)).intValue();
            int h = ((Number) btnGetHeight.invoke(slider)).intValue();
            boolean active = (boolean) btnIsActive.invoke(slider);
            boolean hovered = (boolean) btnIsHoveredOrFocused.invoke(slider);

            // Get slider value (0.0 to 1.0)
            double value = 0.5;
            if (sliderValueField != null) {
                value = sliderValueField.getDouble(slider);
            }

            // Track colors (same scheme as buttons)
            int bgTop, bgBottom, border;
            if (!active) {
                bgTop    = 0x80151515;
                bgBottom = 0x80101010;
                border   = 0x30404040;
            } else if (hovered) {
                bgTop    = 0xC0252525;
                bgBottom = 0xC0181818;
                border   = 0x80FF6B00;
            } else {
                bgTop    = 0xC01A1A1A;
                bgBottom = 0xC0101010;
                border   = 0x40FF6B00;
            }

            // Draw track background
            gfxFillGradient.invoke(guiGraphics, x, y, x + w, y + h, bgTop, bgBottom);

            // Draw track border
            gfxFill.invoke(guiGraphics, x, y, x + w, y + 1, border);
            gfxFill.invoke(guiGraphics, x, y + h - 1, x + w, y + h, border);
            gfxFill.invoke(guiGraphics, x, y + 1, x + 1, y + h - 1, border);
            gfxFill.invoke(guiGraphics, x + w - 1, y + 1, x + w, y + h - 1, border);

            // Draw handle
            int handleW = 8;
            int handleX = x + (int) (value * (w - handleW));
            int handleColor = active ? (hovered ? 0xE0FF6B00 : 0xC0FF6B00) : 0x60404040;
            gfxFill.invoke(guiGraphics, handleX, y, handleX + handleW, y + h, handleColor);

            // Draw text label
            if (mcFont != null && sliderGetMessage != null && drawCenteredStringMethod != null) {
                Object message = sliderGetMessage.invoke(slider);
                if (message != null) {
                    String text = (String) componentGetString.invoke(message);
                    int textColor = active ? 0xFFFFFFFF : 0xFFA0A0A0;
                    drawCenteredStringMethod.invoke(guiGraphics, mcFont, text,
                            x + w / 2, y + (h - 8) / 2, textColor);
                }
            }
        } catch (Exception e) {
            // Silent — slider won't render custom but game continues
        }
    }

    private static synchronized void initSliderReflection(Object slider) {
        if (sliderReflectionInit) return;
        sliderReflectionInit = true;

        try {
            // Slider value field: AbstractSliderButton.value -> e (double)
            Class<?> sliderClass = slider.getClass();
            for (Class<?> cls = sliderClass; cls != null; cls = cls.getSuperclass()) {
                try {
                    sliderValueField = cls.getDeclaredField("e");
                    if (sliderValueField.getType() == double.class) {
                        sliderValueField.setAccessible(true);
                        break;
                    }
                    sliderValueField = null;
                } catch (NoSuchFieldException ignored) {}
            }

            // getMessage() -> B() on AbstractWidget
            sliderGetMessage = findNoArgMethod(sliderClass, "B", Object.class);
            if (sliderGetMessage == null) {
                // Try with the interface return type
                for (Method m : sliderClass.getMethods()) {
                    if ("B".equals(m.getName()) && m.getParameterCount() == 0
                            && !m.getReturnType().isPrimitive()) {
                        sliderGetMessage = m;
                        break;
                    }
                }
            }

            // Component.getString() — stays as "getString"
            if (sliderGetMessage != null) {
                componentGetString = sliderGetMessage.getReturnType().getMethod("getString");
            }

            // Get Minecraft.getInstance().font -> gfj.A.g
            Class<?> mcClass = slider.getClass().getClassLoader().loadClass("gfj");
            java.lang.reflect.Field instanceField = mcClass.getDeclaredField("A");
            instanceField.setAccessible(true);
            Object mcInstance = instanceField.get(null);
            if (mcInstance != null) {
                java.lang.reflect.Field fontField = mcClass.getDeclaredField("g");
                fontField.setAccessible(true);
                mcFont = fontField.get(mcInstance);
            }

            System.out.println("[Alloy] Slider reflection init: value=" + (sliderValueField != null)
                    + ", getMessage=" + (sliderGetMessage != null)
                    + ", font=" + (mcFont != null));
        } catch (Exception e) {
            System.err.println("[Alloy] Slider reflection init failed: " + e.getMessage());
        }
    }

    // ====================== Title Screen: Realms → AlloyMC ====================

    /**
     * Replaces the "Minecraft Realms" button on the title screen with an "AlloyMC"
     * button that opens https://alloymc.net/ in the default browser.
     *
     * Called from ASM-injected bytecode at the end of TitleScreen.init (bg_).
     */
    @SuppressWarnings("unchecked")
    public static void modifyTitleScreenInit(Object titleScreen) {
        try {
            // Use Minecraft's classloader — obfuscated classes aren't on the system classloader
            ClassLoader cl = titleScreen.getClass().getClassLoader();

            // gsd (TitleScreen) extends gsb (Screen)
            Class<?> screenClass = titleScreen.getClass().getSuperclass();
            Class<?> buttonClass = cl.loadClass("gje");
            Class<?> widgetClass = cl.loadClass("gmm");

            // Access renderables list: Screen.d (List<gmm>)
            java.lang.reflect.Field renderablesField = screenClass.getDeclaredField("d");
            renderablesField.setAccessible(true);
            List<Object> renderables = (List<Object>) renderablesField.get(titleScreen);

            // Find the Realms button — it's a Button (gje) whose message contains "Realms"
            // getMessage() = B() on AbstractWidget, returns Component (yh)
            // Component.getString() returns the visible text
            Object realmsButton = null;
            int realmsX = 0, realmsY = 0, realmsW = 0, realmsH = 0;

            for (Object widget : renderables) {
                if (!buttonClass.isInstance(widget)) continue;
                try {
                    Method getMessage = findMethodByName(widget.getClass(), "B", 0);
                    if (getMessage == null) continue;
                    Object message = getMessage.invoke(widget);
                    if (message == null) continue;
                    String text = (String) message.getClass().getMethod("getString").invoke(message);
                    if (text != null && text.contains("Realms")) {
                        realmsButton = widget;
                        realmsX = ((Number) findMethodByName(widget.getClass(), "aT_", 0).invoke(widget)).intValue();
                        realmsY = ((Number) findMethodByName(widget.getClass(), "aU_", 0).invoke(widget)).intValue();
                        realmsW = ((Number) findMethodByName(widget.getClass(), "aS_", 0).invoke(widget)).intValue();
                        realmsH = ((Number) findMethodByName(widget.getClass(), "aR_", 0).invoke(widget)).intValue();
                        break;
                    }
                } catch (Exception ignored) {}
            }

            if (realmsButton == null) {
                System.out.println("[Alloy] Realms button not found — skipping replacement");
                return;
            }

            // Remove the Realms button from all screen lists
            renderables.remove(realmsButton);
            try {
                Method removeWidget = screenClass.getDeclaredMethod("e", widgetClass);
                removeWidget.setAccessible(true);
                removeWidget.invoke(titleScreen, realmsButton);
            } catch (Exception ignored) {}

            // Create "AlloyMC" button text: Component.literal("AlloyMC") = yh.a(String)
            Class<?> componentClass = cl.loadClass("yh");
            Method literalMethod = componentClass.getMethod("a", String.class);
            Object buttonText = literalMethod.invoke(null, "AlloyMC");

            // Create OnPress handler via dynamic proxy for gje$c (Button.OnPress)
            Class<?> onPressInterface = cl.loadClass("gje$c");
            Object onPress = java.lang.reflect.Proxy.newProxyInstance(
                    cl,
                    new Class<?>[]{onPressInterface},
                    (proxy, method, args) -> {
                        if ("onPress".equals(method.getName())) {
                            openAlloyWebsite();
                        }
                        return null;
                    }
            );

            // Build button: Button.builder(text, onPress).bounds(x, y, w, h).build()
            // gje.a(yh, gje$c) -> gje$a.a(int,int,int,int) -> gje$a.a() -> gje
            Class<?> builderClass = cl.loadClass("gje$a");
            Method builder = buttonClass.getMethod("a", componentClass, onPressInterface);
            Object buttonBuilder = builder.invoke(null, buttonText, onPress);
            Method bounds = builderClass.getMethod("a", int.class, int.class, int.class, int.class);
            buttonBuilder = bounds.invoke(buttonBuilder, realmsX, realmsY, realmsW, realmsH);
            Method build = builderClass.getMethod("a");
            Object newButton = build.invoke(buttonBuilder);

            // Add to screen: Screen.c(T) = addRenderableWidget, erased to c(gmm)
            Method addWidget = screenClass.getDeclaredMethod("c", widgetClass);
            addWidget.setAccessible(true);
            addWidget.invoke(titleScreen, newButton);

            System.out.println("[Alloy] Replaced Realms button with AlloyMC link");
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to replace Realms button: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Method findMethodByName(Class<?> cls, String name, int paramCount) {
        for (Method m : cls.getMethods()) {
            if (name.equals(m.getName()) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }

    private static void openAlloyWebsite() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("mac")) {
                pb = new ProcessBuilder("open", "https://alloymc.net/");
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "https://alloymc.net/");
            } else {
                pb = new ProcessBuilder("xdg-open", "https://alloymc.net/");
            }
            pb.start();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to open AlloyMC website: " + e.getMessage());
        }
    }

    // ============================ macOS Dock Icon ============================

    /**
     * On macOS, GLFW ignores {@code glfwSetWindowIcon}. Set the dock icon via
     * the Objective-C bridge (java-objc-bridge is on the MC classpath).
     */
    private static void setMacOsDockIcon(String iconPath) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return;
        }

        try {
            Class<?> clientClass = mcClass("ca.weblite.objc.Client");
            Class<?> proxyClass = mcClass("ca.weblite.objc.Proxy");

            Object client = clientClass.getMethod("getInstance").invoke(null);

            var sendProxyMethod = clientClass.getMethod("sendProxy",
                    String.class, String.class, Object[].class);
            Object nsApp = sendProxyMethod.invoke(client,
                    "NSApplication", "sharedApplication", new Object[0]);

            Object nsImageAlloc = sendProxyMethod.invoke(client,
                    "NSImage", "alloc", new Object[0]);

            var proxySendProxy = proxyClass.getMethod("sendProxy",
                    String.class, Object[].class);
            Object nsImage = proxySendProxy.invoke(nsImageAlloc,
                    "initWithContentsOfFile:", new Object[]{iconPath});

            var proxySend = proxyClass.getMethod("send",
                    String.class, Object[].class);
            proxySend.invoke(nsApp,
                    "setApplicationIconImage:", new Object[]{nsImage});

            System.out.println("[Alloy] macOS dock icon set");
        } catch (Exception e) {
            System.err.println("[Alloy] macOS dock icon failed (non-fatal): " + e.getMessage());
        }
    }
}
