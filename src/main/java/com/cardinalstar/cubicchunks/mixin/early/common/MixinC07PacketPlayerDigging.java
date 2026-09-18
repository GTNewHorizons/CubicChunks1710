package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.buffer.ByteBuf;

@Mixin(C07PacketPlayerDigging.class)
public class MixinC07PacketPlayerDigging {

    @Shadow
    private int field_149509_b;

    @Redirect(
        method = "readPacketData",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readUnsignedByte()S", ordinal = 1))
    private short skipVanillaYRead(PacketBuffer data) {
        return 0;
    }

    @Inject(
        method = "readPacketData",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readInt()I", ordinal = 1))
    private void readFullY(PacketBuffer data, CallbackInfo ci) {
        this.field_149509_b = data.readInt();
    }

    @Redirect(
        method = "writePacketData",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/PacketBuffer;writeByte(I)Lio/netty/buffer/ByteBuf;",
            ordinal = 1))
    private ByteBuf writeFullY(PacketBuffer data, int ignored) {
        return data.writeInt(this.field_149509_b);
    }
}
