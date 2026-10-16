package com.susano.client;

import com.susano.SusanooEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/** Modele code en cubes. Origine : haut du torse (y=0), pieds a y=24 (modele de 2 blocs, mis a l'echelle par le renderer). */
public class SusanooModel extends EntityModel<SusanooEntity> {
    private final ModelPart root, head, horns, helmet, body, ribs, armor, skirt, rArm, lArm, sword, rLeg, lLeg;

    public SusanooModel(ModelPart root) {
        this.root = root;
        head = root.getChild("head");
        horns = head.getChild("horns");
        helmet = head.getChild("helmet");
        body = root.getChild("body");
        ribs = root.getChild("ribs");
        armor = body.getChild("armor");
        skirt = body.getChild("skirt");
        rArm = root.getChild("right_arm");
        lArm = root.getChild("left_arm");
        sword = rArm.getChild("sword");
        rLeg = root.getChild("right_leg");
        lLeg = root.getChild("left_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData md = new ModelData();
        ModelPartData r = md.getRoot();

        ModelPartData head = r.addChild("head", ModelPartBuilder.create().uv(0, 0).cuboid(-4, -8, -4, 8, 8, 8), ModelTransform.NONE);
        head.addChild("horns", ModelPartBuilder.create().uv(0, 0).cuboid(-6, -13, -1, 2, 6, 2).cuboid(4, -13, -1, 2, 6, 2), ModelTransform.NONE);
        head.addChild("helmet", ModelPartBuilder.create().uv(0, 0).cuboid(-5, -9, -5, 10, 4, 10).cuboid(-1, -15, -7, 2, 7, 12), ModelTransform.NONE);

        // forme 2/3 : torse plein + armure
        ModelPartData body = r.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-5, 0, -3, 10, 12, 6), ModelTransform.NONE);
        body.addChild("armor", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-11, -2, -4, 6, 5, 8).cuboid(5, -2, -4, 6, 5, 8)
                .cuboid(-4, 1, -3.8f, 8, 6, 1), ModelTransform.NONE);
        body.addChild("skirt", ModelPartBuilder.create().uv(0, 0).cuboid(-6, 11, -4, 12, 6, 8), ModelTransform.NONE);

        // forme 1 : cage thoracique
        ModelPartBuilder rb = ModelPartBuilder.create().uv(0, 0).cuboid(-1, 0, 1, 2, 12, 2).cuboid(-1, 0, -3.5f, 2, 10, 1);
        for (int i = 0; i < 4; i++) rb.cuboid(-5.5f, 1 + i * 3, -3, 2, 1, 6.5f).cuboid(3.5f, 1 + i * 3, -3, 2, 1, 6.5f);
        r.addChild("ribs", rb, ModelTransform.NONE);

        ModelPartData ra = r.addChild("right_arm", ModelPartBuilder.create().uv(0, 0).cuboid(-3, -2, -2.5f, 5, 16, 5), ModelTransform.pivot(-7.5f, 2, 0));
        ra.addChild("sword", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-4, 14, -1.5f, 8, 2, 3)
                .cuboid(-2, 16, -0.5f, 4, 44, 1), ModelTransform.NONE);
        r.addChild("left_arm", ModelPartBuilder.create().uv(0, 0).cuboid(-2, -2, -2.5f, 5, 16, 5), ModelTransform.pivot(7.5f, 2, 0));

        r.addChild("right_leg", ModelPartBuilder.create().uv(0, 0).cuboid(-2.5f, 0, -2.5f, 5, 12, 5), ModelTransform.pivot(-3, 12, 0));
        r.addChild("left_leg", ModelPartBuilder.create().uv(0, 0).cuboid(-2.5f, 0, -2.5f, 5, 12, 5), ModelTransform.pivot(3, 12, 0));
        return TexturedModelData.of(md, 64, 64);
    }

    @Override
    public void setAngles(SusanooEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        int f = e.getForm();
        boolean armored = f >= 2;
        ribs.visible = f == 1;
        body.visible = armored;
        helmet.visible = armored;
        sword.visible = armored;
        rLeg.visible = lLeg.visible = f == 3;

        head.yaw = headYaw * 0.017453292f;
        head.pitch = headPitch * 0.017453292f;

        float breathe = MathHelper.sin(age * 0.08f) * 0.04f;
        rArm.pitch = -1.1f + breathe; rArm.roll = 0.12f; rArm.yaw = 0;
        lArm.pitch = -0.5f - breathe; lArm.roll = -0.12f; lArm.yaw = 0;
        if (f == 1) { // bras ouverts, griffes en avant
            rArm.pitch = lArm.pitch = -1.4f + breathe;
            rArm.roll = 0.35f; lArm.roll = -0.35f;
        }

        float p = e.getAttackProgress();
        if (p >= 0) {
            float pitch = p < 0.35f
                    ? MathHelper.lerp(p / 0.35f, -1.1f, -3.1f)      // on leve
                    : MathHelper.lerp((p - 0.35f) / 0.65f, -3.1f, -0.25f); // on abat
            rArm.pitch = pitch; rArm.roll = 0.05f;
            lArm.pitch = f == 1 ? pitch : -0.9f;
            if (f == 1) lArm.roll = -0.05f;
        }

        float sw = e.isWalking() ? MathHelper.sin(age * 0.25f) * 0.7f : 0;
        rLeg.pitch = sw; lLeg.pitch = -sw;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
        root.render(matrices, vertices, light, overlay, r, g, b, a);
    }
}
