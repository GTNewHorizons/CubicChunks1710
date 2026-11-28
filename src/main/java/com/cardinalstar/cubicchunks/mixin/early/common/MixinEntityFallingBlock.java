package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.cardinalstar.cubicchunks.world.FallingBlockMove;
import com.cardinalstar.cubicchunks.world.api.IMinMaxHeight;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(EntityFallingBlock.class)
public abstract class MixinEntityFallingBlock extends Entity {

    public MixinEntityFallingBlock(World worldIn) {
        super(worldIn);
    }

    /// Entities are only updated with every cube around them loaded, except in cubes forced by a ticket. There a
    /// falling block would leave the loaded cubes and load the cube it enters, so it waits in front of that cube.
    @WrapMethod(method = "onUpdate")
    private void updateWithoutLoadingCubes(Operation<Void> original) {
        if (worldObj.isRemote) {
            original.call();
            return;
        }

        FallingBlockMove.begin();
        try {
            if (!cc$wouldEnterUnloadedCube()) original.call();
        } finally {
            FallingBlockMove.end();
        }
    }

    /// Where the fall of this update ends: on top of what is in the way, as [Entity#moveEntity] will find.
    @Unique
    private boolean cc$wouldEnterUnloadedCube() {
        double fall = motionY - 0.04D;
        for (AxisAlignedBB box : worldObj
            .getCollidingBoundingBoxes(this, boundingBox.addCoord(motionX, fall, motionZ))) {
            fall = box.calculateYOffset(boundingBox, fall);
        }

        int x = MathHelper.floor_double(posX + motionX);
        int z = MathHelper.floor_double(posZ + motionZ);
        // top and bottom: the entity is filed in the cube of its centre, and an explosion can push it upwards
        return cc$isInUnloadedCube(x, MathHelper.floor_double(boundingBox.minY + fall), z)
            || cc$isInUnloadedCube(x, MathHelper.floor_double(boundingBox.maxY + fall), z);
    }

    @Unique
    private boolean cc$isInUnloadedCube(int x, int y, int z) {
        IMinMaxHeight heights = (IMinMaxHeight) worldObj;
        return y >= heights.getMinHeight() && y < heights.getMaxHeight() && !worldObj.blockExists(x, y, z);
    }

    @Definition(id = "y", local = @Local(type = int.class, ordinal = 1))
    @Expression("y < 1")
    @WrapOperation(method = "onUpdate", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1)
    private boolean expireBelowMinHeight(int y, int one, Operation<Boolean> original) {
        return original.call(y, ((IMinMaxHeight) worldObj).getMinHeight() + one);
    }

    @Definition(id = "y", local = @Local(type = int.class, ordinal = 1))
    @Expression("y > 256")
    @WrapOperation(method = "onUpdate", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1)
    private boolean expireAboveMaxHeight(int y, int maxHeight, Operation<Boolean> original) {
        return original.call(y, ((IMinMaxHeight) worldObj).getMaxHeight());
    }
}
