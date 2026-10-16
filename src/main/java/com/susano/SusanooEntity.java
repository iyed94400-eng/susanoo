package com.susano;

import net.minecraft.entity.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Susanoo : 3 formes.
 *  1 = cotes + bras (taille 6)   2 = presque parfait, armure + epee (taille 10)   3 = parfait, geant (taille 18)
 */
public class SusanooEntity extends PathAwareEntity {
    private static final TrackedData<Integer> FORM_T = DataTracker.registerData(SusanooEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ATTACK_T = DataTracker.registerData(SusanooEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WALK_T = DataTracker.registerData(SusanooEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public static final int ATTACK_LEN = 20;
    private static final int IMPACT_TICK = 9;
    private static final int SPECIAL_CD = 200;
    private static final int[] AUTO_CD = {0, 30, 25, 30};
    private static final double[] REACH = {0, 9, 18, 30};

    private UUID ownerUuid;
    private int form = 1;
    private int attackTicks, cooldown, specialCooldown, transformTicks;
    private boolean pendingSpecial, locked;
    private float lockYaw;
    private double lastOx, lastOz;
    private float renderScale = 0.4f;

    public SusanooEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        setPersistent();
        this.ignoreCameraFrustum = true;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1000)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(FORM_T, 1);
        dataTracker.startTracking(ATTACK_T, 0);
        dataTracker.startTracking(WALK_T, false);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        if (FORM_T.equals(data)) {
            form = dataTracker.get(FORM_T);
            calculateDimensions();
        }
        super.onTrackedDataSet(data);
    }

    // ---------- accesseurs ----------
    public int getForm() { return form; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwner(UUID u) { ownerUuid = u; }
    public boolean isWalking() { return dataTracker.get(WALK_T); }
    public float getRenderScale() { return getWorld().isClient ? renderScale : scaleOf(form); }
    public static float scaleOf(int f) { return f == 1 ? 3f : f == 2 ? 5f : 9f; }

    /** -1 si pas d'attaque, sinon progression 0..1 */
    public float getAttackProgress() {
        int t = dataTracker.get(ATTACK_T);
        return t > 0 ? 1f - t / (float) ATTACK_LEN : -1f;
    }

    public PlayerEntity getOwner() {
        return ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
    }

    public void setForm(int f) {
        form = f;
        dataTracker.set(FORM_T, f);
        calculateDimensions();
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        float s = scaleOf(form);
        return EntityDimensions.changing(s * (form == 3 ? 1.1f : 1f), s * 2f);
    }

    // ---------- indestructible, non pousse, non cliquable ----------
    @Override public boolean damage(DamageSource source, float amount) {
        return source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.damage(source, amount);
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override protected void tickCramming() { }
    @Override public boolean canImmediatelyDespawn(double d) { return false; }

    @Override
    public void remove(RemovalReason reason) {
        if (ownerUuid != null && !getWorld().isClient) SusanooMod.ACTIVE.remove(ownerUuid);
        super.remove(reason);
    }

    // ---------- evolution ----------
    public void evolve() {
        PlayerEntity o = getOwner();
        if (form >= 3) {
            if (o != null) o.sendMessage(Text.literal("Susanoo parfait : forme maximale !"), true);
            return;
        }
        setForm(form + 1);
        transformEffects();
        if (o != null) o.sendMessage(Text.literal(form == 2
                ? "Susanoo — Forme 2 : armure et épée (presque parfait)"
                : "Susanoo — Forme 3 : LE SUSANOO PARFAIT"), true);
    }

    public void dismiss() {
        if (getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + getHeight() / 2, getZ(), 200, getWidth() / 2, getHeight() / 2, getWidth() / 2, 0.2);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getHeight() / 2, getZ(), 100, getWidth() / 2, getHeight() / 2, getWidth() / 2, 0.1);
            w.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 3f, 0.8f);
        }
        discard();
    }

