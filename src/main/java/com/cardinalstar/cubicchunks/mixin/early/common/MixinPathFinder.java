package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.IntHashMap;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.util.HashMap3D;
import com.cardinalstar.cubicchunks.util.PoisonIntHashMap;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(PathFinder.class)
public class MixinPathFinder {

    @Unique
    private HashMap3D<PathPoint> cc$pointMap = new HashMap3D<>();

    @Shadow
    @Mutable
    private IntHashMap pointMap;

    @Unique
    private int cc$floorLimit;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cc$hookInit(IBlockAccess p_i2137_1_, boolean p_i2137_2_, boolean p_i2137_3_, boolean p_i2137_4_,
        boolean p_i2137_5_, CallbackInfo ci) {
        pointMap = new PoisonIntHashMap("MixinPathFinder");
    }

    @Inject(method = "getSafePoint", at = @At("HEAD"))
    private void cc$captureFloorLimit(Entity entity, int x, int y, int z, PathPoint p_75858_5_, int p_75858_6_,
        CallbackInfoReturnable<PathPoint> cir) {
        cc$floorLimit = Math.max(((ICubicWorld) entity.worldObj).getMinHeight(), y - entity.getMaxSafePointTries());
    }

    @Definition(id = "p_75858_3_", local = @Local(type = int.class, ordinal = 1, argsOnly = true))
    @Expression("p_75858_3_ > 0")
    @WrapOperation(method = "getSafePoint", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean redirectLessThan0Comparison(int yPosition, int zero, Operation<Boolean> original) {
        return yPosition > cc$floorLimit;
    }

    @Definition(id = "getBlock", method = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;")
    @Expression("?.getBlock(?, ?, ?)")
    @Redirect(method = "func_82565_a", at = @At("MIXINEXTRAS:EXPRESSION"))
    private static Block CheckWorldLoaded(World world, int x, int y, int z) {
        if (world.blockExists(x, y, z)) {
            return world.getBlock(x, y, z);
        }
        return Blocks.air;
    }

    @Redirect(
        method = "createEntityPathTo(Lnet/minecraft/entity/Entity;DDDF)Lnet/minecraft/pathfinding/PathEntity;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IntHashMap;clearMap()V"))
    private void cc$interceptPointMapClear(IntHashMap instance) {
        cc$pointMap.clear();
    }

    @Redirect(
        method = "openPoint",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IntHashMap;lookup(I)Ljava/lang/Object;"))
    private Object cc$interceptPointMapLookup(IntHashMap instance, int p_76041_1_,
        @Local(argsOnly = true, name = "p_75854_1_") int x, @Local(argsOnly = true, name = "p_75854_2_") int y,
        @Local(argsOnly = true, name = "p_75854_3_") int z) {
        return cc$pointMap.get(x, y, z);
    }

    @Redirect(
        method = "openPoint",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IntHashMap;addKey(ILjava/lang/Object;)V"))
    private void cc$interceptPointMapAddKey(IntHashMap instance, int p_76038_1_, Object p_76038_2_,
        @Local(argsOnly = true, name = "p_75854_1_") int x, @Local(argsOnly = true, name = "p_75854_2_") int y,
        @Local(argsOnly = true, name = "p_75854_3_") int z) {
        cc$pointMap.put(x, y, z, (PathPoint) p_76038_2_);
    }
}
