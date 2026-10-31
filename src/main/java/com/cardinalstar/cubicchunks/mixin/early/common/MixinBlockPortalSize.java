package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.BlockPortal;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cardinalstar.cubicchunks.world.ICubicWorld;

@Mixin(BlockPortal.Size.class)
public class MixinBlockPortalSize {

    @ModifyConstant(
        method = "<init>",
        constant = @Constant(expandZeroConditions = Constant.Condition.GREATER_THAN_ZERO),
        require = 1)
    private int cc$frameFloor(int vanillaMinimum, World world, int x, int y, int z, int axis) {
        return ((ICubicWorld) world).getMinHeight();
    }
}
