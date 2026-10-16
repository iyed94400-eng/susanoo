package com.susano.client;

import com.susano.SusanooEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class SusanooRenderer extends LivingEntityRenderer<SusanooEntity, SusanooModel> {
    static final Identifier TEX = new Identifier("susanoo", "textures/entity/susanoo.png");

    public SusanooRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SusanooModel(ctx.getPart(SusanooClient.LAYER)), 0f);
        addFeature(new AuraFeature(this));
    }

    @Override public Identifier getTexture(SusanooEntity e) { return TEX; }

    @Override
    protected void scale(SusanooEntity e, MatrixStack m, float tickDelta) {
        float s = e.getRenderScale();
        m.scale(s, s, s);
    }

    @Override protected int getBlockLight(SusanooEntity e, BlockPos pos) { return 15; }
    @Override protected boolean hasLabel(SusanooEntity e) { return false; }

    /** Aura noire : deux coques translucides noires qui pulsent autour du corps. */
    static class AuraFeature extends FeatureRenderer<SusanooEntity, SusanooModel> {
        AuraFeature(FeatureRendererContext<SusanooEntity, SusanooModel> ctx) { super(ctx); }

        @Override
        public void render(MatrixStack m, VertexConsumerProvider vp, int light, SusanooEntity e,
                           float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            float pulse = 0.35f + 0.15f * MathHelper.sin(age * 0.15f);
            float[] scales = {1.06f, 1.14f};
            float[] alphas = {pulse, pulse * 0.5f};
            for (int i = 0; i < 2; i++) {
                m.push();
                m.translate(0, 1.5, 0);              // pieds du modele (24 px)
                m.scale(scales[i], scales[i], scales[i]);
                m.translate(0, -1.5, 0);
                getContextModel().render(m, vp.getBuffer(RenderLayer.getEntityTranslucent(TEX)),
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0f, 0f, 0f, alphas[i]);
                m.pop();
            }
        }
    }
}
