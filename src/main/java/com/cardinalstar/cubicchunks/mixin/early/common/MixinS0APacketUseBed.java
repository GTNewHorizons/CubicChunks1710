package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S0APacketUseBed;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.buffer.ByteBuf;

@Mixin(S0APacketUseBed.class)
public class MixinS0APacketUseBed {

    @Shadow
    private int field_149096_c;

    @Redirect(
        method = "readPacketData",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readByte()B"),
        require = 1)
    private byte skipVanillaYRead(PacketBuffer data) {
        return 0;
    }

    @Inject(
        method = "readPacketData",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readInt()I", ordinal = 2),
        require = 1)
    private void readFullY(PacketBuffer data, CallbackInfo ci) {
        this.field_149096_c = data.readInt();
    }

    @Redirect(
        method = "writePacketData",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/PacketBuffer;writeByte(I)Lio/netty/buffer/ByteBuf;"),
        require = 1)
    private ByteBuf writeFullY(PacketBuffer data, int ignored) {
        return data.writeInt(this.field_149096_c);
    }
}
