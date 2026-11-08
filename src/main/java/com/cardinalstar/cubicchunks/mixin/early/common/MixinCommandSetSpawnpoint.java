package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.command.CommandSetSpawnpoint;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(CommandSetSpawnpoint.class)
public class MixinCommandSetSpawnpoint {

    @WrapOperation(
        method = "processCommand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/command/CommandSetSpawnpoint;parseIntBounded(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;II)I",
            ordinal = 1),
        require = 1)
    private int cc$parseHeight(ICommandSender sender, String value, int minimum, int maximum,
        Operation<Integer> original, @Local EntityPlayerMP player) {
        ICubicWorld world = (ICubicWorld) player.worldObj;
        return original.call(sender, value, world.getMinHeight(), world.getMaxHeight() - 1);
    }
}
