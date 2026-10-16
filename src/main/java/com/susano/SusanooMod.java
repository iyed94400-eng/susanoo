package com.susano;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SusanooMod implements ModInitializer {
    public static final String ID = "susanoo";
    /** Joueurs ayant actuellement un Susanoo actif (invulnerables). */
    public static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    public static final Identifier ACTION = new Identifier(ID, "action");

    public static final EntityType<SusanooEntity> SUSANOO = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(ID, "susanoo"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, SusanooEntity::new)
                    .dimensions(EntityDimensions.fixed(3f, 6f))
                    .trackRangeBlocks(160).trackedUpdateRate(1).fireImmune().build());

    public static final Item MANGEKYO = Registry.register(Registries.ITEM, new Identifier(ID, "mangekyo"),
            new SusanooItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(SUSANOO, SusanooEntity.createAttributes());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(MANGEKYO));

        // Le joueur protege par son Susanoo ne subit aucun degat (sauf void / /kill)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof PlayerEntity p && ACTIVE.contains(p.getUuid())
                    && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
            return true;
        });

        // Touches R (attaque) / V (speciale) envoyees par le client
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, sender) -> {
            int action = buf.readInt();
            server.execute(() -> {
                SusanooEntity s = find(player);
                if (s != null) s.manual(action == 1);
            });
        });
    }

    public static SusanooEntity find(PlayerEntity p) {
        return p.getWorld().getEntitiesByClass(SusanooEntity.class, p.getBoundingBox().expand(120),
                e -> p.getUuid().equals(e.getOwnerUuid())).stream().findFirst().orElse(null);
    }
}
