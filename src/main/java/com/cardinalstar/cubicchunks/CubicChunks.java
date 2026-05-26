/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.world.storage.StorageFormatFactory;
import com.cardinalstar.cubicchunks.api.worldtype.VanillaCubicWorldType;
import com.cardinalstar.cubicchunks.async.TaskPool;
import com.cardinalstar.cubicchunks.event.handlers.ClientEventHandler;
import com.cardinalstar.cubicchunks.event.handlers.CommonEventHandler;
import com.cardinalstar.cubicchunks.network.NetworkChannel;
import com.cardinalstar.cubicchunks.server.ICubicChunksServer;
import com.cardinalstar.cubicchunks.server.chunkio.RegionCubeStorage;
import com.cardinalstar.cubicchunks.util.CompatHandler;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.util.SideUtils;
import com.cardinalstar.cubicchunks.world.worldgen.WorldGenerators;
import com.cardinalstar.cubicchunks.worldgen.WorldgenHangWatchdog;
import com.falsepattern.chunk.api.DataRegistry;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ICrashCallable;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.NetworkModHolder;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.InvalidVersionSpecificationException;
import cpw.mods.fml.common.versioning.VersionRange;
import cpw.mods.fml.relauncher.Side;

@ParametersAreNonnullByDefault
@Mod(modid = CubicChunks.MODID, useMetadata = true)
public class CubicChunks {

    public static final int MAX_RENDER_DISTANCE = 64;
    public static final VersionRange SUPPORTED_SERVER_VERSIONS;
    public static final VersionRange SUPPORTED_CLIENT_VERSIONS;

    static {
        try {
            // Versions newer than current will be only checked on the other side
            // (I know this can be hard to actually fully understand)
            SUPPORTED_SERVER_VERSIONS = VersionRange.createFromVersionSpec("[1.12.2-0.0.887.0,)");
            SUPPORTED_CLIENT_VERSIONS = VersionRange.createFromVersionSpec("[1.12.2-0.0.887.0,)");
        } catch (InvalidVersionSpecificationException e) {
            throw new Error(e);
        }
    }

    public static final int MIN_SUPPORTED_BLOCK_Y = Integer.MIN_VALUE + 4096;
    public static final int MAX_SUPPORTED_BLOCK_Y = Integer.MAX_VALUE - 4095;

    public static final boolean DEBUG_ENABLED = System.getProperty("cubicchunks.debug", "false")
        .equalsIgnoreCase("true");
    public static final String MODID = "cubicchunks";

    @Nonnull
    public static Logger LOGGER = LogManager.getLogger("EarlyCubicChunks");// use some logger even before it's set.
                                                                           // useful for unit tests

    private static final String MOD_VERSION;

    static {
        String implementationVersion = CubicChunks.class.getPackage()
            .getImplementationVersion();
        if (implementationVersion == null) {
            LOGGER.error("No implementation version! If this is dev environment, this is normal");
            implementationVersion = "9999.9999.9999.9";
        }
        MOD_VERSION = implementationVersion;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        LOGGER = e.getModLog();

        registerAnvil3dStorageFormatProvider();
        VanillaCubicWorldType.init();

        LOGGER.debug("Registered world types");

        try {
            ConfigurationManager.registerConfig(CubicChunksConfig.class);
        } catch (ConfigException ex) {
            throw new RuntimeException(ex);
        }
        FMLCommonHandler.instance()
            .registerCrashCallable(new ICrashCallable() {

                @Override
                public String getLabel() {
                    return "CubicChunks WorldGen Hang Watchdog samples";
                }

                @Override
                public String call() throws Exception {
                    String message = WorldgenHangWatchdog.getCrashInfo();
                    if (message == null) {
                        return "(no data)";
                    }
                    return message;
                }
            });

        // we have to redo the check for network compatibility because it depends on config
        // and config is done after forge does the check
        NetworkModHolder holder = NetworkRegistry.INSTANCE.registry()
            .get(
                Loader.instance()
                    .activeModContainer());
        holder.testVanillaAcceptance();
        WorldGenerators.init();
        TaskPool.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        try {
            CubicChunksConfig.init();
        } catch (ConfigException ex) {
            throw new RuntimeException(ex);
        }

        CommonEventHandler eventHandler = new CommonEventHandler();
        MinecraftForge.EVENT_BUS.register(eventHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(eventHandler);
        SideUtils.runForClient(() -> () -> {
            ClientEventHandler clientEventHandler = new ClientEventHandler();
            MinecraftForge.EVENT_BUS.register(clientEventHandler);
            FMLCommonHandler.instance()
                .bus()
                .register(clientEventHandler);
        });
        NetworkChannel.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        CompatHandler.init();

        if (Mods.ChunkAPI.isModLoaded()) {
            DataRegistry.disableDataManager("chunkapi", "lighting");
        }
    }

    @Mod.EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        SideUtils.runForSide(() -> () -> {
            MinecraftServer server = event.getServer();
            server.setBuildLimit(CubicChunks.MAX_SUPPORTED_BLOCK_Y);
            ((ICubicChunksServer) server).setBuildMinimum(CubicChunks.MIN_SUPPORTED_BLOCK_Y);
        }, () -> () -> {
            // no-op, done by mixin
        });
    }

