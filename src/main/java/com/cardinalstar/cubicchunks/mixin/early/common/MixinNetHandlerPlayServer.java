package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cardinalstar.cubicchunks.world.api.IMinMaxHeight;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(NetHandlerPlayServer.class)
public class MixinNetHandlerPlayServer {

    @Shadow
    public EntityPlayerMP playerEntity;

    @Redirect(
        method = "processPlayerBlockPlacement",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/ItemInWorldManager;activateBlockOrUseItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIIIFFF)Z"))
    private boolean preventLowPlacement(ItemInWorldManager manager, EntityPlayer player, World world, ItemStack stack,
        int x, int y, int z, int side, float hitX, float hitY, float hitZ,
        @Local(type = WorldServer.class) WorldServer server) {
        if (y < ((IMinMaxHeight) server).getMinHeight() + 1
            && (side == 0 || y < ((IMinMaxHeight) server).getMinHeight())) {
            ChatComponentTranslation chatcomponenttranslation = new ChatComponentTranslation(
                "cubicchunks.build.too.low",
                ((IMinMaxHeight) server).getMinHeight());
            chatcomponenttranslation.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            this.playerEntity.playerNetServerHandler.sendPacket(new S02PacketChat(chatcomponenttranslation));
            return false;
        }

        return !manager.activateBlockOrUseItem(player, world, stack, x, y, z, side, hitX, hitY, hitZ);
    }

    @Redirect(
        method = "processPlayerBlockPlacement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I"))
    public int noopHeightChecks(MinecraftServer instance, @Local(type = WorldServer.class) WorldServer server) {
        return ((IMinMaxHeight) server).getMaxHeight();
    }

    @Definition(
        id = "serverController",
        field = "Lnet/minecraft/network/NetHandlerPlayServer;serverController:Lnet/minecraft/server/MinecraftServer;")
    @Definition(id = "getBuildLimit", method = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I")
    @Expression("? >= this.serverController.getBuildLimit()")
    @ModifyExpressionValue(method = "processPlayerDigging", at = @At("MIXINEXTRAS:EXPRESSION"))
    public boolean setDimensionalBounds(boolean original, @Local(type = WorldServer.class) WorldServer server,
        @Local(type = int.class, name = "j") int j) {
        return j >= ((IMinMaxHeight) server).getMaxHeight() || j < ((IMinMaxHeight) server).getMinHeight();
    }
}
