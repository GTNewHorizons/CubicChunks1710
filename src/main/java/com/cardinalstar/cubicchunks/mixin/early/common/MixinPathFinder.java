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
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;

@Mixin(PathFinder.class)
public class MixinPathFinder {

    @Unique
    private HashMap3D<PathPoint> cc$pointMap = new HashMap3D<>();

    @Shadow
    @Mutable
    private IntHashMap pointMap;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cc$hookInit(IBlockAccess p_i2137_1_, boolean p_i2137_2_, boolean p_i2137_3_, boolean p_i2137_4_,
        boolean p_i2137_5_, CallbackInfo ci) {
        pointMap = new PoisonIntHashMap("MixinPathFinder");
    }

    @Inject(method = "getSafePoint", at = @At("HEAD"))
    private void cc$captureFloorLimit(Entity entity, int x, int y, int z, PathPoint p_75858_5_, int p_75858_6_,
        CallbackInfoReturnable<PathPoint> cir, @Share("cc$floorLimit") LocalIntRef floorLimit) {
        floorLimit.set(((ICubicWorld) entity.worldObj).getMinHeight());
    }

    @Definition(id = "p_75858_3_", local = @Local(type = int.class, ordinal = 1, argsOnly = true))
    @Expression("p_75858_3_ > 0")
    @WrapOperation(method = "getSafePoint", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean cc$redirectLessThan0Comparison(int yPosition, int zero, Operation<Boolean> original,
        @Share("cc$floorLimit") LocalIntRef floorLimit) {
        return yPosition > floorLimit.get();
    }

    @Definition(id = "getBlock", method = "Lnet/minecraft/world/World;getBlock(III)Lnet/minecraft/block/Block;")
    @Expression("?.getBlock(?, ?, ?)")
    @Redirect(method = "func_82565_a", at = @At("MIXINEXTRAS:EXPRESSION"))
    private static Block cc$checkWorldLoaded(World world, int x, int y, int z) {
        if (world.blockExists(x, y, z)) {
            return world.getBlock(x, y, z);
        }
        return Blocks.air;
    }

    @Redirect(
        method = "createEntityPathTo(Lnet/minecraft/entity/Entity;DDDF)Lnet/minecraft/pathfinding/PathEntity;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IntHashMap;clearMap()V"), require = 1)
    private void cc$interceptPointMapClear(IntHashMap instance) {
        cc$pointMap.clear();
    }

    @Redirect(
        method = "openPoint",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IntHashMap;lookup(I)Ljava/lang/Object;"), require = 1)
    private Object cc$interceptPointMapLookup(IntHashMap instance, int p_76041_1_, int p_75854_1_, int p_75854_2_,
        int p_75854_3_) {
        return cc$pointMap.get(p_75854_1_, p_75854_2_, p_75854_3_);
    }

    @Redirect(
        method = "openPoint",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IntHashMap;addKey(ILjava/lang/Object;)V"), require = 1)
    private void cc$interceptPointMapAddKey(IntHashMap instance, int p_76038_1_, Object p_76038_2_, int p_75854_1_,
        int p_75854_2_, int p_75854_3_) {
        cc$pointMap.put(p_75854_1_, p_75854_2_, p_75854_3_, (PathPoint) p_76038_2_);
    }
}
