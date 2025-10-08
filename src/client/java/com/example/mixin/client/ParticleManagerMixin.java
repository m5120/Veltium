package com.example.mixin.client;

import com.example.Veltium;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

    @Shadow
    private Queue<Particle>[] particles;

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void veltiumOptimizeParticleCreation(ParticleEffect parameters, double x, double y, double z,
                                                 double velocityX, double velocityY, double velocityZ,
                                                 CallbackInfoReturnable<Particle> cir) {

        int currentParticleCount = veltiumGetCurrentParticleCount();
        int maxParticles = Veltium.config.getEffectiveParticleLimit();

        if (currentParticleCount >= maxParticles) {
            cir.setReturnValue(null);
            return;
        }

        if (Veltium.config.optimizeRendering) {
            if (currentParticleCount > maxParticles * 0.8 && Math.random() < 0.3) {
                cir.setReturnValue(null);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void veltiumOptimizeParticleTick(CallbackInfo ci) {
        if (Veltium.config.reduceLag) {
            int currentParticleCount = veltiumGetCurrentParticleCount();
            int maxParticles = Veltium.config.getEffectiveParticleLimit();

            if (currentParticleCount > maxParticles * 0.9) {

            }
        }
    }

    private int veltiumGetCurrentParticleCount() {
        if (particles == null) return 0;

        int count = 0;
        for (Queue<Particle> queue : particles) {
            if (queue != null) {
                count += queue.size();
            }
        }
        return count;
    }
}
