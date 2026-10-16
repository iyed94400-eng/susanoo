package com.susano.client;

import com.susano.SusanooMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SusanooClient implements ClientModInitializer {
    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(SusanooMod.ID, "susanoo"), "main");

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(LAYER, SusanooModel::getTexturedModelData);
        EntityRendererRegistry.register(SusanooMod.SUSANOO, SusanooRenderer::new);

        KeyBinding atk = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.susanoo.attack",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.susanoo"));
        KeyBinding spc = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.susanoo.special",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.susanoo"));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (atk.wasPressed()) send(0);
            while (spc.wasPressed()) send(1);
        });
    }

    private static void send(int action) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeInt(action);
        ClientPlayNetworking.send(SusanooMod.ACTION, b);
    }
}