    // ---------- effets de transformation : particules + lumiere ----------
    public void transformEffects() {
        if (!(getWorld() instanceof ServerWorld w)) return;
        double x = getX(), y = getY(), z = getZ(), h = getHeight(), r = getWidth();
        w.spawnParticles(ParticleTypes.FLASH, x, y + h * 0.5, z, 5, r * 0.3, h * 0.3, r * 0.3, 0);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.SQUID_INK, x, y + h * 0.5, z, 150 * form, r * 0.5, h * 0.5, r * 0.5, 0.3);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + h * 0.5, z, 80 * form, r * 0.5, h * 0.5, r * 0.5, 0.15);
        w.spawnParticles(ParticleTypes.SCULK_SOUL, x, y + h * 0.5, z, 40 * form, r * 0.5, h * 0.5, r * 0.5, 0.1);
        w.spawnParticles(ParticleTypes.END_ROD, x, y + h * 0.5, z, 60 * form, r * 0.5, h * 0.5, r * 0.5, 0.4);
        ring(w, ParticleTypes.SQUID_INK, getPos(), r * 0.7, 60, 0.3, 0.6);
        ring(w, ParticleTypes.END_ROD, getPos(), r, 60, 0.3, 0.3);
        lightning(w, x, y, z);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 4f, 0.7f);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 4f, 0.8f);
        w.playSound(null, x, y, z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 3f, 0.6f);
        transformTicks = 40;
    }

    private void lightning(ServerWorld w, double x, double y, double z) {
        LightningEntity l = EntityType.LIGHTNING_BOLT.create(w);
        if (l != null) {
            l.refreshPositionAndAngles(x, y, z, 0, 0);
            l.setCosmetic(true);
            w.spawnEntity(l);
        }
    }

    private void ring(ServerWorld w, ParticleEffect pe, Vec3d c, double r, int n, double dy, double speed) {
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            w.spawnParticles(pe, c.x + Math.cos(a) * r, c.y + dy, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, speed);
        }
    }

    // ---------- tick ----------
    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) { clientTick(); return; }

        PlayerEntity owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator()) { discard(); return; }
        SusanooMod.ACTIVE.add(ownerUuid);
        follow(owner);

        // "Chakra" : leger cout en faim
        if (age % 20 == 0 && !owner.isCreative()) owner.addExhaustion(0.2f * form);

        if (transformTicks > 0 && getWorld() instanceof ServerWorld sw) {
            transformTicks--;
            double a = transformTicks * 0.6, rr = getWidth() * 0.6, hy = getHeight() * (1 - transformTicks / 40.0);
            for (int k = 0; k < 4; k++) {
                double aa = a + k * Math.PI / 2;
                sw.spawnParticles(ParticleTypes.SQUID_INK, getX() + Math.cos(aa) * rr, getY() + hy, getZ() + Math.sin(aa) * rr, 2, 0.1, 0.1, 0.1, 0.02);
                sw.spawnParticles(ParticleTypes.END_ROD, getX() + Math.cos(aa) * rr, getY() + hy, getZ() + Math.sin(aa) * rr, 1, 0, 0, 0, 0);
            }
        }

        if (cooldown > 0) cooldown--;
        if (specialCooldown > 0) specialCooldown--;

        if (attackTicks > 0) {
            attackTicks--;
            dataTracker.set(ATTACK_T, attackTicks);
            if (attackTicks == IMPACT_TICK) impact();
            if (attackTicks == 0) locked = false;
        } else if (cooldown == 0) {
            autoTarget(owner);
        }
    }

    private void follow(PlayerEntity o) {
        double back = 2.0 + getWidth() * 0.5;
        Vec3d off = Vec3d.fromPolar(0, o.getYaw()).multiply(-back);
        double tx = o.getX() + off.x, ty = o.getY(), tz = o.getZ() + off.z;
        double nx, ny, nz;
        if (squaredDistanceTo(tx, ty, tz) > 256) { nx = tx; ny = ty; nz = tz; }
        else {
            nx = MathHelper.lerp(0.35, getX(), tx);
            ny = MathHelper.lerp(0.35, getY(), ty);
            nz = MathHelper.lerp(0.35, getZ(), tz);
        }
        float target = (locked && attackTicks > 0) ? lockYaw : o.getYaw();
        float yaw = MathHelper.stepAngleTowards(getYaw(), target, 25f);
        refreshPositionAndAngles(nx, ny, nz, yaw, 0);
        setBodyYaw(yaw);
        setHeadYaw(yaw);

        double dx = o.getX() - lastOx, dz = o.getZ() - lastOz;
        boolean moving = dx * dx + dz * dz > 0.0004;
        lastOx = o.getX(); lastOz = o.getZ();
        if (moving != dataTracker.get(WALK_T)) dataTracker.set(WALK_T, moving);
    }

    private void clientTick() {
        renderScale += (scaleOf(form) - renderScale) * 0.07f;
        var r = random;
        for (int i = 0; i < form * 3; i++) {
            double x = getX() + (r.nextDouble() - 0.5) * getWidth();
            double y = getY() + r.nextDouble() * getHeight();
            double z = getZ() + (r.nextDouble() - 0.5) * getWidth();
            getWorld().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0.02, 0);
            if (r.nextInt(3) == 0) getWorld().addParticle(ParticleTypes.SQUID_INK, x, y, z, 0, 0.03, 0);
        }
    }

    // ---------- attaques ----------
    private void autoTarget(PlayerEntity owner) {
        double reach = REACH[form];
        Box box = getBoundingBox().expand(reach, reach / 2, reach);
        List<LivingEntity> list = getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != this && e != owner && e instanceof Monster);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = squaredDistanceTo(e);
            if (d < bd && d <= reach * reach) { bd = d; best = e; }
        }
        LivingEntity att = owner.getAttacker();
        if (best == null && att != null && att.isAlive() && att != this && squaredDistanceTo(att) <= reach * reach) best = att;
        if (best != null) start(false, best);
    }

    /** Declenche par les touches R (normal) / V (special). */
    public void manual(boolean special) {
        if (attackTicks > 0) return;
        PlayerEntity o = getOwner();
        if (special) {
            if (specialCooldown > 0) {
                if (o != null) o.sendMessage(Text.literal("Technique spéciale prête dans " + (specialCooldown / 20 + 1) + " s"), true);
                return;
            }
            specialCooldown = SPECIAL_CD;
        }
        start(special, null);
    }

    private void start(boolean special, LivingEntity target) {
        attackTicks = ATTACK_LEN;
        pendingSpecial = special;
        cooldown = AUTO_CD[form];
        if (target != null) {
            double dx = target.getX() - getX(), dz = target.getZ() - getZ();
            lockYaw = (float) (MathHelper.atan2(dz, dx) * 57.29577951308232) - 90f;
            locked = true;
        } else locked = false;
        dataTracker.set(ATTACK_T, attackTicks);
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 3f, 0.6f);
    }

    private void impact() {
        if (!(getWorld() instanceof ServerWorld w)) return;
        Vec3d f = Vec3d.fromPolar(0, getYaw());
        Vec3d p = getPos();
        if (pendingSpecial) special(w, f, p); else normal(w, f, p);
        w.playSound(null, p.x, p.y, p.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 4f, 0.6f);
        if (form == 3) w.playSound(null, p.x, p.y, p.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 5f, 0.7f);
    }

    private void normal(ServerWorld w, Vec3d f, Vec3d p) {
        Set<LivingEntity> t = new HashSet<>();
        switch (form) {
            case 1 -> {
                Vec3d c = p.add(f.multiply(5));
                collect(w, c, 6, t); damageAll(w, t, p, 14, 1.3);
                burst(w, c, 4, 0.6);
            }
            case 2 -> {
                Vec3d c = p.add(f.multiply(10));
                collect(w, c, 10, t); damageAll(w, t, p, 40, 2.0);
                arc(w, p, f, 9, 70);
            }
            default -> {
                Vec3d c = p.add(f.multiply(20));
                collect(w, c, 20, t); damageAll(w, t, p, 150, 3.5);
                arc(w, p, f, 18, 90);
                burst(w, c, 12, 1.2);
                w.createExplosion(this, c.x, c.y, c.z, 4f, World.ExplosionSourceType.MOB);
            }
        }
    }

    private void special(ServerWorld w, Vec3d f, Vec3d p) {
        Set<LivingEntity> t = new HashSet<>();
        switch (form) {
            case 1 -> { // onde de choc circulaire
                collect(w, p, 13, t); damageAll(w, t, p, 35, 2.5);
                for (int i = 1; i <= 3; i++) ring(w, ParticleTypes.SQUID_INK, p, i * 4, 40 + i * 20, 0.3, 0.2);
                burst(w, p, 10, 1.0);
            }
            case 2 -> { // vague de lame
                for (int d = 4; d <= 48; d += 4) {
                    Vec3d c = p.add(f.multiply(d));
                    collect(w, c, 5, t);
                    w.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y + 3, c.z, 3, 1.5, 2, 1.5, 0);
                    w.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + 3, c.z, 25, 1.5, 3, 1.5, 0.05);
                    w.spawnParticles(ParticleTypes.END_ROD, c.x, c.y + 3, c.z, 5, 1, 2, 1, 0.1);
                }
                damageAll(w, t, p, 90, 2.5);
            }
            default -> { // fendage titanesque
                for (int d : new int[]{10, 26, 42}) {
                    Vec3d c = p.add(f.multiply(d));
                    collect(w, c, 26, t);
                    w.createExplosion(this, c.x, c.y, c.z, 7f, World.ExplosionSourceType.MOB);
                    lightning(w, c.x, c.y, c.z);
                    burst(w, c, 20, 1.5);
                }
                damageAll(w, t, p, 400, 4.5);
                arc(w, p, f, 22, 120);
            }
        }
    }

    private void collect(ServerWorld w, Vec3d c, double r, Set<LivingEntity> out) {
        PlayerEntity owner = getOwner();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(r, r, r),
                x -> x.isAlive() && x != this && x != owner && !(x instanceof SusanooEntity))) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (dx * dx + dz * dz <= r * r) out.add(e);
        }
    }

    private void damageAll(ServerWorld w, Set<LivingEntity> targets, Vec3d origin, float dmg, double knock) {
        PlayerEntity owner = getOwner();
        DamageSource src = owner != null ? w.getDamageSources().playerAttack(owner) : w.getDamageSources().generic();
        for (LivingEntity e : targets) {
            e.timeUntilRegen = 0;
            e.damage(src, dmg);
            Vec3d d = e.getPos().subtract(origin);
            d = new Vec3d(d.x, 0, d.z).normalize();
            e.addVelocity(d.x * knock, 0.4 + 0.1 * knock, d.z * knock);
            e.velocityModified = true;
        }
    }

    private void burst(ServerWorld w, Vec3d c, double r, double speed) {
        w.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 1, c.z, 6, r * 0.3, 0.5, r * 0.3, 0);
        w.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + 1, c.z, 120, r * 0.4, 1, r * 0.4, speed * 0.2);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 1, c.z, 60, r * 0.4, 1, r * 0.4, speed * 0.1);
        ring(w, ParticleTypes.SQUID_INK, c, r, 50, 0.3, speed * 0.3);
    }

    private void arc(ServerWorld w, Vec3d p, Vec3d f, double radius, int deg) {
        for (int a = -deg; a <= deg; a += 5) {
            Vec3d d = f.rotateY((float) Math.toRadians(a)).multiply(radius);
            for (double hy : new double[]{0.15, 0.4, 0.7}) {
                double x = p.x + d.x, y = p.y + getHeight() * hy, z = p.z + d.z;
                w.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0, 0, 0, 0);
                w.spawnParticles(ParticleTypes.SQUID_INK, x, y, z, 3, 0.3, 0.3, 0.3, 0.03);
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 1, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    // ---------- sauvegarde ----------
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Form", form);
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Form")) setForm(MathHelper.clamp(nbt.getInt("Form"), 1, 3));
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
    }
}
