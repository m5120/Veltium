package com.example.mixin.client;

import com.example.Veltium;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundMixin {

    // змінні для звуку - originalVolume та volumeScaled
    private static double originalVolume = 1.0;
    private static boolean volumeScaled = false;
    private static int audioTickCounter = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void OptimizeAudio(CallbackInfo ci) {
        audioTickCounter++;
        
        if (Veltium.config.optimizationLevel >= 2) {
            MinecraftClient client = MinecraftClient.getInstance();
            GameOptions options = client.options;

            double currentVolume = options.getSoundVolumeOption(SoundCategory.MASTER).getValue();
            int currentFps = client.getCurrentFps();

            // якщо фпс низький і ще не зменшували звук
            if (currentFps < 30 && !volumeScaled) {
                originalVolume = currentVolume;
                options.getSoundVolumeOption(SoundCategory.MASTER).setValue(originalVolume * 0.5d);
                volumeScaled = true;
            }
            // якщо фпс нормальний і звук був зменшений - повертаємо назад
            else if (currentFps >= 35 && volumeScaled) {
                options.getSoundVolumeOption(SoundCategory.MASTER).setValue(originalVolume);
                volumeScaled = false;
            }
        }
    }
}