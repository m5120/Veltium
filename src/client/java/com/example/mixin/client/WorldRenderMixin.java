package com.example.mixin.client;

import com.example.Veltium;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(WorldRenderer.class)
public class WorldRenderMixin {

    // просто статики для оптимізаціі та всякого такого
    private static long lastOptCheck = 0;
    private static long lastFpsCheck = 0;
    private static int cachedFps = 30;
    private static boolean isPojavLauncher = false;
    private static boolean lowPerfMode = true;
    private static int frameCounter = 0;
    private static long lastMemCheck = 0;
    private static boolean lowMemoryMode = false;

    private static final Map<BlockPos, Long> blockOutlineCache = new ConcurrentHashMap<>();

    // динамічні штуки які змінюються по ходу гри
    private static int dynamicRenderDist = 4;
    private static int particleReduction = 4;
    private static int skyRenderSkip = 3;

    static {
        // детектимо pojav launcher якось там
        String vmName = System.getProperty("java.vm.name", "").toLowerCase();
        String osName = System.getProperty("os.name", "").toLowerCase();
        isPojavLauncher = vmName.contains("android") || osName.contains("android")
                || System.getProperty("pojav.launcher") != null;

        if (isPojavLauncher) {
            // для pojav треба більше оптимізувати бо телефон це не пк
            dynamicRenderDist = 3;
            particleReduction = 6;
            skyRenderSkip = 4;
        }
    }

    @Shadow
    private ChunkBuilder chunkBuilder;

    @Inject(method = "render", at = @At("HEAD"))
    private void optimizeWorldRender(CallbackInfo ci) {
        if (Veltium.config.optimizationLevel >= 1 || isPojavLauncher) {
            long currentTime = System.currentTimeMillis();
            frameCounter++;

            // перевіряємо fps кожну секунду щоб не спамити
            if (currentTime - lastFpsCheck > 1000) {
                cachedFps = MinecraftClient.getInstance().getCurrentFps();
                lastFpsCheck = currentTime;

                // визначаємо чи низька продуктивність зараз
                lowPerfMode = isPojavLauncher ? cachedFps < 25 : cachedFps < 30;
                adjustDynamicRenderDistance();
            }

            // для pojav перевіряємо пам'ять частіше бо там мало оперативки
            if (isPojavLauncher && currentTime - lastMemCheck > 3000) {
                checkMemoryStatus();
                lastMemCheck = currentTime;
            }

            // очищаємо кеш контурів блоків щоб не засмічувати
            if (currentTime - lastOptCheck > (isPojavLauncher ? 1500 : 3000)) {
                blockOutlineCache.entrySet().removeIf(e -> currentTime - e.getValue() > 1000);
                lastOptCheck = currentTime;
            }
        }
    }

    @Inject(method = "setupTerrain", at = @At("HEAD"), cancellable = true)
    private void optimizeTerrainSetup(Camera camera, CallbackInfo ci) {
        // якщо дуже лагає то скіпаємо деякі кадри terrain
        if (Veltium.config.optimizationLevel >= 2 && lowPerfMode) {
            int forcedDistance = Math.max(4, dynamicRenderDist / 2);
            Vec3d camPos = camera.getPos();
            // тут можна було б зробити більше але поки так норм
        }
    }

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void optimizeBlockOutline(CallbackInfo ci) {
        // оптимізуємо контури блоків бо вони жрут фпс
        if (Veltium.config.optimizationLevel >= 1 || isPojavLauncher) {
            if (isPojavLauncher && (lowPerfMode || lowMemoryMode)) {
                // для pojav скіпаємо кожен другий кадр
                if (frameCounter % 2 != 0) {
                    ci.cancel();
                    return;
                }
            } else if (lowPerfMode) {
                ci.cancel();
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.crosshairTarget != null && client.crosshairTarget.getPos() != null) {
                BlockPos pos = BlockPos.ofFloored(client.crosshairTarget.getPos());
                Long lastRender = blockOutlineCache.get(pos);
                long now = System.currentTimeMillis();

                int timeout = isPojavLauncher ? 100 : 50;
                if (lastRender != null && now - lastRender < timeout) {
                    ci.cancel();
                    return;
                }

                blockOutlineCache.put(pos, now);
            }
        }
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    private void optimizeParticleRender(CallbackInfo ci) {
        // зменшуємо частички бо їх дуже багато і вони лагають
        if (isPojavLauncher) {
            if (frameCounter % particleReduction == 0) {
                ci.cancel();
            }
        } else if (Veltium.config.optimizationLevel >= 3 && lowPerfMode) {
            if (frameCounter % 3 == 0) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void optimizeSkyRender(CallbackInfo ci) {
        // небо не так важно рендерити кожен кадр бо воно не дуже змінюється
        if (isPojavLauncher) {
            if (frameCounter % skyRenderSkip != 0) {
                ci.cancel();
            }
        } else if (Veltium.config.optimizationLevel >= 3 && lowPerfMode) {
            if (frameCounter % 2 == 0) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void optimizeWeatherRender(CallbackInfo ci) {
        // погода теж не критична для гри взагалі
        if (isPojavLauncher) {
            if (lowMemoryMode && frameCounter % 8 != 0) {
                ci.cancel();
            } else if (frameCounter % 6 != 0) {
                ci.cancel();
            }
        } else if (Veltium.config.optimizationLevel >= 2 && lowPerfMode) {
            if (frameCounter % 4 != 0) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void optimizeCloudsRender(CallbackInfo ci) {
        // хмари взагалі не потрібні якщо лагає і так
        if (isPojavLauncher && (lowPerfMode || lowMemoryMode)) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;renderEntities(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/client/render/MatrixStack;)V"
            ),
            index = 0
    )
    private double optimizeEntityRenderDistance(double distance) {
        // обмежуємо дистанцію рендеру ентитетів бо вони теж жрут fps
        if (isPojavLauncher) {
            if (lowMemoryMode) return Math.min(distance, 16.0);
            if (lowPerfMode) return Math.min(distance, 24.0);
            return Math.min(distance, 32.0);
        } else if (Veltium.config.optimizationLevel >= 2 && lowPerfMode) {
            return Math.min(distance, 32.0);
        }
        return distance;
    }

    // пустий метод але може потрібен буде колись
    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void optimizeTerrainSetupExtra(CallbackInfo ci) {
        // тут нічого поки що але залишимо на всякий випадок
    }

    private void adjustDynamicRenderDistance() {
        // динамічно регулюємо дистанцію рендерингу залежно від fps
        if (isPojavLauncher) {
            if (cachedFps > 35) {
                dynamicRenderDist = Math.min(8, dynamicRenderDist + 1);
            } else if (cachedFps < 15) {
                dynamicRenderDist = Math.max(2, dynamicRenderDist - 2);
            } else if (cachedFps < 25) {
                dynamicRenderDist = Math.max(3, dynamicRenderDist - 1);
            }
        } else {
            if (cachedFps > 60) {
                dynamicRenderDist = Math.min(32, dynamicRenderDist + 1);
            } else if (cachedFps < 30) {
                dynamicRenderDist = Math.max(4, dynamicRenderDist - 2);
            }
        }
    }

    private void checkMemoryStatus() {
        // перевіряємо стан пам'яті щоб не вилетіти з гри
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        double usage = (double) used / max;

        lowMemoryMode = usage > 0.8;

        // якщо дуже мало пам'яті то примусово gc
        if (usage > 0.85) {
            System.gc();
        }
    }
}