package com.cardinalstar.cubicchunks.mixin;

import javax.annotation.Nonnull;

import com.cardinalstar.cubicchunks.util.Mods;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    // =============================================================
    // COMMON Mixins
    // =============================================================
    // MISC
    MIXIN_ANVIL_SAVE_HANDLER(new MixinBuilder("Changing the save handler to return a Cubic anvil chunk loader.")
        .addCommonMixins("common.MixinAnvilSaveHandler")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_GAME_REGISTRY_ACCESSOR(
        new MixinBuilder("Allows access to the generators in GameRegistry").addCommonMixins("common.IGameRegistry")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_GEN_TAIGA_2(new MixinBuilder("Allowing generation and growing of taiga trees above 256 and below 0.")
        .addCommonMixins("common.MixinWorldGenTaiga2")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_GEN_HUGE_TREES(
        new MixinBuilder("Allowing generation and growing of huge trees (Jungle and pine) above 256 and below 0.")
            .addCommonMixins("common.MixinWorldGenHugeTrees")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_GEN_FOREST(new MixinBuilder("Allowing generation and growing of birch(?) trees above 256 and below 0.")
        .addCommonMixins("common.MixinWorldGenForest")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_GEN_TREES(new MixinBuilder("Allowing generation and growing of oak saplings above 256 and below 0.")
        .addCommonMixins("common.MixinWorldGenTrees")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_GEN_SAVANNA_TREE(
        new MixinBuilder("Allowing generation and growing of Acacia saplings above 256 and below 0.")
            .addCommonMixins("common.MixinWorldGenSavannaTree")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_GEN_CANOPY_TREE(
        new MixinBuilder("Allowing generation and growing of dark oak saplings above 256 and below 0.")
            .addCommonMixins("common.MixinWorldGenCanopyTree")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_BLOCK_FALLING(new MixinBuilder("Allowing blocks that are affected by gravity to fall normally.")
        .addCommonMixins("common.MixinBlockFalling")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_GEN_BIG_MUSHROOM(
        new MixinBuilder("Allowing big mushrooms to generate and be grown above 256 and below 0.")
            .addCommonMixins("common.MixinWorldGenBigMushroom")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_BLOCK_MUSHROOM(new MixinBuilder("Allowing mushrooms to be placed above 256 and below 0.")
        .addCommonMixins("common.MixinBlockMushroom")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_GEN_LAKES(new MixinBuilder("Allowing lakes to be placed above 256 and below 0.")
        .addCommonMixins("common.MixinWorldGenLakes")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_BLOCK_LILY_PAD(
        new MixinBuilder("Allowing lilypads to stay above 256 adn below 0.").addCommonMixins("common.MixinBlockLilyPad")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_S01PACKET_JOIN_GAME(new MixinBuilder("Giving the packet info to initialize cubicWorlds for clients.")
        .addCommonMixins("common.vanillaclient.MixinS01PacketJoinGame")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_OVERWORLD_GENERATOR(new MixinBuilder("Modify overworld chunk generator")
        .addCommonMixins("common.worldgen.MixinChunkProviderGenerate")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_EBS(new MixinBuilder("Add simple cache to ExtendedBlockStorage.getBlockByExtId")
        .addCommonMixins("common.MixinExtendedBlockStorage")
        .setPhase(Phase.EARLY)
        .addExcludedMod(Mods.NotEnoughIDs)
        .addExcludedMod(Mods.ChunkAPI)
        .setApplyIf(() -> true)),
    ACCESSOR_S23(new MixinBuilder("Accessors for X/Y/Z fields for S23PacketBlockChange")
        .addCommonMixins("common.AccessorS23PacketBlockChange")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_S23_HEIGHTLIMITS(new MixinBuilder("Changing packet S23 for reading and writing ints to Y values")
        .addCommonMixins("common.MixinS23PacketBlockChange")
        .setPhase(Phase.EARLY)
        .addExcludedMod(Mods.ChunkAPI)
        .setApplyIf(() -> true)),
    MIXIN_BIOME_GEN_BASE(
        new MixinBuilder("Removes bedrock for pure cubic worlds.").addCommonMixins("common.MixinBiomeGenBase")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_SERVER_DEFER_INIT(new MixinBuilder(
        "Defer World.initialize calls for servers so that chunk loads aren't posted before the server is properly register into DimensionManager")
            .addCommonMixins("common.MixinWorld_DeferInit", "common.MixinWorld_DeferInit$MixinWorldServer")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),

    // CHUNK
    MIXIN_CHUNK(new MixinBuilder("Various modifications to inject cubes, height map patches, etc into Chunks.")
        .addCommonMixins("common.MixinChunk")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_CHUNK_CACHE_HEIGHT_LIMITS(new MixinBuilder("Mixin to fix height limits in ChunkCache")
        .addCommonMixins("common.MixinChunkCache_HeightLimits")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_CHUNK_METAKEY(
        new MixinBuilder("Mixin to implement MetaContainer on Chunk").addCommonMixins("common.MixinChunk_MetaKey")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),

    // ITEM
    MIXIN_ITEM_BLOCK(
        new MixinBuilder("Mixin to to allow placing items everywhere").addCommonMixins("common.MixinItemBlock")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),

    // WORLD
    MIXIN_WORLD_SERVER(new MixinBuilder("Mixin for making world server into a ICubicWorldInternal.Server")
        .addCommonMixins("common.MixinWorldServer")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_SETTINGS(
        new MixinBuilder("Mixin for world settings allowing cubes.").addCommonMixins("common.MixinWorldSettings")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_PROVIDER(
        new MixinBuilder("Implementing ICubicWorldProvider.").addCommonMixins("common.MixinWorldProvider")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD(new MixinBuilder("Implementing ICubicWorld.").addCommonMixins("common.MixinWorld")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_INFO(new MixinBuilder("Implementing ICubicWorldInfo").addCommonMixins("common.MixinWorldInfo")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_DERIVED_WORLD_INFO(new MixinBuilder("Giving the isCubic method to DerivedWorldInfo")
        .addCommonMixins("common.MixinDerivedWorldInfo")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_BLOCK_ACCESS_MIN_MAX_FIX(new MixinBuilder("Have IBlockAccess implement IMinMaxHeight")
        .addCommonMixins("common.MixinIBlockAccess_MinMaxHeight")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_HEIGHT_LIMIT(
        new MixinBuilder("Fix a ton of height limit issues in World.").addCommonMixins("common.MixinWorld_HeightLimit")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_TICK(
        new MixinBuilder("Redirecting some things to use Y values.").addCommonMixins("common.MixinWorld_Tick")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_MAP_GEN(new MixinBuilder("Misc patches to pass Worlds around to various map gen objects")
        .addCommonMixins("common.MixinChunkProviderHell", "common.MixinMapGenBase")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),

    // ENTITY
    MIXIN_ENTITY_DEATH_FIX(new MixinBuilder("Replace -64 constant, to avoid killing entities below y=-64")
        .addCommonMixins("common.MixinEntity_DeathFix")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_ENTITY_TRACKER(
        new MixinBuilder("Changing the EntityTracker to work with cubes.").addCommonMixins("common.MixinEntityTracker")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_ENTITY_TRACKER_ENTRY(
        new MixinBuilder("Changing entityTrackerEntry to use cubes.").addCommonMixins("common.MixinEntityTrackerEntry")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_SPAWNER_ANIMALS(
        new MixinBuilder("Fixing spawner animals to work with cubes.").addCommonMixins("common.MixinSpawnerAnimals")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_STRUCTURE_START(
        new MixinBuilder("Giving Y position to structures.").addCommonMixins("common.MixinStructureStart")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_PATH_NAVIGATE(new MixinBuilder("Fixing casting issues for doubles to ints if they are negative.")
        .addCommonMixins("common.MixinPathNavigate")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_ENTITY_MINECART(new MixinBuilder("Fixing minecarts getting auto-killed below y = -64")
        .addCommonMixins("common.MixinEntityMinecart")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_PATH_FINDER(
        new MixinBuilder("Fixing mobs walking off into chasms below y = 0").addCommonMixins("common.MixinPathFinder")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_ENTITY_BRIGHTNESS(
        new MixinBuilder("Fix Entity.getBrightness").addCommonMixins("common.MixinEntity_Brightness")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),

    // SERVER
    MIXIN_INTEGRATED_SERVER_ACCESSOR(
        new MixinBuilder("Allows access to the worldsettings field.").addCommonMixins("common.IIntegratedServer")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_MINECRAFT_SERVER(new MixinBuilder("Initializes a cubic world instead of a normal world.")
        .addCommonMixins("common.MixinMinecraftServer")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_SERVER_CONFIGURATION_MANAGER(new MixinBuilder("Implements ICubicPlayerList in ServerConfigurationManager")
        .addCommonMixins("common.MixinServerConfigurationManager")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_REGION_FILE_CACHE(
        new MixinBuilder("I believe this is for compat but IDK").addCommonMixins("common.MixinRegionFileCache")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_ENTITY_LIVING_BASE(new MixinBuilder("Fix enttiy handling").addClientMixins("common.MixinEntityLivingBase")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_NET_HANDLER_PLAY_SERVER(new MixinBuilder("Remove height check from NetHandlerPlayServer")
        .addCommonMixins("common.MixinNetHandlerPlayServer")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),

    // =============================================================
    // Client Mixins
    // =============================================================
    MIXIN_I_CHUNK_PROVIDER_CLIENT(new MixinBuilder("Implements IChunkProviderClient on ChunkProviderClient")
        .addClientMixins("client.IChunkProviderClient")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_IGUI_VIDEO_SETTINGS(
        new MixinBuilder("Allows access to the getOptionsRowList field.").addClientMixins("client.IGuiVideoSettings")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_IGUI_OPTIONS_ROW_LIST(new MixinBuilder("Allows access to the field_148184_k (getOptions) field.")
        .addClientMixins("client.IGuiOptionsRowList")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_IGUI_SCREEN(new MixinBuilder("Allows access to the buttonList field.").addClientMixins("client.IGuiScreen")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_CHUNK_CUBES_CLIENT(
        new MixinBuilder("Client chunk fix to turn them to cubes.").addClientMixins("client.MixinChunk_Cubes")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_CHUNK_CACHE_HEIGHT_LIMITS_CLIENT(new MixinBuilder("Client chunk cache fixes for height limits.")
        .addClientMixins("client.MixinChunkCache_HeightLimits")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_CLIENT_PLAYER(new MixinBuilder("Fix player handling").addClientMixins("client.MixinEntityClientPlayerMP")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_EMPTY_CHUNK(new MixinBuilder("Client empty chunk fix.").addClientMixins("client.MixinEmptyChunk")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_HEIGHT_LIMITS_CLIENT(
        new MixinBuilder("Height limits fix for client world.").addClientMixins("client.MixinWorld_HeightLimits")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_CLIENT(new MixinBuilder("Cubic world init for client.").addClientMixins("client.MixinWorldClient")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_PROVIDER_CLIENT(
        new MixinBuilder("World provider fix for Y fog.").addClientMixins("client.MixinWorldProvider")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_RENDERGLOBAL(new MixinBuilder("Fix rendering.").addClientMixins("client.MixinRenderGlobal")
        .setPhase(Phase.EARLY)
        .addExcludedMod(Mods.Angelica)
        .setApplyIf(() -> true)),
    MIXIN_ENTITY_RENDERER(new MixinBuilder("Misc EntityRenderer fixes").addClientMixins("client.MixinEntityRenderer")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_ENTITY(new MixinBuilder("Fixing lighting issues for block exists").addClientMixins("client.MixinEntity")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_NET_HANDLER_PLAY_CLIENT(
        new MixinBuilder("Initializes the client world as cubic.").addClientMixins("client.MixinNetHandlerPlayClient")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    // =============================================================
    // Server Mixins
    // =============================================================

    MIXIN_DEDICATED_PLAYER_LIST(
        new MixinBuilder("Allow to set vertical view distance.").addServerMixins("server.MixinDedicatedPlayerList")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_DEDICATED_SERVER_HEIGHT_LIMITS(new MixinBuilder("Fixing height limit issues in dedicated server.")
        .addServerMixins("server.MixinDedicatedServer_HeightLimits")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_DEDICATED_SERVER_DEFAULT_LEVEL(
        new MixinBuilder("Changing default level in servers with this mod installed to be VanillaCubic.")
            .addServerMixins("server.MixinDedicatedServer_DefaultLevelType")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),

    // =============================================================
    // Mod Mixins
    // =============================================================
    MIXIN_COORD_PACKER_HODGE(new MixinBuilder("Overwrite GTNHLib CoordinatePacker algorithm with a CC-compatible one")
        .addCommonMixins("mod.MixinCoordinatePacker")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_COORD_PACKER_CHUNKAPI(
        new MixinBuilder("Overwrite ChunkAPI CoordiantePacker algorithm with a CC-compatible one")
            .addCommonMixins("mod.MixinBlockPosUtil")
            .setPhase(Phase.LATE)
            .addRequiredMod(Mods.ChunkAPI)
            .setApplyIf(() -> true))
    //
    ;

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