    public static void registerAnvil3dStorageFormatProvider() {
        StorageFormatFactory.REGISTRY.register(StorageFormatFactory.DEFAULT, new DefaultStorageFormatFactory());
    }

    @NetworkCheckHandler
    public static boolean checkCanConnectWithMods(Map<String, String> modVersions, Side remoteSide) {
        String remoteFullVersion = modVersions.get(MODID);
        if (remoteFullVersion == null) {
            return !remoteSide.isClient(); // don't allow client without CC to connect
        }
        if (!checkVersionFormat(MOD_VERSION, remoteSide.isClient() ? Side.SERVER : Side.CLIENT)) {
            return true;
        }
        if (!checkVersionFormat(remoteFullVersion, remoteSide)) {
            return true;
        }

        ArtifactVersion version = new DefaultArtifactVersion(remoteFullVersion);
        ArtifactVersion currentVersion = new DefaultArtifactVersion(MOD_VERSION);
        if (currentVersion.compareTo(version) < 0) {
            return true; // allow connection if this version is older, let newer one decide
        }
        return (remoteSide.isClient() ? SUPPORTED_CLIENT_VERSIONS : SUPPORTED_SERVER_VERSIONS).containsVersion(version);
    }

    // returns true if version format is known. Side can be null if not logging connection attempt
    private static boolean checkVersionFormat(String version, @Nullable Side remoteSide) {
        int mcVersionSplit = version.indexOf('-');
        if (mcVersionSplit < 0) {
            LOGGER.warn(
                "Connection attempt with unexpected " + remoteSide
                    + " version string: "
                    + version
                    + ". Cannot split into MC "
                    + "version and mod version. Assuming dev environment or special/unknown version, connection will be allowed.");
            return false;
        }

        String modVersion = version.substring(mcVersionSplit + 1);

        if (modVersion.isEmpty()) {
            LOGGER.warn(
                "Connection attempt with unexpected " + remoteSide
                    + " version string: "
                    + version
                    + ". Mod version part not "
                    + "found. Assuming dev environment or special/unknown version,, connection will be allowed");
            return false;
        }

        final String versionRegex = "\\d+\\." + "\\d+\\." + "\\d+\\." + "\\d+" + "(-.+)?";// "MAJORMOD.MAJORAPI.MINOR.PATCH(-final/rcX/betaX)"

        if (!modVersion.matches(versionRegex)) {
            LOGGER.warn(
                "Connection attempt with unexpected " + remoteSide
                    + " version string: "
                    + version
                    + ". Mod version part ("
                    + modVersion
                    + ") does not match expected format ('MAJORMOD.MAJORAPI.MINOR.PATCH(-optionalText)'). Assuming dev "
                    + "environment or special/unknown version, connection will be allowed");
            return false;
        }
        return true;
    }

    // essentially a copy of FMLLog.bigWarning, with more lines of stacktrace
    public static void bigWarning(String format, Object... data) {
        StackTraceElement[] trace = Thread.currentThread()
            .getStackTrace();
        LOGGER.log(Level.WARN, "****************************************");
        LOGGER.log(Level.WARN, "* " + format, data);
        for (int i = 2; i < 10 && i < trace.length; i++) {
            LOGGER.log(Level.WARN, "*  at {}{}", trace[i].toString(), i == 9 ? "..." : "");
        }
        LOGGER.log(Level.WARN, "****************************************");
    }

    private static class DefaultStorageFormatFactory extends StorageFormatFactory {

        public DefaultStorageFormatFactory() {
            setRegistryName(StorageFormatFactory.DEFAULT);
            setUnlocalizedName("cubicchunks.gui.storagefmt.anvil3d");
        }

        @Override
        public Path getWorldSaveDirectory(ISaveHandler saveHandler, WorldServer worldServer) {
            Path path = worldServer.getSaveHandler()
                .getWorldDirectory()
                .toPath();

            if (worldServer.provider.getSaveFolder() != null) {
                return path.resolve(worldServer.provider.getSaveFolder());
            } else {
                return path;
            }
        }

        @Override
        public ICubicStorage provideStorage(World world, Path path) throws IOException {
            return new RegionCubeStorage(path);
        }
    }
}
