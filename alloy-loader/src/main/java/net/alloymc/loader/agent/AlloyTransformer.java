package net.alloymc.loader.agent;

import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * ASM-based class transformer that injects Alloy branding into Minecraft.
 *
 * Transformations (obfuscated names from Mojang ProGuard mappings for MC 1.21.11):
 * <ul>
 *   <li><b>Window (fyk)</b>: Replaces window title with Alloy branding string</li>
 *   <li><b>Window (fyk)</b>: Replaces window icon via {@link AlloyBranding#setWindowIcon}</li>
 *   <li><b>LogoRenderer (gjw)</b>: No-ops renderLogo to hide the Minecraft logo</li>
 *   <li><b>SplashRenderer (gkm)</b>: No-ops render to hide splash text</li>
 *   <li><b>TitleScreen (gsd)</b>: Empties COPYRIGHT_TEXT, injects Alloy branding text</li>
 *   <li><b>PauseScreen (grx)</b>: Injects subtle Alloy version text</li>
 *   <li><b>AbstractButton (giu)</b>: Replaces button sprite with glass orange/black background</li>
 * </ul>
 *
 * Mapping reference:
 * <pre>
 *   Screen (gsb):          font=q, width=o, height=p, minecraft=n
 *   TitleScreen (gsd):     render=a(Lgir;IIF)V, clinit, COPYRIGHT_TEXT=c
 *   PauseScreen (grx):     render=a(Lgir;IIF)V
 *   Window (fyk):          setTitle=b(String)V, setIcon=a(Lazk;Lfyb;)V, handle=g
 *   LogoRenderer (gjw):    renderLogo=a(Lgir;IF)V and a(Lgir;IFI)V
 *   SplashRenderer (gkm):  render=a(Lgir;ILgio;F)V
 *   GuiGraphics (gir):     drawString=b(Lgio;String;III)V, drawCenteredString=a(Lgio;String;III)V
 *   Font (gio):            width(String)=b(String)I
 *   Component (yh):        literal(String)=a(String)Lyh; (static interface method)
 *   AbstractWidget (gjc):  getX=aT_()I, getY=aU_()I, getWidth=aS_()I, getHeight=aR_()I,
 *                          isActive=b()Z, isHoveredOrFocused=D()Z, getAlpha=A()F
 *   AbstractButton (giu):  renderDefaultSprite=a(Lgir;)V
 * </pre>
 */
public final class AlloyTransformer implements ClassFileTransformer {

    // --- Class names (obfuscated) ---
    private static final String WINDOW_CLASS = "fyk";
    private static final String TITLE_SCREEN_CLASS = "gsd";
    private static final String PAUSE_SCREEN_CLASS = "grx";
    private static final String LOGO_RENDERER_CLASS = "gjw";
    private static final String SPLASH_RENDERER_CLASS = "gkm";
    private static final String ABSTRACT_BUTTON_CLASS = "giu";
    private static final String ABSTRACT_SLIDER_CLASS = "giz";

    // --- Server-side: ServerGamePacketListenerImpl (ayi) ---
    private static final String SERVER_GAME_HANDLER_CLASS = "ayi";
    private static final String COMMAND_DISPATCH_HOOK_CLASS = "net/alloymc/loader/agent/CommandDispatchHook";
    private static final String EVENT_HOOK_CLASS = "net/alloymc/loader/agent/EventFiringHook";

    // --- Handshake: ServerHandshakePacketListenerImpl (ayj) ---
    private static final String HANDSHAKE_HANDLER_CLASS = "ayj";
    private static final String HANDSHAKE_HOOK_CLASS = "net/alloymc/loader/agent/AlloyHandshakeHook";

    // --- Handshake: ClientIntentionPacket (akj) ---
    private static final String CLIENT_INTENTION_PACKET_CLASS = "akj";

    // --- Server-side: PlayerList (bbz) ---
    private static final String PLAYER_LIST_CLASS = "bbz";

    // --- Server-side: ServerPlayerGameMode (axh) ---
    private static final String SERVER_PLAYER_GAME_MODE_CLASS = "axh";

    // --- Server-side: ServerPlayer (axg) ---
    private static final String SERVER_PLAYER_CLASS = "axg";

    // --- Server-side: ServerLevel (axf) ---
    private static final String SERVER_LEVEL_CLASS = "axf";

    // --- Server-side: LivingEntity (chl) ---
    private static final String LIVING_ENTITY_CLASS = "chl";

    // --- New entity/block class transforms ---
    private static final String BLOCK_ITEM_CLASS = "dkb";
    private static final String BUCKET_ITEM_CLASS = "dkh";
    private static final String SERVER_EXPLOSION_CLASS = "dxe";
    private static final String MOB_CLASS = "chn";
    private static final String FLOWING_FLUID_CLASS = "fkz";
    private static final String FIRE_BLOCK_CLASS = "ecv";
    private static final String PISTON_BASE_BLOCK_CLASS = "eny";
    private static final String DISPENSER_BLOCK_CLASS = "eby";
    private static final String TREE_GROWER_CLASS = "enu";
    private static final String THROWN_SPLASH_POTION_CLASS = "dff";

    // --- Packet class descriptors for ayi method matching ---
    private static final String CHAT_PACKET_DESC = "(Laik;)V";
    private static final String PLAYER_ACTION_PACKET_DESC = "(Laji;)V";
    private static final String INTERACT_PACKET_DESC = "(Laiy;)V";
    private static final String MOVE_PACKET_DESC = "(Lajb;)V";
    private static final String SET_CARRIED_ITEM_PACKET_DESC = "(Lajt;)V";
    private static final String SIGN_UPDATE_PACKET_DESC = "(Laka;)V";
    private static final String USE_ITEM_ON_PACKET_DESC = "(Lake;)V";
    private static final String CONTAINER_CLICK_PACKET_DESC = "(Lais;)V";
    private static final String CONTAINER_CLOSE_PACKET_DESC = "(Lait;)V";

    // --- InteractionResult (cdc) ---
    private static final String INTERACTION_RESULT_CLASS = "cdc";

    // --- MinecraftServer (not obfuscated) ---
    private static final String MINECRAFT_SERVER_CLASS = "net/minecraft/server/MinecraftServer";

    // --- Screen (gsb) fields ---
    private static final String SCREEN_CLASS = "gsb";
    private static final String FONT_FIELD = "q";
    private static final String FONT_CLASS = "gio";
    private static final String WIDTH_FIELD = "o";
    private static final String HEIGHT_FIELD = "p";

    // --- GuiGraphics (gir) ---
    private static final String GUI_GRAPHICS_CLASS = "gir";
    private static final String DRAW_STRING = "b";
    private static final String DRAW_STRING_DESC =
            "(L" + FONT_CLASS + ";Ljava/lang/String;III)V";
    private static final String DRAW_CENTERED_STRING = "a";
    private static final String DRAW_CENTERED_STRING_DESC =
            "(L" + FONT_CLASS + ";Ljava/lang/String;III)V";

    // --- Render method descriptor: render(GuiGraphics, int, int, float) ---
    private static final String RENDER_DESC = "(L" + GUI_GRAPHICS_CLASS + ";IIF)V";

    // --- Component (yh) ---
    private static final String COMPONENT_CLASS = "yh";

    // --- AlloyBranding helper ---
    private static final String BRANDING_CLASS = "net/alloymc/loader/agent/AlloyBranding";

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className == null) return null;

        // Server-side event transforms — only apply on dedicated server.
        // On the client, these hooks call into EventFiringHook which references
        // API classes (Player, AlloyAPI, etc.) that can trigger ClassNotFoundException
        // when loaded from MC's classloader hierarchy.
        if (!"client".equalsIgnoreCase(System.getProperty("alloy.environment"))) {
            byte[] serverResult = switch (className) {
                case SERVER_GAME_HANDLER_CLASS -> transformServerGameHandler(classfileBuffer);
                case HANDSHAKE_HANDLER_CLASS -> transformHandshakeHandler(classfileBuffer);
                case PLAYER_LIST_CLASS -> transformPlayerList(classfileBuffer);
                case SERVER_PLAYER_GAME_MODE_CLASS -> transformServerPlayerGameMode(classfileBuffer);
                case MINECRAFT_SERVER_CLASS -> transformMinecraftServer(classfileBuffer);
                case SERVER_PLAYER_CLASS -> transformServerPlayer(classfileBuffer);
                case SERVER_LEVEL_CLASS -> transformServerLevel(classfileBuffer);
                case LIVING_ENTITY_CLASS -> transformLivingEntity(classfileBuffer);
                case BLOCK_ITEM_CLASS -> transformBlockItem(classfileBuffer);
                case BUCKET_ITEM_CLASS -> transformBucketItem(classfileBuffer);
                case SERVER_EXPLOSION_CLASS -> transformServerExplosion(classfileBuffer);
                case MOB_CLASS -> transformMob(classfileBuffer);
                case FLOWING_FLUID_CLASS -> transformFlowingFluid(classfileBuffer);
                case FIRE_BLOCK_CLASS -> transformFireBlock(classfileBuffer);
                case PISTON_BASE_BLOCK_CLASS -> transformPistonBaseBlock(classfileBuffer);
                case DISPENSER_BLOCK_CLASS -> transformDispenserBlock(classfileBuffer);
                case TREE_GROWER_CLASS -> transformTreeGrower(classfileBuffer);
                case THROWN_SPLASH_POTION_CLASS -> transformThrownSplashPotion(classfileBuffer);
                default -> null;
            };
            if (serverResult != null) return serverResult;
        }

        // Server has no LWJGL/GUI classes — skip all client transforms to avoid ClassNotFoundException
        if ("server".equalsIgnoreCase(System.getProperty("alloy.environment"))) {
            return null;
        }

        return switch (className) {
            case WINDOW_CLASS -> transformWindow(classfileBuffer);
            case LOGO_RENDERER_CLASS -> transformLogoRenderer(classfileBuffer);
            case SPLASH_RENDERER_CLASS -> transformSplashRenderer(classfileBuffer);
            case TITLE_SCREEN_CLASS -> transformTitleScreen(classfileBuffer, loader);
            case PAUSE_SCREEN_CLASS -> transformPauseScreen(classfileBuffer, loader);
            case ABSTRACT_BUTTON_CLASS -> transformAbstractButton(classfileBuffer);
            case ABSTRACT_SLIDER_CLASS -> transformAbstractSlider(classfileBuffer);
            case CLIENT_INTENTION_PACKET_CLASS -> transformClientIntentionPacket(classfileBuffer);
            default -> null;
        };
    }

    // ============================= 1. Window Title =============================

    /**
     * Replaces the window title with {@link AlloyBranding#TITLE} and redirects
     * the icon setter to {@link AlloyBranding#setWindowIcon(long)}.
     */
    private byte[] transformWindow(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // setTitle: b(Ljava/lang/String;)V — replace title argument
                    if ("b".equals(name) && "(Ljava/lang/String;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // Overwrite parameter: title = AlloyBranding.TITLE
                                mv.visitFieldInsn(Opcodes.GETSTATIC, BRANDING_CLASS,
                                        "TITLE", "Ljava/lang/String;");
                                mv.visitVarInsn(Opcodes.ASTORE, 1);
                            }
                        };
                    }

                    // setIcon: a(Lazk;Lfyb;)V — replace body with AlloyBranding call
                    if ("a".equals(name) && "(Lazk;Lfyb;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // AlloyBranding.setWindowIcon(this.g)
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitFieldInsn(Opcodes.GETFIELD, WINDOW_CLASS, "g", "J");
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRANDING_CLASS,
                                        "setWindowIcon", "(J)V", false);
                                mv.visitInsn(Opcodes.RETURN);
                            }

                            // Suppress the rest of the original method body
                            @Override
                            public void visitInsn(int opcode) { /* drop */ }
                            @Override
                            public void visitVarInsn(int opcode, int varIndex) { /* drop */ }
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String n, String desc) { /* drop */ }
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String n, String desc, boolean itf) { /* drop */ }
                            @Override
                            public void visitTypeInsn(int opcode, String type) { /* drop */ }
                            @Override
                            public void visitLdcInsn(Object value) { /* drop */ }
                            @Override
                            public void visitJumpInsn(int opcode, Label label) { /* drop */ }
                            @Override
                            public void visitLabel(Label label) { /* drop */ }
                            @Override
                            public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) { /* drop */ }
                            @Override
                            public void visitIntInsn(int opcode, int operand) { /* drop */ }
                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                // Provide correct maxs for our replacement body
                                super.visitMaxs(3, 3);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: Window \u2014 title + icon branding applied");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform Window: " + e.getMessage());
            return null;
        }
    }

    // ========================= 2. LogoRenderer No-Op =========================

    /**
     * No-ops both renderLogo overloads so the Minecraft logo doesn't render.
     */
    private byte[] transformLogoRenderer(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // a(Lgir;IF)V and a(Lgir;IFI)V — both renderLogo overloads
                    if ("a".equals(name) &&
                            (("(L" + GUI_GRAPHICS_CLASS + ";IF)V").equals(descriptor) ||
                             ("(L" + GUI_GRAPHICS_CLASS + ";IFI)V").equals(descriptor))) {
                        return replaceWithReturn(mv);
                    }
                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: LogoRenderer \u2014 renderLogo no-oped");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform LogoRenderer: " + e.getMessage());
            return null;
        }
    }

    // ======================== 3. SplashRenderer No-Op ========================

    /**
     * No-ops the splash text renderer so no random splash is drawn.
     */
    private byte[] transformSplashRenderer(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // a(Lgir;ILgio;F)V — render(GuiGraphics, int, Font, float)
                    if ("a".equals(name) &&
                            ("(L" + GUI_GRAPHICS_CLASS + ";IL" + FONT_CLASS + ";F)V").equals(descriptor)) {
                        return replaceWithReturn(mv);
                    }
                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: SplashRenderer \u2014 render no-oped");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform SplashRenderer: " + e.getMessage());
            return null;
        }
    }

    // ======================== 4. TitleScreen Branding ========================

    /**
     * Transforms the TitleScreen:
     * <ol>
     *   <li>{@code <clinit>}: empties COPYRIGHT_TEXT (field c) with Component.literal("")</li>
     *   <li>{@code render}: injects 4 branding text draws before each RETURN</li>
     * </ol>
     */
    private byte[] transformTitleScreen(byte[] classBytes, ClassLoader transformLoader) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // <clinit> — inject COPYRIGHT_TEXT override BEFORE RETURN
                    // (must run after original clinit sets field c, so we overwrite it)
                    if ("<clinit>".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    // gsd.c = Component.literal("")
                                    mv.visitLdcInsn("");
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                            COMPONENT_CLASS, "a",
                                            "(Ljava/lang/String;)L" + COMPONENT_CLASS + ";",
                                            true);  // interface method — literal(String)
                                    mv.visitFieldInsn(Opcodes.PUTSTATIC,
                                            TITLE_SCREEN_CLASS, "c",
                                            "L" + COMPONENT_CLASS + ";");
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    // render — suppress vanilla text draws + inject our branding before RETURN
                    if ("a".equals(name) && RENDER_DESC.equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner,
                                                        String mName, String mDesc, boolean itf) {
                                // Suppress drawString (b) and drawCenteredString (a) on GuiGraphics
                                // that take (Font, String, int, int, int) — these draw vanilla text
                                // like "Minecraft 1.21.11" and copyright
                                String paramPrefix = "(L" + FONT_CLASS + ";Ljava/lang/String;III)";
                                if (opcode == Opcodes.INVOKEVIRTUAL
                                        && GUI_GRAPHICS_CLASS.equals(owner)
                                        && ("b".equals(mName) || "a".equals(mName))
                                        && mDesc.startsWith(paramPrefix)) {
                                    // Pop all 6 values: objectref + font + text + x + y + color
                                    for (int i = 0; i < 6; i++) {
                                        mv.visitInsn(Opcodes.POP);
                                    }
                                    // If method returns int instead of void, push dummy 0
                                    if (mDesc.endsWith(")I")) {
                                        mv.visitInsn(Opcodes.ICONST_0);
                                    }
                                    return;
                                }
                                super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    injectTitleScreenBranding(mv);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    // bg_()V = Screen.init() — replace Realms button with AlloyMC link
                    if ("bg_".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    mv.visitVarInsn(Opcodes.ALOAD, 0); // this (TitleScreen)
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRANDING_CLASS,
                                            "modifyTitleScreenInit",
                                            "(Ljava/lang/Object;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            System.out.println("[Alloy] Transformed: TitleScreen \u2014 copyright cleared + render branding injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform TitleScreen: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Emits bytecode for Alloy branding on the title screen:
     * - Fill bar at bottom to cover vanilla version/copyright text
     * - AlloyBranding.renderLogo() for logo (or text fallback) + tagline
     * - Bottom-left and bottom-right version strings
     *
     * Stack layout for render(GuiGraphics gfx, int mouseX, int mouseY, float delta):
     *   ALOAD 0 = this (TitleScreen extends Screen)
     *   ALOAD 1 = guiGraphics (gir)
     *   ILOAD 2 = mouseX
     *   ILOAD 3 = mouseY
     *   FLOAD 4 = tickDelta
     */
    private static void injectTitleScreenBranding(MethodVisitor mv) {
        // 1. AlloyBranding.renderLogo(guiGraphics, font, width)
        //    Handles logo texture (or "ALLOY" text fallback) + "Forged with Alloy" tagline
        mv.visitVarInsn(Opcodes.ALOAD, 1);   // guiGraphics
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SCREEN_CLASS, FONT_FIELD,
                "L" + FONT_CLASS + ";");       // this.font
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SCREEN_CLASS, WIDTH_FIELD, "I");  // this.width
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRANDING_CLASS,
                "renderLogo", "(Ljava/lang/Object;Ljava/lang/Object;I)V", false);

        // 2. drawString(font, "AlloyMC 1.21.11", 2, height-10, 0xFF78716C) — bottom-left
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SCREEN_CLASS, FONT_FIELD,
                "L" + FONT_CLASS + ";");
        mv.visitLdcInsn("AlloyMC 1.21.11");
        mv.visitInsn(Opcodes.ICONST_2);       // x = 2
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SCREEN_CLASS, HEIGHT_FIELD, "I");
        mv.visitIntInsn(Opcodes.BIPUSH, 10);
        mv.visitInsn(Opcodes.ISUB);            // height - 10
        mv.visitLdcInsn(0xFF78716C);           // stone gray
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GUI_GRAPHICS_CLASS,
                DRAW_STRING, DRAW_STRING_DESC, false);

        // 3. drawString(font, "Alloy v0.1.0", width-72, height-10, 0xFF78716C) — bottom-right
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SCREEN_CLASS, FONT_FIELD,
                "L" + FONT_CLASS + ";");
        mv.visitLdcInsn("Alloy v0.1.0");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SCREEN_CLASS, WIDTH_FIELD, "I");
        mv.visitIntInsn(Opcodes.BIPUSH, 72);
        mv.visitInsn(Opcodes.ISUB);            // width - 72
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SCREEN_CLASS, HEIGHT_FIELD, "I");
        mv.visitIntInsn(Opcodes.BIPUSH, 10);
        mv.visitInsn(Opcodes.ISUB);            // height - 10
        mv.visitLdcInsn(0xFF78716C);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GUI_GRAPHICS_CLASS,
                DRAW_STRING, DRAW_STRING_DESC, false);
    }

    // ======================== 5. PauseScreen Branding ========================

    /**
     * Injects a subtle "Alloy v0.1.0" version string at the bottom-left of the pause screen.
     */
    private byte[] transformPauseScreen(byte[] classBytes, ClassLoader transformLoader) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if ("a".equals(name) && RENDER_DESC.equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    injectPauseScreenBranding(mv);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return mv;
                }
            };
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            System.out.println("[Alloy] Transformed: PauseScreen \u2014 render branding injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform PauseScreen: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Emits bytecode to draw "Alloy v0.1.0" at bottom-left of the pause screen.
     */
    private static void injectPauseScreenBranding(MethodVisitor mv) {
        // drawString(font, "Alloy v0.1.0", 2, height-10, 0x80FF6B00)
        mv.visitVarInsn(Opcodes.ALOAD, 1);  // guiGraphics
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SCREEN_CLASS, FONT_FIELD,
                "L" + FONT_CLASS + ";");
        mv.visitLdcInsn("Alloy v0.1.0");
        mv.visitInsn(Opcodes.ICONST_2);       // x = 2
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SCREEN_CLASS, HEIGHT_FIELD, "I");
        mv.visitIntInsn(Opcodes.BIPUSH, 10);
        mv.visitInsn(Opcodes.ISUB);            // height - 10
        mv.visitLdcInsn(0x80FF6B00);           // semi-transparent ember
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GUI_GRAPHICS_CLASS,
                DRAW_STRING, DRAW_STRING_DESC, false);
    }

    // ====================== 6. AbstractButton Styling =======================

    /**
     * Replaces AbstractButton.renderDefaultSprite with Alloy's glass orange/black
     * button background. Affects ALL buttons across all screens.
     */
    private byte[] transformAbstractButton(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // renderDefaultSprite: a(Lgir;)V — replace body with AlloyBranding call
                    if ("a".equals(name) && ("(L" + GUI_GRAPHICS_CLASS + ";)V").equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 1);  // guiGraphics
                                mv.visitVarInsn(Opcodes.ALOAD, 0);  // this (button)
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRANDING_CLASS,
                                        "renderButtonBackground",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                                mv.visitInsn(Opcodes.RETURN);
                            }

                            // Suppress all original bytecode
                            @Override public void visitInsn(int opcode) {}
                            @Override public void visitVarInsn(int opcode, int varIndex) {}
                            @Override public void visitFieldInsn(int opcode, String owner, String n, String desc) {}
                            @Override public void visitMethodInsn(int opcode, String owner, String n, String desc, boolean itf) {}
                            @Override public void visitTypeInsn(int opcode, String type) {}
                            @Override public void visitLdcInsn(Object value) {}
                            @Override public void visitJumpInsn(int opcode, Label label) {}
                            @Override public void visitLabel(Label label) {}
                            @Override public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {}
                            @Override public void visitIntInsn(int opcode, int operand) {}
                            @Override public void visitInvokeDynamicInsn(String n, String desc, Handle bsmHandle, Object... bsmArgs) {}
                            @Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {}
                            @Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {}
                            @Override public void visitMultiANewArrayInsn(String desc, int numDimensions) {}
                            @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {}
                            @Override public void visitLocalVariable(String n, String desc, String sig, Label start, Label end, int index) {}
                            @Override public void visitLineNumber(int line, Label start) {}

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                super.visitMaxs(2, 2);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: AbstractButton \u2014 glass orange/black button styling applied");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform AbstractButton: " + e.getMessage());
            return null;
        }
    }

    // ===================== 7. AbstractSliderButton Styling ====================

    /**
     * Replaces AbstractSliderButton.renderWidget with Alloy's glass slider styling.
     * Draws custom track + handle and re-renders the label text.
     */
    private byte[] transformAbstractSlider(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // renderWidget: a_(Lgir;IIF)V — replace body with AlloyBranding call
                    if ("a_".equals(name) && RENDER_DESC.equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 1);  // guiGraphics
                                mv.visitVarInsn(Opcodes.ALOAD, 0);  // this (slider)
                                mv.visitVarInsn(Opcodes.ILOAD, 2);  // mouseX
                                mv.visitVarInsn(Opcodes.ILOAD, 3);  // mouseY
                                mv.visitVarInsn(Opcodes.FLOAD, 4);  // delta
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRANDING_CLASS,
                                        "renderSliderWidget",
                                        "(Ljava/lang/Object;Ljava/lang/Object;IIF)V", false);
                                mv.visitInsn(Opcodes.RETURN);
                            }

                            // Suppress all original bytecode
                            @Override public void visitInsn(int opcode) {}
                            @Override public void visitVarInsn(int opcode, int varIndex) {}
                            @Override public void visitFieldInsn(int opcode, String owner, String n, String desc) {}
                            @Override public void visitMethodInsn(int opcode, String owner, String n, String desc, boolean itf) {}
                            @Override public void visitTypeInsn(int opcode, String type) {}
                            @Override public void visitLdcInsn(Object value) {}
                            @Override public void visitJumpInsn(int opcode, Label label) {}
                            @Override public void visitLabel(Label label) {}
                            @Override public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {}
                            @Override public void visitIntInsn(int opcode, int operand) {}
                            @Override public void visitInvokeDynamicInsn(String n, String desc, Handle bsmHandle, Object... bsmArgs) {}
                            @Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {}
                            @Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {}
                            @Override public void visitMultiANewArrayInsn(String desc, int numDimensions) {}
                            @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {}
                            @Override public void visitLocalVariable(String n, String desc, String sig, Label start, Label end, int index) {}
                            @Override public void visitLineNumber(int line, Label start) {}

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {
                                super.visitMaxs(5, 5);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: AbstractSliderButton \u2014 glass slider styling applied");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform AbstractSliderButton: " + e.getMessage());
            return null;
        }
    }

    // =================== 8. ServerGamePacketListenerImpl ====================

    /**
     * Transforms ServerGamePacketListenerImpl (ayi) to intercept commands, chat,
     * entity interactions, and player actions.
     *
     * <p>Hooks injected:
     * <ul>
     *   <li>performUnsignedChatCommand b(String)V — PlayerCommandEvent + command dispatch</li>
     *   <li>handleChat a(Laik;)V — PlayerChatEvent</li>
     *   <li>handlePlayerAction a(Laji;)V — PlayerInteractEvent (left-click)</li>
     *   <li>handleInteract a(Laiy;)V — EntityDamageByEntityEvent / PlayerInteractEntityEvent</li>
     * </ul>
     */
    private byte[] transformServerGameHandler(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // performUnsignedChatCommand: b(Ljava/lang/String;)V
                    // Fires PlayerCommandEvent, then tries Alloy command dispatch
                    if ("b".equals(name) && "(Ljava/lang/String;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();

                                // Wrap both hooks in try-catch(Throwable) so failures never crash MC
                                Label tryStart = new Label();
                                Label tryEnd = new Label();
                                Label catchHandler = new Label();
                                Label afterAll = new Label();

                                mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");

                                mv.visitLabel(tryStart);

                                // 1. Fire PlayerCommandEvent (cancellable)
                                Label notCancelled = new Label();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "firePlayerCommand",
                                        "(Ljava/lang/Object;Ljava/lang/String;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, notCancelled);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(notCancelled);

                                // 2. Try Alloy command dispatch
                                Label continueVanilla = new Label();
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        COMMAND_DISPATCH_HOOK_CLASS,
                                        "tryDispatch",
                                        "(Ljava/lang/Object;Ljava/lang/String;)Z",
                                        false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueVanilla);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(continueVanilla);

                                mv.visitLabel(tryEnd);
                                mv.visitJumpInsn(Opcodes.GOTO, afterAll);
                                mv.visitLabel(catchHandler);
                                mv.visitInsn(Opcodes.POP); // discard the Throwable
                                mv.visitLabel(afterAll);
                            }
                        };
                    }

                    // handleChat: a(Laik;)V — ServerboundChatPacket
                    if ("a".equals(name) && CHAT_PACKET_DESC.equals(descriptor)) {
                        return injectCancellableVoidHook(mv, "handleChatPacket");
                    }

                    // handlePlayerAction: a(Laji;)V — ServerboundPlayerActionPacket
                    if ("a".equals(name) && PLAYER_ACTION_PACKET_DESC.equals(descriptor)) {
                        return injectCancellableVoidHook(mv, "handlePlayerActionPacket");
                    }

                    // handleInteract: a(Laiy;)V — ServerboundInteractPacket
                    if ("a".equals(name) && INTERACT_PACKET_DESC.equals(descriptor)) {
                        return injectCancellableVoidHook(mv, "handleInteractPacket");
                    }

                    // handleMovePlayer: a(Lajb;)V — ServerboundMovePlayerPacket
                    if ("a".equals(name) && MOVE_PACKET_DESC.equals(descriptor)) {
                        return injectCancellableVoidHook(mv, "handleMovePacket");
                    }

                    // handleSetCarriedItem: a(Lajt;)V — ServerboundSetCarriedItemPacket
                    if ("a".equals(name) && SET_CARRIED_ITEM_PACKET_DESC.equals(descriptor)) {
                        return injectCancellableVoidHook(mv, "handleSetCarriedItemPacket");
                    }

                    // handleSignUpdate: a(Laka;)V — ServerboundSignUpdatePacket
                    if ("a".equals(name) && SIGN_UPDATE_PACKET_DESC.equals(descriptor)) {
                        return injectCancellableVoidHook(mv, "handleSignUpdatePacket");
                    }

                    // NOTE: UseItemOn packet hook removed — PlayerInteractEvent is fired
                    // from ServerPlayerGameMode.useItemOn (checkUseItemOn) instead,
                    // which has proper InteractionResult cancellation support.

                    // handleContainerClick: a(Lais;)V — ServerboundContainerClickPacket
                    if ("a".equals(name) && CONTAINER_CLICK_PACKET_DESC.equals(descriptor)) {
                        return injectCancellableVoidHook(mv, "handleContainerClickPacket");
                    }

                    // handleContainerClose: a(Lait;)V — ServerboundContainerClosePacket
                    if ("a".equals(name) && CONTAINER_CLOSE_PACKET_DESC.equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                emitSafeVoidCall(mv, EVENT_HOOK_CLASS, "fireContainerClose",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                        () -> {
                                            mv.visitVarInsn(Opcodes.ALOAD, 0); // this (handler)
                                            mv.visitVarInsn(Opcodes.ALOAD, 1); // packet
                                        });
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            System.out.println("[Alloy] Transformed: ServerGamePacketListenerImpl \u2014 command, chat, action, interact, container click/close hooks injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform ServerGamePacketListenerImpl: " + e.getMessage());
            return null;
        }
    }

    /**
     * Transforms PlayerList (bbz) to fire join/quit events.
     *
     * <ul>
     *   <li>placeNewPlayer a(Lwu;Laxg;Laxu;)V — firePlayerJoin at method end</li>
     *   <li>remove b(Laxg;)V — firePlayerQuit at method start</li>
     * </ul>
     */
    private byte[] transformPlayerList(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // placeNewPlayer: a(Lwu;Laxg;Laxu;)V
                    // - START: verify Alloy handshake (safety net)
                    // - END: fire PlayerJoinEvent
                    if ("a".equals(name) && "(Lwu;Laxg;Laxu;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // if (AlloyHandshakeHook.verifyOnJoin(connection, serverPlayer)) return;
                                Label tryStart = new Label();
                                Label tryEnd = new Label();
                                Label catchHandler = new Label();
                                Label continueLabel = new Label();

                                mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");

                                mv.visitLabel(tryStart);
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // connection (wu)
                                mv.visitVarInsn(Opcodes.ALOAD, 2); // serverPlayer (axg)
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, HANDSHAKE_HOOK_CLASS,
                                        "verifyOnJoin",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(tryEnd);
                                mv.visitJumpInsn(Opcodes.GOTO, continueLabel);

                                mv.visitLabel(catchHandler);
                                mv.visitInsn(Opcodes.POP); // discard the Throwable
                                mv.visitLabel(continueLabel);
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.RETURN) {
                                    emitSafeVoidCall(mv, EVENT_HOOK_CLASS,
                                            "firePlayerJoin", "(Ljava/lang/Object;)V",
                                            () -> mv.visitVarInsn(Opcodes.ALOAD, 2));
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    // remove: b(Laxg;)V — fire quit at start
                    if ("b".equals(name) && "(Laxg;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                emitSafeVoidCall(mv, EVENT_HOOK_CLASS,
                                        "firePlayerQuit", "(Ljava/lang/Object;)V",
                                        () -> mv.visitVarInsn(Opcodes.ALOAD, 1));
                            }
                        };
                    }

                    // respawn: a(Laxg;ZLcgk$e;)Laxg; — fire PlayerRespawnEvent after return
                    // NOTE: Cannot use emitSafeVoidCall (try-catch) before ARETURN with DUP
                    // because the catch path clears the stack, creating conflicting frames.
                    // Direct call is safe — EventFiringHook catches exceptions internally.
                    if ("a".equals(name) && descriptor.startsWith("(Laxg;Z")
                            && descriptor.endsWith(")Laxg;")) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == Opcodes.ARETURN) {
                                    mv.visitInsn(Opcodes.DUP);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                            "firePlayerRespawn", "(Ljava/lang/Object;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            System.out.println("[Alloy] Transformed: PlayerList \u2014 join/quit event hooks injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform PlayerList: " + e.getMessage());
            return null;
        }
    }

    /**
     * Transforms ServerPlayerGameMode (axh) to fire block break and use item events.
     *
     * <ul>
     *   <li>destroyBlock a(Lis;)Z — fireBlockBreak, return false if cancelled</li>
     *   <li>useItemOn a(Laxg;Ldwo;Ldlt;Lcdb;Lfti;)Lcdc; — checkUseItemOn, return FAIL if cancelled</li>
     * </ul>
     */
    private byte[] transformServerPlayerGameMode(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // destroyBlock: a(Lis;)Z — returns boolean
                    if ("a".equals(name) && "(Lis;)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();

                                // if (EventFiringHook.fireBlockBreak(this, blockPos)) return false;
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (gameMode)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // blockPos
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireBlockBreak",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.ICONST_0); // false
                                mv.visitInsn(Opcodes.IRETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    // useItemOn: a(Laxg;Ldwo;Ldlt;Lcdb;Lfti;)Lcdc;
                    if ("a".equals(name) && "(Laxg;Ldwo;Ldlt;Lcdb;Lfti;)Lcdc;".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();

                                // Object result = EventFiringHook.checkUseItemOn(gameMode, player, level, item, hand, hit)
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (gameMode)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // serverPlayer
                                mv.visitVarInsn(Opcodes.ALOAD, 2); // level
                                mv.visitVarInsn(Opcodes.ALOAD, 3); // itemStack
                                mv.visitVarInsn(Opcodes.ALOAD, 4); // hand
                                mv.visitVarInsn(Opcodes.ALOAD, 5); // hitResult
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "checkUseItemOn",
                                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
                                                + "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)"
                                                + "Ljava/lang/Object;",
                                        false);
                                mv.visitInsn(Opcodes.DUP);
                                mv.visitJumpInsn(Opcodes.IFNULL, continueLabel);
                                // Non-null = cancelled, cast and return InteractionResult
                                mv.visitTypeInsn(Opcodes.CHECKCAST, INTERACTION_RESULT_CLASS);
                                mv.visitInsn(Opcodes.ARETURN);
                                mv.visitLabel(continueLabel);
                                mv.visitInsn(Opcodes.POP); // pop the null
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            System.out.println("[Alloy] Transformed: ServerPlayerGameMode \u2014 block break + use item hooks injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform ServerPlayerGameMode: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a MethodVisitor that injects a cancellable hook at the start of a void method.
     * Pattern: if (EventFiringHook.{hookMethod}(this, arg1)) return;
     *
     * @param delegate the original method visitor
     * @param hookMethod the EventFiringHook method name to call
     * @return a wrapping method visitor
     */
    private static MethodVisitor injectCancellableVoidHook(MethodVisitor delegate, String hookMethod) {
        return new MethodVisitor(Opcodes.ASM9, delegate) {
            @Override
            public void visitCode() {
                super.visitCode();

                // Wrap in try-catch(Throwable) so hook failures never crash Minecraft
                Label tryStart = new Label();
                Label tryEnd = new Label();
                Label catchHandler = new Label();
                Label continueLabel = new Label();

                delegate.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");

                delegate.visitLabel(tryStart);
                delegate.visitVarInsn(Opcodes.ALOAD, 0); // this (handler)
                delegate.visitVarInsn(Opcodes.ALOAD, 1); // packet argument
                delegate.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                        hookMethod,
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                delegate.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                delegate.visitInsn(Opcodes.RETURN);
                delegate.visitLabel(tryEnd);
                delegate.visitJumpInsn(Opcodes.GOTO, continueLabel);

                delegate.visitLabel(catchHandler);
                delegate.visitInsn(Opcodes.POP); // discard the Throwable
                delegate.visitLabel(continueLabel);
            }
        };
    }

    /**
     * Transforms MinecraftServer to capture the server instance and hook the tick loop.
     *
     * <ul>
     *   <li>runServer A()V — calls onServerReady(this) just before the "Done" message</li>
     *   <li>tickServer a(BooleanSupplier)V — calls onServerTick(this) at method start</li>
     * </ul>
     */
    private byte[] transformMinecraftServer(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            // Pass the ClassReader to ClassWriter so unmodified methods are copied
            // byte-for-byte (preserving their original stack map frames intact).
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // runServer: A()V — inject onServerReady(this) + onServerStopping(this)
                    if ("A".equals(name) && "()V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            private boolean injected = false;

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                if (!injected) {
                                    injected = true;
                                    // try { onServerReady(this); } catch (Throwable t) { /* ignore */ }
                                    Label tryStart = new Label();
                                    Label tryEnd = new Label();
                                    Label catchHandler = new Label();
                                    Label after = new Label();
                                    mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");
                                    mv.visitLabel(tryStart);
                                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                            "onServerReady",
                                            "(Ljava/lang/Object;)V", false);
                                    mv.visitLabel(tryEnd);
                                    mv.visitJumpInsn(Opcodes.GOTO, after);
                                    mv.visitLabel(catchHandler);
                                    mv.visitInsn(Opcodes.POP);
                                    mv.visitLabel(after);
                                }
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                // Before every RETURN, call onServerStopping() to clean up.
                                // Skip ATHROW — injecting a try-catch before it corrupts the
                                // stack map frames (the original Throwable on the stack conflicts
                                // with the catch handler's empty-stack path).
                                if (opcode == Opcodes.RETURN) {
                                    Label ts = new Label();
                                    Label te = new Label();
                                    Label ch = new Label();
                                    Label af = new Label();
                                    mv.visitTryCatchBlock(ts, te, ch, "java/lang/Throwable");
                                    mv.visitLabel(ts);
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                            "onServerStopping", "()V", false);
                                    mv.visitLabel(te);
                                    mv.visitJumpInsn(Opcodes.GOTO, af);
                                    mv.visitLabel(ch);
                                    mv.visitInsn(Opcodes.POP);
                                    mv.visitLabel(af);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    // tickServer: a(Ljava/util/function/BooleanSupplier;)V — inject tick at start
                    if ("a".equals(name) && "(Ljava/util/function/BooleanSupplier;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // try { onServerTick(this); } catch (Throwable t) { /* ignore */ }
                                Label tryStart = new Label();
                                Label tryEnd = new Label();
                                Label catchHandler = new Label();
                                Label after = new Label();
                                mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");
                                mv.visitLabel(tryStart);
                                mv.visitVarInsn(Opcodes.ALOAD, 0);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "onServerTick",
                                        "(Ljava/lang/Object;)V", false);
                                mv.visitLabel(tryEnd);
                                mv.visitJumpInsn(Opcodes.GOTO, after);
                                mv.visitLabel(catchHandler);
                                mv.visitInsn(Opcodes.POP);
                                mv.visitLabel(after);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0); // preserve original frames
            System.out.println("[Alloy] Transformed: MinecraftServer \u2014 server capture + tick hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform MinecraftServer: " + e.getMessage());
            return null;
        }
    }

    // ==================== 12. ServerPlayer (die hook) =======================

    /**
     * Transforms ServerPlayer (axg) to fire PlayerDeathEvent.
     *
     * <ul>
     *   <li>die a(Lcex;)V — fires firePlayerDeath(this, damageSource) at method start</li>
     * </ul>
     */
    private byte[] transformServerPlayer(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // die: a(Lcex;)V — DamageSource parameter
                    if ("a".equals(name) && "(Lcex;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // EventFiringHook.firePlayerDeath(this, damageSource)
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (ServerPlayer)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // damageSource
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "firePlayerDeath",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                            }
                        };
                    }

                    // drop: a(Ldlt;ZZ)Lczl; — ItemStack, boolean, boolean → ItemEntity
                    if ("a".equals(name) && "(Ldlt;ZZ)Lczl;".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // if (EventFiringHook.firePlayerDrop(this, itemStack)) return null;
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (ServerPlayer)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // itemStack
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "firePlayerDrop",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.ACONST_NULL); // return null
                                mv.visitInsn(Opcodes.ARETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    // teleportTo: a(Laxf;DDDLjava/util/Set;FFZ)Z — fires PlayerTeleportEvent
                    if ("a".equals(name) && "(Laxf;DDDLjava/util/Set;FFZ)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // EventFiringHook.firePlayerTeleport(this, destLevel, x, y, z, yaw, pitch)
                                mv.visitVarInsn(Opcodes.ALOAD, 0);  // this
                                mv.visitVarInsn(Opcodes.ALOAD, 1);  // destLevel
                                mv.visitVarInsn(Opcodes.DLOAD, 2);  // x (double)
                                mv.visitVarInsn(Opcodes.DLOAD, 4);  // y (double)
                                mv.visitVarInsn(Opcodes.DLOAD, 6);  // z (double)
                                mv.visitVarInsn(Opcodes.FLOAD, 9);  // yaw (float) — after Set at slot 8
                                mv.visitVarInsn(Opcodes.FLOAD, 10); // pitch (float)
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "firePlayerTeleport",
                                        "(Ljava/lang/Object;Ljava/lang/Object;DDDFF)V", false);
                            }
                        };
                    }

                    // onItemPickup: a(Lczl;)V — ItemEntity parameter
                    if ("a".equals(name) && "(Lczl;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // if (EventFiringHook.fireEntityPickupItem(this, itemEntity)) return;
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // itemEntity
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireEntityPickupItem",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0); // preserve original frames
            System.out.println("[Alloy] Transformed: ServerPlayer \u2014 death event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform ServerPlayer: " + e.getMessage());
            return null;
        }
    }

    // =================== 13. ServerLevel (entity spawn) ====================

    /**
     * Transforms ServerLevel (axf) to fire EntitySpawnEvent.
     *
     * <ul>
     *   <li>addFreshEntity b(Lcgk;)Z — fires fireEntitySpawn, returns false if cancelled</li>
     * </ul>
     */
    private byte[] transformServerLevel(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // addFreshEntity: b(Lcgk;)Z — Entity parameter, returns boolean
                    if ("b".equals(name) && "(Lcgk;)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();

                                // if (EventFiringHook.fireEntitySpawn(this, entity)) return false;
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (ServerLevel)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // entity
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireEntitySpawn",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.ICONST_0); // false
                                mv.visitInsn(Opcodes.IRETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0); // preserve original frames
            System.out.println("[Alloy] Transformed: ServerLevel \u2014 entity spawn event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform ServerLevel: " + e.getMessage());
            return null;
        }
    }

    // =================== 14. LivingEntity (damage hook) ====================

    /**
     * Transforms LivingEntity (chl) to fire EntityDamageEvent.
     *
     * <ul>
     *   <li>hurtServer a(Laxf;Lcex;F)Z — fires fireEntityDamage, returns false if cancelled</li>
     * </ul>
     */
    private byte[] transformLivingEntity(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // die: a(Lcex;)V — DamageSource parameter → fires EntityDeathEvent
                    if ("a".equals(name) && "(Lcex;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // EventFiringHook.fireEntityDeath(this, damageSource)
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (LivingEntity)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // damageSource
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireEntityDeath",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                            }
                        };
                    }

                    // hurtServer: a(Laxf;Lcex;F)Z — ServerLevel, DamageSource, float → boolean
                    if ("a".equals(name) && "(Laxf;Lcex;F)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();

                                // if (EventFiringHook.fireEntityDamage(this, serverLevel, damageSource, amount)) return false;
                                mv.visitVarInsn(Opcodes.ALOAD, 0);  // this (LivingEntity)
                                mv.visitVarInsn(Opcodes.ALOAD, 1);  // serverLevel
                                mv.visitVarInsn(Opcodes.ALOAD, 2);  // damageSource
                                mv.visitVarInsn(Opcodes.FLOAD, 3);  // amount (float)
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireEntityDamage",
                                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;F)Z",
                                        false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.ICONST_0); // false
                                mv.visitInsn(Opcodes.IRETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0); // preserve original frames
            System.out.println("[Alloy] Transformed: LivingEntity \u2014 damage event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform LivingEntity: " + e.getMessage());
            return null;
        }
    }

    // =================== 15. BlockItem (block place) =========================

    /**
     * Transforms BlockItem (dkb) to fire BlockPlaceEvent.
     *
     * <ul>
     *   <li>place a(Ldpu;)Lcdc; — BlockPlaceContext → InteractionResult, returns FAIL if cancelled</li>
     * </ul>
     */
    private byte[] transformBlockItem(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // place: a(Ldpu;)Lcdc; — BlockPlaceContext → InteractionResult
                    if ("a".equals(name) && "(Ldpu;)Lcdc;".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // Object result = EventFiringHook.fireBlockPlace(this, blockPlaceContext)
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (BlockItem)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // blockPlaceContext
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireBlockPlace",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                                        false);
                                mv.visitInsn(Opcodes.DUP);
                                mv.visitJumpInsn(Opcodes.IFNULL, continueLabel);
                                mv.visitTypeInsn(Opcodes.CHECKCAST, INTERACTION_RESULT_CLASS);
                                mv.visitInsn(Opcodes.ARETURN);
                                mv.visitLabel(continueLabel);
                                mv.visitInsn(Opcodes.POP); // pop the null
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: BlockItem \u2014 block place event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform BlockItem: " + e.getMessage());
            return null;
        }
    }

    // ================== 16. BucketItem (bucket use) ========================

    /**
     * Transforms BucketItem (dkh) to fire PlayerBucketEvent.
     *
     * <ul>
     *   <li>use a(Ldwo;Lddm;Lcdb;)Lcdc; — Level, Player, InteractionHand → InteractionResult</li>
     * </ul>
     */
    private byte[] transformBucketItem(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // use: a(Ldwo;Lddm;Lcdb;)Lcdc;
                    if ("a".equals(name) && "(Ldwo;Lddm;Lcdb;)Lcdc;".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // Object result = firePlayerBucket(level, player, hand)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // level
                                mv.visitVarInsn(Opcodes.ALOAD, 2); // player
                                mv.visitVarInsn(Opcodes.ALOAD, 3); // hand
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "firePlayerBucket",
                                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                                        false);
                                mv.visitInsn(Opcodes.DUP);
                                mv.visitJumpInsn(Opcodes.IFNULL, continueLabel);
                                mv.visitTypeInsn(Opcodes.CHECKCAST, INTERACTION_RESULT_CLASS);
                                mv.visitInsn(Opcodes.ARETURN);
                                mv.visitLabel(continueLabel);
                                mv.visitInsn(Opcodes.POP);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: BucketItem \u2014 bucket event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform BucketItem: " + e.getMessage());
            return null;
        }
    }

    // ============= 17. ServerExplosion (explosion events) ==================

    /**
     * Transforms ServerExplosion (dxe) to fire EntityExplodeEvent / BlockExplodeEvent.
     *
     * <ul>
     *   <li>interactWithBlocks a(Ljava/util/List;)V — fires before blocks are destroyed</li>
     * </ul>
     */
    private byte[] transformServerExplosion(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // interactWithBlocks: a(Ljava/util/List;)V
                    if ("a".equals(name) && "(Ljava/util/List;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // if (EventFiringHook.fireExplosionBlocks(this, blockList)) return;
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (ServerExplosion)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // blockPositions list
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireExplosionBlocks",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: ServerExplosion \u2014 explosion event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform ServerExplosion: " + e.getMessage());
            return null;
        }
    }

    // ==================== 18. Mob (entity target) ==========================

    /**
     * Transforms Mob (chn) to fire EntityTargetEvent.
     *
     * <ul>
     *   <li>setTarget g(Lchl;)V — LivingEntity parameter, cancellable</li>
     * </ul>
     */
    private byte[] transformMob(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // setTarget: g(Lchl;)V
                    if ("g".equals(name) && "(Lchl;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // if (EventFiringHook.fireEntityTarget(this, target)) return;
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (Mob)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // target (LivingEntity)
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireEntityTarget",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: Mob \u2014 entity target event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform Mob: " + e.getMessage());
            return null;
        }
    }

    // ================= 19. FlowingFluid (liquid spread) ====================

    /**
     * Transforms FlowingFluid (fkz) to fire BlockFromToEvent.
     *
     * <ul>
     *   <li>spreadTo a(Ldwp;Lis;Leoh;Liz;Lflb;)V — cancellable, prevents liquid spread</li>
     * </ul>
     */
    private byte[] transformFlowingFluid(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // spreadTo: a(Ldwp;Lis;Leoh;Liz;Lflb;)V
                    if ("a".equals(name) && "(Ldwp;Lis;Leoh;Liz;Lflb;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // if (EventFiringHook.fireLiquidSpread(levelAccessor, blockPos)) return;
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // levelAccessor
                                mv.visitVarInsn(Opcodes.ALOAD, 2); // toBlockPos
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireLiquidSpread",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: FlowingFluid \u2014 liquid spread event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform FlowingFluid: " + e.getMessage());
            return null;
        }
    }

    // =================== 20. FireBlock (fire events) =======================

    /**
     * Transforms FireBlock (ecv) to fire BlockIgniteEvent and BlockBurnEvent.
     *
     * <ul>
     *   <li>tick a(Leoh;Laxf;Lis;Lbgr;)V — fires BlockIgniteEvent at start</li>
     *   <li>checkBurnOut a(Ldwo;Lis;ILbgr;I)V — fires BlockBurnEvent at start</li>
     * </ul>
     */
    private byte[] transformFireBlock(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // tick: a(Leoh;Laxf;Lis;Lbgr;)V — BlockState, ServerLevel, BlockPos, RandomSource
                    if ("a".equals(name) && "(Leoh;Laxf;Lis;Lbgr;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // if (EventFiringHook.fireFireTick(serverLevel, blockPos)) return;
                                mv.visitVarInsn(Opcodes.ALOAD, 2); // serverLevel
                                mv.visitVarInsn(Opcodes.ALOAD, 3); // blockPos
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireFireTick",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    // checkBurnOut: a(Ldwo;Lis;ILbgr;I)V — Level, BlockPos, int, RandomSource, int
                    if ("a".equals(name) && "(Ldwo;Lis;ILbgr;I)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // if (EventFiringHook.fireBlockBurn(level, blockPos)) return;
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // level
                                mv.visitVarInsn(Opcodes.ALOAD, 2); // blockPos
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireBlockBurn",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: FireBlock \u2014 fire ignite/burn event hooks injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform FireBlock: " + e.getMessage());
            return null;
        }
    }

    // ================ 21. PistonBaseBlock (piston events) ==================

    /**
     * Transforms PistonBaseBlock (eny) to fire BlockPistonEvent.
     *
     * <ul>
     *   <li>moveBlocks a(Ldwo;Lis;Liz;Z)Z — Level, BlockPos, Direction, extending → boolean</li>
     * </ul>
     */
    private byte[] transformPistonBaseBlock(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // moveBlocks: a(Ldwo;Lis;Liz;Z)Z — Level, BlockPos, Direction, boolean → boolean
                    if ("a".equals(name) && "(Ldwo;Lis;Liz;Z)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // if (EventFiringHook.firePistonMove(level, blockPos, extending)) return false;
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // level
                                mv.visitVarInsn(Opcodes.ALOAD, 2); // blockPos
                                mv.visitVarInsn(Opcodes.ILOAD, 4); // extending (boolean)
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "firePistonMove",
                                        "(Ljava/lang/Object;Ljava/lang/Object;Z)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.ICONST_0); // return false
                                mv.visitInsn(Opcodes.IRETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: PistonBaseBlock \u2014 piston event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform PistonBaseBlock: " + e.getMessage());
            return null;
        }
    }

    // ================ 22. DispenserBlock (dispense events) ==================

    /**
     * Transforms DispenserBlock (eby) to fire BlockDispenseEvent.
     *
     * <ul>
     *   <li>dispenseFrom a(Laxf;Leoh;Lis;)V — ServerLevel, BlockState, BlockPos</li>
     * </ul>
     */
    private byte[] transformDispenserBlock(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // dispenseFrom: a(Laxf;Leoh;Lis;)V
                    if ("a".equals(name) && "(Laxf;Leoh;Lis;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // serverLevel
                                mv.visitVarInsn(Opcodes.ALOAD, 2); // blockState
                                mv.visitVarInsn(Opcodes.ALOAD, 3); // blockPos
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireDispense",
                                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",
                                        false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: DispenserBlock \u2014 dispense event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform DispenserBlock: " + e.getMessage());
            return null;
        }
    }

    // ================= 23. TreeGrower (structure grow) =====================

    /**
     * Transforms TreeGrower (enu) to fire StructureGrowEvent.
     *
     * <ul>
     *   <li>growTree a(Laxf;Leqg;Lis;Leoh;Lbgr;)Z — cancellable, prevents tree growth</li>
     * </ul>
     */
    private byte[] transformTreeGrower(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // growTree: a(Laxf;Leqg;Lis;Leoh;Lbgr;)Z
                    if ("a".equals(name) && "(Laxf;Leqg;Lis;Leoh;Lbgr;)Z".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                Label continueLabel = new Label();
                                // if (EventFiringHook.fireTreeGrow(serverLevel, blockPos)) return false;
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // serverLevel
                                mv.visitVarInsn(Opcodes.ALOAD, 3); // blockPos
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "fireTreeGrow",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                mv.visitInsn(Opcodes.ICONST_0); // return false
                                mv.visitInsn(Opcodes.IRETURN);
                                mv.visitLabel(continueLabel);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: TreeGrower \u2014 structure grow event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform TreeGrower: " + e.getMessage());
            return null;
        }
    }

    // ============= 24. ThrownSplashPotion (potion splash) =================

    /**
     * Transforms ThrownSplashPotion (dff) to fire PotionSplashEvent.
     *
     * <ul>
     *   <li>onHitAsPotion a(Laxf;Ldlt;Lftk;)V — fires before splash effects are applied</li>
     * </ul>
     */
    private byte[] transformThrownSplashPotion(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // onHitAsPotion: a(Laxf;Ldlt;Lftk;)V
                    if ("a".equals(name) && "(Laxf;Ldlt;Lftk;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // EventFiringHook.firePotionSplash(this)
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (ThrownSplashPotion)
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, EVENT_HOOK_CLASS,
                                        "firePotionSplash",
                                        "(Ljava/lang/Object;)V", false);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, 0);
            System.out.println("[Alloy] Transformed: ThrownSplashPotion \u2014 potion splash event hook injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform ThrownSplashPotion: " + e.getMessage());
            return null;
        }
    }

    // ============= Handshake: ServerHandshakePacketListenerImpl =============

    /**
     * Transforms ServerHandshakePacketListenerImpl (ayj) to intercept the initial
     * handshake packet and check for the Alloy address marker.
     *
     * <p>Hook: handleIntention a(Lakj;)V — calls AlloyHandshakeHook.onHandshakeReceived(this, packet)
     * at method start. The hook parses the address field, stores verified connections,
     * and strips the marker so vanilla MC doesn't see it.
     */
    private byte[] transformHandshakeHandler(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // handleIntention: a(Lakj;)V — ClientIntentionPacket parameter
                    if ("a".equals(name) && "(Lakj;)V".equals(descriptor)) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                // try { AlloyHandshakeHook.onHandshakeReceived(this, packet); }
                                // catch (Throwable t) { /* ignore */ }
                                Label tryStart = new Label();
                                Label tryEnd = new Label();
                                Label catchHandler = new Label();
                                Label after = new Label();

                                mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");
                                mv.visitLabel(tryStart);
                                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (ServerHandshakePacketListenerImpl)
                                mv.visitVarInsn(Opcodes.ALOAD, 1); // packet (ClientIntentionPacket)
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, HANDSHAKE_HOOK_CLASS,
                                        "onHandshakeReceived",
                                        "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                                mv.visitLabel(tryEnd);
                                mv.visitJumpInsn(Opcodes.GOTO, after);
                                mv.visitLabel(catchHandler);
                                mv.visitInsn(Opcodes.POP); // discard Throwable
                                mv.visitLabel(after);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            System.out.println("[Alloy] Transformed: ServerHandshakePacketListenerImpl \u2014 handshake marker check injected");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform ServerHandshakePacketListenerImpl: " + e.getMessage());
            return null;
        }
    }

    // ============= Handshake: ClientIntentionPacket (client-side) ===========

    /**
     * Transforms ClientIntentionPacket (akj) on the client side to append the Alloy
     * address marker to the hostName field.
     *
     * <p>ClientIntentionPacket is a record with fields:
     * <pre>
     *   b = protocolVersion (int)
     *   c = hostName (String)
     *   d = port (int)
     *   e = intention (ClientIntent)
     * </pre>
     *
     * <p>The hostName accessor e() is used at the server side, but the field 'c' is
     * what gets serialized into the packet via STREAM_CODEC. We modify the field value
     * in the constructor (the record's canonical constructor) so all reads see the marker.
     *
     * <p>Strategy: intercept the constructor. After the super() call and field assignments,
     * replace field 'c' with AlloyHandshakeHook.addMarkerToAddress(c).
     */
    private byte[] transformClientIntentionPacket(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = createFrameWriter(reader);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                    // Canonical constructor: <init>(ILjava/lang/String;ILClientIntent;)V
                    // The exact descriptor includes the obfuscated ClientIntent type.
                    // Match any constructor that takes (int, String, int, ...) pattern.
                    if ("<init>".equals(name) && descriptor.contains("Ljava/lang/String;")) {
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitInsn(int opcode) {
                                // Before the constructor RETURN, modify the hostName field
                                if (opcode == Opcodes.RETURN) {
                                    // try { this.c = AlloyHandshakeHook.addMarkerToAddress(this.c); }
                                    // catch (Throwable t) { /* ignore — use original address */ }
                                    Label tryStart = new Label();
                                    Label tryEnd = new Label();
                                    Label catchHandler = new Label();
                                    Label after = new Label();

                                    mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");
                                    mv.visitLabel(tryStart);

                                    mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                                    mv.visitVarInsn(Opcodes.ALOAD, 0); // this (for getfield)
                                    mv.visitFieldInsn(Opcodes.GETFIELD, CLIENT_INTENTION_PACKET_CLASS,
                                            "c", "Ljava/lang/String;"); // this.c (hostName)
                                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, HANDSHAKE_HOOK_CLASS,
                                            "addMarkerToAddress",
                                            "(Ljava/lang/String;)Ljava/lang/String;", false);
                                    mv.visitFieldInsn(Opcodes.PUTFIELD, CLIENT_INTENTION_PACKET_CLASS,
                                            "c", "Ljava/lang/String;"); // this.c = modified

                                    mv.visitLabel(tryEnd);
                                    mv.visitJumpInsn(Opcodes.GOTO, after);
                                    mv.visitLabel(catchHandler);
                                    mv.visitInsn(Opcodes.POP); // discard Throwable
                                    mv.visitLabel(after);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }

                    return mv;
                }
            };
            reader.accept(visitor, ClassReader.SKIP_FRAMES);
            System.out.println("[Alloy] Transformed: ClientIntentionPacket \u2014 Alloy address marker injection applied");
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to transform ClientIntentionPacket: " + e.getMessage());
            return null;
        }
    }

    // ============================== Utilities ================================

    /**
     * Emits a void static call wrapped in try-catch(Throwable) at the bytecode level.
     * If the call throws, the exception is silently discarded so Minecraft is never crashed
     * by a hook failure.
     *
     * @param mv         the method visitor to emit into
     * @param owner      internal class name of the target (e.g. "net/alloymc/loader/agent/EventFiringHook")
     * @param methodName the static method name to call
     * @param descriptor the method descriptor (must return void)
     * @param pushArgs   a Runnable that emits the bytecode to push all arguments onto the stack
     */
    private static void emitSafeVoidCall(MethodVisitor mv, String owner,
                                          String methodName, String descriptor,
                                          Runnable pushArgs) {
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchHandler = new Label();
        Label after = new Label();

        mv.visitTryCatchBlock(tryStart, tryEnd, catchHandler, "java/lang/Throwable");
        mv.visitLabel(tryStart);
        pushArgs.run();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, methodName, descriptor, false);
        mv.visitLabel(tryEnd);
        mv.visitJumpInsn(Opcodes.GOTO, after);
        mv.visitLabel(catchHandler);
        mv.visitInsn(Opcodes.POP); // discard the Throwable
        mv.visitLabel(after);
    }

    /**
     * Returns a {@link MethodVisitor} that replaces the entire method body with
     * a single RETURN instruction. Used for no-op transforms.
     */
    private static MethodVisitor replaceWithReturn(MethodVisitor delegate) {
        return new MethodVisitor(Opcodes.ASM9, delegate) {
            @Override
            public void visitCode() {
                super.visitCode();
                delegate.visitInsn(Opcodes.RETURN);
            }

            // Drop all original bytecode
            @Override public void visitInsn(int opcode) {}
            @Override public void visitVarInsn(int opcode, int varIndex) {}
            @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {}
            @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {}
            @Override public void visitTypeInsn(int opcode, String type) {}
            @Override public void visitLdcInsn(Object value) {}
            @Override public void visitJumpInsn(int opcode, Label label) {}
            @Override public void visitLabel(Label label) {}
            @Override public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {}
            @Override public void visitIntInsn(int opcode, int operand) {}
            @Override public void visitInvokeDynamicInsn(String name, String desc, Handle bsmHandle, Object... bsmArgs) {}
            @Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {}
            @Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {}
            @Override public void visitMultiANewArrayInsn(String desc, int numDimensions) {}
            @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {}
            @Override public void visitLocalVariable(String name, String desc, String sig, Label start, Label end, int index) {}
            @Override public void visitLineNumber(int line, Label start) {}

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                super.visitMaxs(1, maxLocalsForDesc());
            }

            private int maxLocalsForDesc() {
                // Instance methods need at least 'this' + parameters
                return 5; // generous for any of our target signatures
            }
        };
    }

    /**
     * Creates a ClassWriter with COMPUTE_FRAMES and safe getCommonSuperClass fallback.
     */
    private static ClassWriter createFrameWriter(ClassReader reader) {
        return new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                // NEVER use Class.forName here — it triggers recursive class loading
                // through the javaagent transformer, which causes a native SIGSEGV in
                // libzip on macOS ARM64 (_platform_memmove crash). This is fatal and
                // uncatchable. Returning "java/lang/Object" is safe: the ClassReader
                // passed to the constructor provides existing frame data, and ASM only
                // calls this for methods we actually modify. Our injected code uses
                // simple if-return patterns that don't create type-merge points requiring
                // precise superclass resolution.
                return "java/lang/Object";
            }
        };
    }
}
