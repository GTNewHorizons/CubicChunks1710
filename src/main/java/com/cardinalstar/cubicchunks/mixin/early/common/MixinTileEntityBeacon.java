package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBeacon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.cardinalstar.cubicchunks.world.ICubicWorld;

@Mixin(TileEntityBeacon.class)
public abstract class MixinTileEntityBeacon extends TileEntity {

    @ModifyConstant(
        method = "func_146003_y",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO),
        require = 1)
    private int cc$pyramidFloor(int vanillaMinimum) {
        return ((ICubicWorld) this.worldObj).getMinHeight();
    }
}
