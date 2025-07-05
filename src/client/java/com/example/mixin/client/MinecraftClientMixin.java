package com.example.mixin.client;

import com.example.TemplateModClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    private int ticksSinceLastOptimization = 0;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onMinecraftClientInit(RunArgs args, CallbackInfo ci) {
        if (TemplateModClient.config.fastMath) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        ticksSinceLastOptimization++;

        if (ticksSinceLastOptimization >= 600) {
            ticksSinceLastOptimization = 0;
            performMemoryOptimization();
        }
    }

    private void performMemoryOptimization() {
        if (!TemplateModClient.config.smartMemoryManagement) return;

        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        double memoryUsage = 1.0 - ((double) freeMemory / totalMemory);

        if (memoryUsage > 0.85) {
            System.gc();
        }
    }
}
