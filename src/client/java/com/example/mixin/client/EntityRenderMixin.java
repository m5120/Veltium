package com.example.mixin.client;

import com.example.Veltium;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Mixin(EntityRenderer.class)
public class EntityRenderMixin {
    @Unique
    private static final Map<Integer, VeltiumEntityRenderData> veltium$entityData = new ConcurrentHashMap<>();
    @Unique
    private static long veltium$lastCleanup = 0;
    @Unique
    private static final int CLEANUP_INTERVAL = 10000; // 10 секунд
    @Unique
    private static final int DATA_EXPIRE_TIME = 60000; // 60 секунд

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void veltium$optimizeEntityRender(Entity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
        // Ранній вихід якщо оптимізація вимкнена
        if (!Veltium.config.shouldCullEntities() || Veltium.config.optimizationLevel == 0) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Використовуємо entityId замість самого entity для уникнення memory leaks
        int entityId = entity.getId();
        double distanceSquared = client.player.squaredDistanceTo(entity);

        VeltiumEntityRenderData data = veltium$entityData.computeIfAbsent(entityId,
                id -> new VeltiumEntityRenderData(entity));

        // Перевірка чи entity ще існує
        if (data.entityRef.get() == null || data.entityRef.get().isRemoved()) {
            veltium$entityData.remove(entityId);
            return;
        }

        if (veltium$shouldCullEntity(entity, client, distanceSquared, currentTime, data)) {
            ci.cancel();
            return;
        }

        data.lastRenderTime = currentTime;

        // Cleanup викликаємо рідше для кращої продуктивності
        if (currentTime - veltium$lastCleanup > CLEANUP_INTERVAL) {
            veltium$cleanupOldEntities(currentTime);
        }
    }

    @Unique
    private boolean veltium$shouldCullEntity(Entity entity, MinecraftClient client, double distanceSquared, long currentTime, VeltiumEntityRenderData data) {
        int optimizationLevel = Veltium.config.optimizationLevel;

        // Ніколи не каллимо гравців поруч або важливі entity
        if (entity instanceof PlayerEntity) {
            return veltium$shouldCullPlayer(entity, distanceSquared, currentTime, data, optimizationLevel);
        }

        if (entity instanceof LivingEntity) {
            return veltium$shouldCullLivingEntity((LivingEntity) entity, client, distanceSquared, currentTime, data, optimizationLevel);
        }

        return veltium$shouldCullGenericEntity(entity, client, distanceSquared, currentTime, data, optimizationLevel);
    }

    @Unique
    private boolean veltium$shouldCullPlayer(Entity entity, double distanceSquared, long currentTime, VeltiumEntityRenderData data, int optimizationLevel) {
        // Більш консервативний підхід для гравців
        if (optimizationLevel < 3) return false;

        // Тільки дуже далекі гравці і тільки на максимальній оптимізації
        if (distanceSquared > 20736) { // 144 блоки
            return currentTime - data.lastRenderTime < 300;
        }

        return false;
    }

    @Unique
    private boolean veltium$shouldCullLivingEntity(LivingEntity entity, MinecraftClient client, double distanceSquared, long currentTime, VeltiumEntityRenderData data, int optimizationLevel) {
        // Ніколи не каллимо важливі entity
        if (entity.isGlowing() || entity.hasCustomName() || entity.hasVehicle() || entity.hasPassengers()) {
            return false;
        }

        if (optimizationLevel >= 2) {
            if (distanceSquared > 1600) { // 40 блоків
                boolean isMoving = entity.getVelocity().lengthSquared() > 0.005; // Менш чутливо до руху
                boolean recentlyMoved = currentTime - data.lastMovementTime < 2000;

                if (!isMoving && !recentlyMoved) {
                    return currentTime - data.lastRenderTime < veltium$getUpdateInterval(distanceSquared, optimizationLevel);
                }

                if (isMoving) {
                    data.lastMovementTime = currentTime;
                }
            }
        }

        if (optimizationLevel >= 3) {
            // Тварини
            if (distanceSquared > 576 && entity instanceof AnimalEntity) { // 24 блоки
                // Не каллимо тварин що падають
                if (!entity.isOnGround() && entity.getVelocity().y < -0.3) {
                    return false;
                }
                return currentTime - data.lastRenderTime < 200;
            }

            // Моби
            if (distanceSquared > 400 && entity instanceof MobEntity) { // 20 блоків
                MobEntity mob = (MobEntity) entity;
                if (mob.getTarget() == null && mob.getVelocity().lengthSquared() < 0.002) {
                    return currentTime - data.lastRenderTime < 300;
                }
            }
        }

        return false;
    }

    @Unique
    private boolean veltium$shouldCullGenericEntity(Entity entity, MinecraftClient client, double distanceSquared, long currentTime, VeltiumEntityRenderData data, int optimizationLevel) {
        if (optimizationLevel < 2) return false;

        // Не каллимо важливі entity
        if (entity.hasCustomName() || entity.isGlowing() || entity.hasVehicle() || entity.hasPassengers()) {
            return false;
        }

        if (distanceSquared > 2304) { // 48 блоків
            boolean isMoving = entity.getVelocity().lengthSquared() > 0.002;
            if (!isMoving) {
                return currentTime - data.lastRenderTime < veltium$getUpdateInterval(distanceSquared, optimizationLevel);
            }
        }

        return false;
    }

    @Unique
    private int veltium$getUpdateInterval(double distanceSquared, int optimizationLevel) {
        // Більш консервативні інтервали
        if (distanceSquared > 22500) return 500 + (optimizationLevel * 100); // 150+ блоків
        if (distanceSquared > 10000) return 300 + (optimizationLevel * 50);  // 100+ блоків
        if (distanceSquared > 2500) return 150 + (optimizationLevel * 25);   // 50+ блоків
        return 75;
    }

    @Unique
    private void veltium$cleanupOldEntities(long currentTime) {
        try {
            veltium$entityData.entrySet().removeIf(entry -> {
                VeltiumEntityRenderData data = entry.getValue();
                Entity entity = data.entityRef.get();

                return entity == null ||
                        entity.isRemoved() ||
                        currentTime - data.lastRenderTime > DATA_EXPIRE_TIME;
            });
        } catch (Exception e) {
            // Тихо ігноруємо помилки cleanup'у
        } finally {
            veltium$lastCleanup = currentTime;
        }
    }

    @Unique
    private static class VeltiumEntityRenderData {
        final WeakReference<Entity> entityRef;
        long lastRenderTime = 0;
        long lastMovementTime = 0;

        VeltiumEntityRenderData(Entity entity) {
            this.entityRef = new WeakReference<>(entity);
        }
    }
}