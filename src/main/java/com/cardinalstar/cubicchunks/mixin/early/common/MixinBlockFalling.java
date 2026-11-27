package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cardinalstar.cubicchunks.world.FallingBlockMove;
import com.cardinalstar.cubicchunks.world.api.IMinMaxHeight;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(BlockFalling.class)
public class MixinBlockFalling {

    /// Ticks between two looks at a cube that was not loaded when the block wanted to fall into it.
    @Unique
    private static final int CC_UNLOADED_CUBE_RECHECK_TICKS = 20;

    @Unique
    private static boolean cc$isInUnloadedCube(World world, int x, int y, int z) {
        if (world.isRemote) return false;
        IMinMaxHeight heights = (IMinMaxHeight) world;
        return y >= heights.getMinHeight() && y < heights.getMaxHeight() && !world.blockExists(x, y, z);
    }

    /// Reading a block loads or generates its cube, so asking whether a block can fall into a cube that is not loaded
    /// would load it, and an instant fall every cube down to the next surface. Nothing falls into such a cube.
    @Inject(method = "func_149831_e", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cannotFallIntoUnloadedCube(World world, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        if (cc$isInUnloadedCube(world, x, y, z)) cir.setReturnValue(false);
    }

    /// A block above a cube that is not loaded looks again later; the scheduled update is saved with its cube.
    @Inject(method = { "onBlockAdded", "func_149830_m" }, at = @At("HEAD"), require = 2)
    private void waitForCubeBelow(World world, int x, int y, int z, CallbackInfo ci) {
        if (cc$isInUnloadedCube(world, x, y - 1, z)) {
            world.scheduleBlockUpdate(x, y, z, (Block) (Object) this, CC_UNLOADED_CUBE_RECHECK_TICKS);
        }
    }

    @WrapMethod(method = "func_149830_m")
    private void moveWithoutLoadingNeighbors(World world, int x, int y, int z, Operation<Void> original) {
        FallingBlockMove.begin();
        try {
            original.call(world, x, y, z);
        } finally {
            FallingBlockMove.end();
        }
    }

    @Definition(id = "y", local = @Local(argsOnly = true, ordinal = 1, type = int.class))
    @Expression("y >= 0")
    @WrapOperation(method = "func_149830_m", at = @At("MIXINEXTRAS:EXPRESSION"))
    boolean blockFallingMinHeighFix1(int left, int right, Operation<Boolean> original, World world) {
        return left >= ((IMinMaxHeight) world).getMinHeight();
    }

    @Definition(id = "y", local = @Local(argsOnly = true, ordinal = 1, type = int.class))
    @Expression("y > 0")
    @WrapOperation(method = "func_149830_m", at = @At("MIXINEXTRAS:EXPRESSION"))
    boolean blockFallingMinHeighFix2(int left, int right, Operation<Boolean> original, World world) {
        return left >= ((IMinMaxHeight) world).getMinHeight();
    }
}
