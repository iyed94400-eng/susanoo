package com.susano;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SusanooItem extends Item {
    public SusanooItem(Settings settings) { super(settings); }

    /** Clic droit : invoque / fait evoluer. Accroupi + clic droit : renvoie le Susanoo. */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity p, Hand hand) {
        ItemStack stack = p.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);

        SusanooEntity s = SusanooMod.find(p);
        if (p.isSneaking()) {
            if (s != null) s.dismiss();
        } else if (s == null) {
            SusanooEntity e = SusanooMod.SUSANOO.create(world);
            if (e != null) {
                e.setOwner(p.getUuid());
                e.setForm(1);
                Vec3d back = Vec3d.fromPolar(0, p.getYaw()).multiply(-4);
                e.refreshPositionAndAngles(p.getX() + back.x, p.getY(), p.getZ() + back.z, p.getYaw(), 0);
                world.spawnEntity(e);
                e.transformEffects();
                p.sendMessage(Text.literal("Susanoo — Forme 1 : les côtes et les bras"), true);
            }
        } else {
            s.evolve();
        }
        p.getItemCooldownManager().set(this, 30);
        return TypedActionResult.success(stack);
    }
}
