package com.cardinalstar.cubicchunks.api.worldgen.hwaccel;

import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT;

import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLVideo;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.util.JavaUtils;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lombok.Getter;

public class KernelContext {

    @Getter
    private static boolean enabled;

    private static Thread worker;

    @Getter
    private static KernelScheduler scheduler;

    @Getter
    private static int SSBOAlignment;

    @SideOnly(Side.CLIENT)
    public static void initClient() {
        if (!isContextValid()) {
            CubicChunks.LOGGER.info("Render context is invalid, disabling compute shaders");

            return;
        }

        CubicChunks.LOGGER.info("Render context is valid, using it as the compute context");

        SSBOAlignment = glGetInteger(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);

        enabled = true;

        scheduler = new KernelScheduler();

        FMLCommonHandler.instance().bus().register(new ClientEventHandler());
    }

    public static class ClientEventHandler {

        @SubscribeEvent
        public void tick(RenderTickEvent event) {
            if (event.phase != Phase.START) return;

            scheduler.tick();
        }
    }

    @SideOnly(Side.SERVER)
    public static void initServer() {
        worker = new Thread(() -> {
            SDLHints.SDL_SetHint("SDL_VIDEODRIVER", "offscreen");
            SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO);

            long window = SDLVideo.SDL_CreateWindow("", 1, 1, SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN);
            long context = SDLVideo.SDL_GL_CreateContext(window);
            SDLVideo.SDL_GL_MakeCurrent(window, context);

//            GL.create(SDLVideo::SDL_GL_GetProcAddress);
//            GL.createCapabilities();

            if (!isContextValid()) {
                CubicChunks.LOGGER.info("Server offscreen context does not support compute shaders: disabling");
                return;
            }

            CubicChunks.LOGGER.info("Successfully created server offscreen context that supports OpenGL compute shaders");

            enabled = true;

            scheduler = new KernelScheduler();

            try {
                while (true) {
                    scheduler.tick();

                    Thread.yield();
                    JavaUtils.onSpinWait(); // TODO: blocking
                }
            } catch (Throwable t) {
                CubicChunks.LOGGER.error("CC-WG-Dispatcher failed with the following error", t);
            }

            SDLVideo.SDL_GL_DestroyContext(context);
            SDLVideo.SDL_DestroyWindow(window);
        });

        worker.setName("CC-WG-Dispatcher");
        worker.setDaemon(true);

        worker.start();
    }

    private static boolean isContextValid() {
        String versionRaw = GL11.glGetString(GL11.GL_VERSION);

        CubicChunks.LOGGER.info("GL Version: {}", versionRaw);

        String[] version = versionRaw.split(" +");

        String[] parts = version[0].split("\\.");

        if (Integer.parseInt(parts[0]) >= 4 && Integer.parseInt(parts[1]) >= 3) return true;

        String extensionsRaw = GL11.glGetString(GL11.GL_EXTENSIONS);

        CubicChunks.LOGGER.info("GL Extensions: {}", extensionsRaw);

        String[] extensions = extensionsRaw.split(" +");

        boolean hasComputeShaders = false;
        boolean hasSSBOs = false;

        for (var ext : extensions) {
            if (ext.equals("GL_ARB_compute_shader")) {
                hasComputeShaders = true;
            }

            if (ext.equals("GL_ARB_shader_storage_buffer_object")) {
                hasSSBOs = true;
            }
        }

        return hasComputeShaders && hasSSBOs;
    }
}
