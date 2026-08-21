package com.cardinalstar.cubicchunks.mixin.early.common.vanillaclient;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.api.ICubicWorldServer;
import com.cardinalstar.cubicchunks.network.ICubicRespawnPacket;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mixin(S07PacketRespawn.class)
public class MixinS07PacketRespawn implements ICubicRespawnPacket {

    @Unique
    private int minHeight = 0;

    @Unique
    private int maxHeight = 256;

    @Unique
    private int minGenerationHeight = 0;

    @Unique
    private int maxGenerationHeight = 256;

    @Inject(
        method = "<init>(ILnet/minecraft/world/EnumDifficulty;Lnet/minecraft/world/WorldType;Lnet/minecraft/world/WorldSettings$GameType;)V",
        at = @At("TAIL"))
    private void cubicChunks$initHeightInfo(int dimension, EnumDifficulty difficulty, WorldType worldType,
        WorldSettings.GameType gameType, CallbackInfo ci) {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) return;

        WorldServer world = server.worldServerForDimension(dimension);
        if (world instanceof ICubicWorldServer cubicWorld) {
            cubicChunks$setHeightInfo(
                cubicWorld.getMinHeight(),
                cubicWorld.getMaxHeight(),
                cubicWorld.getMinGenerationHeight(),
                cubicWorld.getMaxGenerationHeight());
        }
    }

    @SideOnly(Side.CLIENT)
    @Unique
    @Override
    public int cubicChunks$getMinHeight() {
        return minHeight;
    }

    @SideOnly(Side.CLIENT)
    @Unique
    @Override
    public int cubicChunks$getMaxHeight() {
        return maxHeight;
    }

    @SideOnly(Side.CLIENT)
    @Unique
    @Override
    public int cubicChunks$getMinGenerationHeight() {
        return minGenerationHeight;
    }

    @SideOnly(Side.CLIENT)
    @Unique
    @Override
    public int cubicChunks$getMaxGenerationHeight() {
        return maxGenerationHeight;
    }

    @Unique
    @Override
    public void cubicChunks$setHeightInfo(int minHeight, int maxHeight, int minGenerationHeight,
        int maxGenerationHeight) {
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.minGenerationHeight = minGenerationHeight;
        this.maxGenerationHeight = maxGenerationHeight;
    }

    @Inject(method = "writePacketData", at = @At("TAIL"))
    private void cubicChunks$writeHeightInfo(PacketBuffer data, CallbackInfo ci) {
        data.writeInt(minHeight);
        data.writeInt(maxHeight);
        data.writeInt(minGenerationHeight);
        data.writeInt(maxGenerationHeight);
    }

    @Inject(method = "readPacketData", at = @At("TAIL"))
    private void cubicChunks$readHeightInfo(PacketBuffer data, CallbackInfo ci) {
        cubicChunks$setHeightInfo(data.readInt(), data.readInt(), data.readInt(), data.readInt());
    }
}
