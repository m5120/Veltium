package com.example.mixin.client;

import com.example.TemplateModClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderMixin {
    // мапа для зберігання даних про ентитки, щоб не лагало
    private static final Map<Entity, VeltiumEntityRenderData> veltiumEntityData = new ConcurrentHashMap<>();
    private static long veltiumLastCleanup = 0;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void veltiumOptimizeEntityRender(Entity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
        if (!TemplateModClient.config.shouldCullEntities()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.gameRenderer == null) return;

        long currentTime = System.currentTimeMillis();
        double distanceSquared = client.player.squaredDistanceTo(entity);

        VeltiumEntityRenderData data = veltiumEntityData.computeIfAbsent(entity, e -> new VeltiumEntityRenderData());

        // якщо ентитка далеко або не треба її рендерити - скіпаємо
        if (veltiumShouldCullEntity(entity, client, distanceSquared, currentTime, data)) {
            ci.cancel();
            return;
        }

        data.veltiumLastRenderTime = currentTime;

        // прибираємо старі дані щоб не засмічувати пам'ять
        veltiumCleanupOldEntities(currentTime);
    }

    private boolean veltiumShouldCullEntity(Entity entity, MinecraftClient client, double distanceSquared, long currentTime, VeltiumEntityRenderData data) {
        int optimizationLevel = TemplateModClient.config.optimizationLevel;

        // для різних типів ентиток різні правила
        if (entity instanceof PlayerEntity) {
            return veltiumShouldCullPlayer(entity, distanceSquared, currentTime, data, optimizationLevel);
        }

        if (entity instanceof LivingEntity) {
            return veltiumShouldCullLivingEntity((LivingEntity) entity, client, distanceSquared, currentTime, data, optimizationLevel);
        }

        return veltiumShouldCullGenericEntity(entity, client, distanceSquared, currentTime, data, optimizationLevel);
    }

    private boolean veltiumShouldCullPlayer(Entity entity, double distanceSquared, long currentTime, VeltiumEntityRenderData data, int optimizationLevel) {
        if (optimizationLevel < 2) return false;

        // дуже далеких гравців можна рендерити рідше
        if (distanceSquared > 16384) {
            return currentTime - data.veltiumLastRenderTime < 200;
        }

        // середня дистанція - теж не критично
        if (distanceSquared > 4096 && optimizationLevel >= 3) {
            return currentTime - data.veltiumLastRenderTime < 100;
        }

        return false;
    }

    private boolean veltiumShouldCullLivingEntity(LivingEntity entity, MinecraftClient client, double distanceSquared, long currentTime, VeltiumEntityRenderData data, int optimizationLevel) {
        // якщо ентитка світиться або має кастомне ім'я - не чіпаємо
        if (entity.isGlowing() || entity.hasCustomName()) return false;

        if (optimizationLevel >= 2) {
            if (distanceSquared > 1024) {
                boolean isMoving = entity.getVelocity().lengthSquared() > 0.01;
                boolean recentlyMoved = currentTime - data.veltiumLastMovementTime < 1000;

                // якщо ентитка стоїть на місці - можна рендерити рідше
                if (!isMoving && !recentlyMoved) {
                    return currentTime - data.veltiumLastRenderTime < veltiumGetUpdateInterval(distanceSquared, optimizationLevel);
                }

                // запамятовуємо коли вона рухалась останній раз
                if (isMoving) {
                    data.veltiumLastMovementTime = currentTime;
                }
            }
        }

        if (optimizationLevel >= 3) {
            // тварини далеко не так важливі
            if (distanceSquared > 256 && entity instanceof AnimalEntity) {
                if (!entity.isOnGround() && entity.getVelocity().y < -0.5) {
                    return false; // падаючу тварину краще показати
                }
                return currentTime - data.veltiumLastRenderTime < 150;
            }

            // моби без цілі теж не критичні
            if (distanceSquared > 64 && entity instanceof MobEntity) {
                MobEntity mob = (MobEntity) entity;
                if (mob.getTarget() == null && mob.getVelocity().lengthSquared() < 0.001) {
                    return currentTime - data.veltiumLastRenderTime < 250;
                }
            }
        }

        if (optimizationLevel >= 4) {
            // якщо ентитка не в полі зору - навіщо її рендерити
            if (distanceSquared > 2304 && !veltiumIsEntityVisible(entity, client)) {
                return currentTime - data.veltiumLastRenderTime < 500;
            }
        }

        return false;
    }

    private boolean veltiumShouldCullGenericEntity(Entity entity, MinecraftClient client, double distanceSquared, long currentTime, VeltiumEntityRenderData data, int optimizationLevel) {
        if (optimizationLevel < 2) return false;

        // особливі ентитки завжди рендеримо
        if (entity.hasCustomName() || entity.isGlowing()) return false;

        if (distanceSquared > 2048) {
            boolean isMoving = entity.getVelocity().lengthSquared() > 0.001;
            if (!isMoving) {
                return currentTime - data.veltiumLastRenderTime < veltiumGetUpdateInterval(distanceSquared, optimizationLevel);
            }
        }

        if (optimizationLevel >= 3 && distanceSquared > 512) {
            if (!veltiumIsEntityVisible(entity, client)) {
                return currentTime - data.veltiumLastRenderTime < 400;
            }
        }

        return false;
    }

    private int veltiumGetUpdateInterval(double distanceSquared, int optimizationLevel) {
        // чим далі ентитка тим рідше оновлюємо
        if (distanceSquared > 16384) return 400 + (optimizationLevel * 100);
        if (distanceSquared > 4096) return 200 + (optimizationLevel * 50);
        if (distanceSquared > 1024) return 100 + (optimizationLevel * 25);
        return 50;
    }

    private boolean veltiumIsEntityVisible(Entity entity, MinecraftClient client) {
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) return true;

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Vec3d entityPos = entity.getPos();
        Vec3d direction = entityPos.subtract(cameraPos).normalize();

        float yaw = client.gameRenderer.getCamera().getYaw();
        float pitch = client.gameRenderer.getCamera().getPitch();

        // математика для визначення чи видно ентитку
        double cameraYawRad = Math.toRadians(-yaw);
        double cameraPitchRad = Math.toRadians(-pitch);

        Vec3d cameraDirection = new Vec3d(
                Math.sin(cameraYawRad) * Math.cos(cameraPitchRad),
                Math.sin(cameraPitchRad),
                -Math.cos(cameraYawRad) * Math.cos(cameraPitchRad)
        );

        double dotProduct = direction.dotProduct(cameraDirection);
        return dotProduct > 0.3; // якщо кут не дуже великий то видно
    }

    private void veltiumCleanupOldEntities(long currentTime) {
        if (currentTime - veltiumLastCleanup < 5000) return;

        // видаляємо дані про ентитки які вже не існують або давно не рендерились
        veltiumEntityData.entrySet().removeIf(entry -> {
            Entity entity = entry.getKey();
            return entity.isRemoved() || currentTime - entry.getValue().veltiumLastRenderTime > 30000;
        });

        veltiumLastCleanup = currentTime;
    }

    // просто клас для зберігання даних про ентитку
    private static class VeltiumEntityRenderData {
        long veltiumLastRenderTime = 0;
        long veltiumLastMovementTime = 0;
    }
}