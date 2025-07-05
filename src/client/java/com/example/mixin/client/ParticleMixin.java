package com.example.mixin.client;

import com.example.TemplateModClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class ParticleMixin {
    // незнаю чому статік але працює
    private static long veltiumLastMemoryCheck = 0;
    private static long veltiumLastGCTime = 0;
    private static boolean veltiumShouldCullParticles = false;
    private static int veltiumParticleCounter = 0; // лічильник

    @Inject(method = "tick", at = @At("HEAD"))
    private void veltiumOptimizeParticles(CallbackInfo ci) {
        // якщо вимкнено то нафіг
        if (!TemplateModClient.config.cullParticles || TemplateModClient.config.optimizationLevel < 1) return;

        long currentTime = System.currentTimeMillis();

        // перевіряємо раз в секунду бо частіше не треба
        if (currentTime - veltiumLastMemoryCheck > 1000) {
            Runtime runtime = Runtime.getRuntime();
            long freeMemory = runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            double memoryUsage = 1.0 - ((double) freeMemory / totalMemory);

            // якщо памяті мало то треба різати частинки
            veltiumShouldCullParticles = memoryUsage > 0.70;
            veltiumLastMemoryCheck = currentTime;

            // gc тільки коли зовсім жах
            if (memoryUsage > 0.85 && currentTime - veltiumLastGCTime > 30000) {
                System.gc(); // може допоможе
                veltiumLastGCTime = currentTime;
            }
        }
    }

    @Inject(method = "addParticle", at = @At("HEAD"), cancellable = true)
    private void veltiumCullDistantParticles(CallbackInfo ci) {
        if (!TemplateModClient.config.cullParticles) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return; // на всякий

        veltiumParticleCounter++;

        int cullRate = veltiumGetCullRate(client.getCurrentFps());

        // скіпаємо частинки щоб не лагало
        if (veltiumParticleCounter % cullRate != 0) {
            ci.cancel();
            return;
        }

        // ще більше ріжемо якщо памяті критично мало
        if (veltiumShouldCullParticles && veltiumParticleCounter % 2 == 0) {
            ci.cancel(); // bye bye particle
        }
    }

    private int veltiumGetCullRate(int fps) {
        // чим менше фпс тим більше ріжемо
        if (fps < 20) return 5; // дуже погано
        if (fps < 40) return 3; // так собі
        if (fps < 60) return 2; // майже норм
        return 1; // все ок
    }
}