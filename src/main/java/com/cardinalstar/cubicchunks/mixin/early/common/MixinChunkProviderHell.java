package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderHell;
import net.minecraft.world.gen.structure.MapGenNetherBridge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(ChunkProviderHell.class)
public class MixinChunkProviderHell {

    @Shadow
    public MapGenNetherBridge genNetherBridge;

    @Shadow
    private World worldObj;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void cc$setWorldObjs(World world, long seed, CallbackInfo ci) {
        ((MixinMapGenBase) this.genNetherBridge).setWorldObj(world);
    }

    @Definition(id = "bedrock", field = "Lnet/minecraft/init/Blocks;bedrock:Lnet/minecraft/block/Block;")
    @Expression("?[?] = bedrock")
    @WrapOperation(
        method = "replaceBiomeBlocks",
        at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 0),
        remap = false)
    private void cc$removeBedrock(Block[] array, int index, Block value, Operation<Void> original) {
        if (!((ICubicWorldInternal) this.worldObj).cc$getRemoveNetherBedrock()) {
            array[index] = value;
        }
    }
}
