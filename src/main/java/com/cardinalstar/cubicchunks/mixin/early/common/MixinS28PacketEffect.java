package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S28PacketEffect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.buffer.ByteBuf;

@Mixin(S28PacketEffect.class)
public class MixinS28PacketEffect {

    @Shadow
    private int field_149247_d;

    @Redirect(
        method = "readPacketData",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readByte()B"))
    private byte skipVanillaYRead(PacketBuffer data) {
        return 0;
    }

    @Inject(
        method = "readPacketData",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readInt()I", ordinal = 2))
    private void readFullY(PacketBuffer data, CallbackInfo ci) {
        this.field_149247_d = data.readInt();
    }

    @Redirect(
        method = "writePacketData",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/PacketBuffer;writeByte(I)Lio/netty/buffer/ByteBuf;"))
    private ByteBuf writeFullY(PacketBuffer data, int ignored) {
        return data.writeInt(this.field_149247_d);
    }
}
