package com.example.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.render.Camera;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.entity.Entity;
import com.example.config.YACLConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkOptimizer {
    private final YACLConfig config;

    // тут зберігаємо усі дані про чанки та їх стан
    private final Map<Long, ChunkData> chunkCache = new ConcurrentHashMap<>();
    private final Set<Long> emptyChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> culledChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, Integer> chunkPriority = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastUpdateTime = new ConcurrentHashMap<>();

    // система лод щоб далекі чанки рендерились простіше (Погано працює)
    private final Map<Long, Integer> chunkLOD = new ConcurrentHashMap<>();
    private final int[] LOD_DISTANCES = {4, 8, 16, 24, 32};
    private final float[] LOD_DETAIL = {1.0f, 0.8f, 0.6f, 0.4f, 0.2f};

    // дані для frustum culling - щоб не рендерить те що не видно
    private Camera lastCamera;
    private Vec3d cameraPos;
    private Vec3d cameraDirection;
    private final double fovRadians = Math.toRadians(70); // стандартний фов в майнкрафті

    // статистика для дебагу і тд
    private int totalChunks = 0;
    private int culledCount = 0;
    private int emptyCount = 0;
    private long lastCleanup = 0;
    private long lastStatsUpdate = 0;

    // запамятовуємо де був гравець щоб не пересчитувати кожен кадр (економія)
    private int lastPlayerChunkX = Integer.MAX_VALUE;
    private int lastPlayerChunkZ = Integer.MAX_VALUE;

    public ChunkOptimizer(YACLConfig config) {
        this.config = config;
    }

    public void optimizeChunks(MinecraftClient client) {
        if (!config.shouldOptimizeChunks() || client.world == null || client.player == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        updateCamera(client);

        int playerChunkX = (int) client.player.getX() >> 4;
        int playerChunkZ = (int) client.player.getZ() >> 4;

        // оновлюємо тільки коли гравець переміщується в новий чанк (розумно)
        if (playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ) {
            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkZ = playerChunkZ;
            performChunkAnalysis(client.world, playerChunkX, playerChunkZ, currentTime);
        }

        // frustum culling робимо кожен кадр бо камера може рухатись швидко
        if (shouldUseFrustumCulling()) {
            performFrustumCulling(playerChunkX, playerChunkZ);
        }

        // occlusion culling рідше бо він важчий для процесора
        if (shouldUseOcclusionCulling() && currentTime % 3 == 0) {
            performOcclusionCulling(client.world, playerChunkX, playerChunkZ);
        }

        // прибираємо старі дані - інакше пам'ять буде забиватись
        if (currentTime - lastCleanup > getCleanupInterval()) {
            cleanupCache(playerChunkX, playerChunkZ);
            lastCleanup = currentTime;
        }

        updateStatistics(currentTime);
    }

    private void updateCamera(MinecraftClient client) {
        Camera camera = client.gameRenderer.getCamera();
        if (camera != lastCamera) {
            lastCamera = camera;
            cameraPos = camera.getPos();

            // рахуємо куди дивится камера
            float yaw = camera.getYaw();
            float pitch = camera.getPitch();

            double x = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
            double y = -Math.sin(Math.toRadians(pitch));
            double z = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));

            cameraDirection = new Vec3d(x, y, z).normalize();
        }
    }

    private void performChunkAnalysis(ClientWorld world, int playerX, int playerZ, long currentTime) {
        int renderDistance = getRenderDistance();
        Set<Long> activeChunks = new HashSet<>();

        // проходимо по всім чанкам які можуть бути видні
        for (int x = playerX - renderDistance; x <= playerX + renderDistance; x++) {
            for (int z = playerZ - renderDistance; z <= playerZ + renderDistance; z++) {
                long chunkPos = ChunkPos.toLong(x, z);
                activeChunks.add(chunkPos);

                int distance = Math.max(Math.abs(x - playerX), Math.abs(z - playerZ));
                analyzeChunk(world, x, z, distance, currentTime);
            }
        }

        // видаляємо дані про чанки які вже не потрібні (щоб не забивать пам'ять)
        chunkCache.keySet().retainAll(activeChunks);
        emptyChunks.retainAll(activeChunks);
        culledChunks.retainAll(activeChunks);
        chunkPriority.keySet().retainAll(activeChunks);
        chunkLOD.keySet().retainAll(activeChunks);
        lastUpdateTime.keySet().retainAll(activeChunks);

        totalChunks = activeChunks.size();
    }

    private void analyzeChunk(ClientWorld world, int chunkX, int chunkZ, int distance, long currentTime) {
        long chunkPos = ChunkPos.toLong(chunkX, chunkZ);

        // перевіряємо чи треба оновлювати цей чанк (може не варто?)
        Long lastUpdate = lastUpdateTime.get(chunkPos);
        int updateInterval = getUpdateInterval(distance);
        if (lastUpdate != null && currentTime - lastUpdate < updateInterval) {
            return; // ще рано оновлювати
        }

        lastUpdateTime.put(chunkPos, currentTime);

        try {
            WorldChunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk == null) {
                culledChunks.add(chunkPos);
                return;
            }

            ChunkData data = analyzeChunkData(chunk, world, distance);
            chunkCache.put(chunkPos, data);

            // встановлюємо лод рівень залежно від відстані
            if (shouldUseLOD()) {
                int lodLevel = calculateLODLevel(distance);
                chunkLOD.put(chunkPos, lodLevel);
            }

            // рахуємо пріоритет для цього чанку
            int priority = calculateChunkPriority(data, distance);
            chunkPriority.put(chunkPos, priority);

            // якщо чанк пустий або майже пустий то його можна пропустити
            float emptyThreshold = getEmptyChunkThreshold();
            if (data.isEmpty || data.airBlockPercentage > emptyThreshold) {
                emptyChunks.add(chunkPos);
            } else {
                emptyChunks.remove(chunkPos);
            }

        } catch (Exception e) {
            culledChunks.add(chunkPos);
        }
    }

    private ChunkData analyzeChunkData(WorldChunk chunk, ClientWorld world, int distance) {
        ChunkData data = new ChunkData();
        data.isEmpty = chunk.isEmpty();

        if (data.isEmpty) {
            data.airBlockPercentage = 100.0f; // 100% повітря
            return data;
        }

        // детальний настройки що робити залежно від відстані та налаштувань
        int baseSampleRate = getAnalysisSampleRate();
        int sampleRate = Math.max(1, baseSampleRate + distance / 4);
        int totalBlocks = 0;
        int airBlocks = 0;
        int solidBlocks = 0;
        int transparentBlocks = 0;
        int complexBlocks = 0;

        int minY = world.getBottomY();
        int maxY = minY + world.getHeight();

        // проходимо по блокам з певним кроком
        for (int y = minY; y < maxY; y += sampleRate * 2) {
            for (int x = 0; x < 16; x += sampleRate) {
                for (int z = 0; z < 16; z += sampleRate) {
                    BlockPos pos = new BlockPos(
                            (chunk.getPos().x << 4) + x,
                            y,
                            (chunk.getPos().z << 4) + z
                    );

                    BlockState state = chunk.getBlockState(pos);
                    totalBlocks++;

                    if (state.isAir()) {
                        airBlocks++;
                    } else if (state.isOpaque()) {
                        solidBlocks++;
                    } else {
                        transparentBlocks++;
                    }

                    // складні блоки які важко рендерить (типу води)
                    if (state.hasBlockEntity() ||
                            state.getBlock() == Blocks.WATER ||
                            state.getBlock() == Blocks.LAVA ||
                            !state.getCollisionShape(world, pos).isEmpty()) {
                        complexBlocks++;
                    }
                }
            }
        }

        // рахуємо відсотки різних типів блоків
        if (totalBlocks > 0) {
            data.airBlockPercentage = (airBlocks * 100.0f) / totalBlocks;
            data.solidBlockPercentage = (solidBlocks * 100.0f) / totalBlocks;
            data.transparentBlockPercentage = (transparentBlocks * 100.0f) / totalBlocks;
            data.complexBlockPercentage = (complexBlocks * 100.0f) / totalBlocks;
        }

        // рахуємо ентити тільки якщо увімкнено і для близьких чанків
        if (shouldCountEntities() && distance <= getEntityCountDistance()) {
            data.entityCount = countEntities(world, chunk.getPos().x, chunk.getPos().z);
        }

        data.lastAnalysis = System.currentTimeMillis();
        return data;
    }

    private int countEntities(ClientWorld world, int chunkX, int chunkZ) {
        int minY = world.getBottomY();
        int maxY = minY + world.getHeight();

        Box chunkBox = new Box(
                chunkX * 16, minY, chunkZ * 16,
                chunkX * 16 + 16, maxY, chunkZ * 16 + 16
        );

        List<Entity> entities = world.getOtherEntities(null, chunkBox);
        return entities.size(); // просто кількість
    }

    private void performFrustumCulling(int playerX, int playerZ) {
        if (cameraPos == null || cameraDirection == null) return;

        culledCount = 0;
        double fovExtension = getFrustumCullingFOV();

        // проходимо по всім чанкам і перевіряємо чи вони в полі зору
        for (Map.Entry<Long, ChunkData> entry : chunkCache.entrySet()) {
            long chunkPos = entry.getKey();
            int chunkX = ChunkPos.getPackedX(chunkPos);
            int chunkZ = ChunkPos.getPackedZ(chunkPos);

            if (isChunkInFrustum(chunkX, chunkZ, fovExtension)) {
                culledChunks.remove(chunkPos); // видимий чанк
            } else {
                culledChunks.add(chunkPos); // невидимий - пропускаємо
                culledCount++;
            }
        }
    }

    private boolean isChunkInFrustum(int chunkX, int chunkZ, double fovExtension) {
        double chunkCenterX = chunkX * 16 + 8; // центр чанку
        double chunkCenterZ = chunkZ * 16 + 8;

        Vec3d toChunk = new Vec3d(
                chunkCenterX - cameraPos.x,
                0,
                chunkCenterZ - cameraPos.z
        ).normalize();

        // горизонтальний напрямок камери (без Y)
        Vec3d cameraHorizontal = new Vec3d(cameraDirection.x, 0, cameraDirection.z).normalize();

        // розрахунок кута між напрямком камери і напрямком до чанку
        double dotProduct = cameraHorizontal.dotProduct(toChunk);
        double threshold = Math.cos(fovRadians / 2.0 + fovExtension);

        return dotProduct >= threshold;
    }

    private void performOcclusionCulling(ClientWorld world, int playerX, int playerZ) {
        float solidThreshold = getOcclusionSolidThreshold();
        int minDistance = getOcclusionMinDistance();

        // простенькі евристики для occlusion culling
        for (Map.Entry<Long, ChunkData> entry : chunkCache.entrySet()) {
            long chunkPos = entry.getKey();
            ChunkData data = entry.getValue();
            int chunkX = ChunkPos.getPackedX(chunkPos);
            int chunkZ = ChunkPos.getPackedZ(chunkPos);

            int distance = Math.max(Math.abs(chunkX - playerX), Math.abs(chunkZ - playerZ));

            // далекі чанки з малою кількістю блоків можна пропускати
            if (distance > 16 && data.solidBlockPercentage < solidThreshold) {
                culledChunks.add(chunkPos);
                continue;
            }

            // чанки які заховані за іншими щільними чанками
            if (distance > minDistance && isChunkOccluded(chunkX, chunkZ, playerX, playerZ)) {
                culledChunks.add(chunkPos);
            }
        }
    }

    private boolean isChunkOccluded(int chunkX, int chunkZ, int playerX, int playerZ) {
        // дивимось чи є щільні чанки між гравцем і цільовим чанком
        int dx = chunkX - playerX;
        int dz = chunkZ - playerZ;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));

        if (steps <= 1) return false; // занадто близько

        float occlusionThreshold = getOcclusionThreshold();

        for (int i = 1; i < steps; i++) {
            int midX = playerX + (dx * i) / steps;
            int midZ = playerZ + (dz * i) / steps;
            long midChunkPos = ChunkPos.toLong(midX, midZ);

            ChunkData midData = chunkCache.get(midChunkPos);
            if (midData != null && midData.solidBlockPercentage > occlusionThreshold) {
                return true; // заслонено
            }
        }

        return false;
    }

    private int calculateLODLevel(int distance) {
        for (int i = 0; i < LOD_DISTANCES.length; i++) {
            if (distance <= LOD_DISTANCES[i]) {
                return i;
            }
        }
        return LOD_DISTANCES.length - 1; // максимальний лод
    }

    private int calculateChunkPriority(ChunkData data, int distance) {
        int priority = 100 - distance; // чанки ближче мають вищий пріоритет

        // бонуси до пріоритету
        if (data.entityCount > 0) priority += getEntityPriorityBonus();
        if (data.complexBlockPercentage > 20.0f) priority += getComplexBlockPriorityBonus();
        if (data.transparentBlockPercentage > 30.0f) priority += getTransparentBlockPriorityBonus();

        return Math.max(1, priority);
    }

    private int getUpdateInterval(int distance) {
        int baseInterval = getBaseUpdateInterval();

        // близькі чанки оновлюємо частіше
        if (distance <= 4) return baseInterval;
        if (distance <= 8) return baseInterval * 2;
        if (distance <= 16) return baseInterval * 4;
        return baseInterval * 10; // дуже далекі чанки оновлюємо рідко
    }

    private void cleanupCache(int playerX, int playerZ) {
        int maxDistance = getRenderDistance() + getCleanupExtraDistance();

        // видалення чанків які дуже далеко
        chunkCache.entrySet().removeIf(entry -> {
            long chunkPos = entry.getKey();
            int x = ChunkPos.getPackedX(chunkPos);
            int z = ChunkPos.getPackedZ(chunkPos);
            return Math.max(Math.abs(x - playerX), Math.abs(z - playerZ)) > maxDistance;
        });

        // прибираємо з усіх колекцій
        emptyChunks.removeIf(this::isChunkTooFar);
        culledChunks.removeIf(this::isChunkTooFar);
        chunkPriority.keySet().removeIf(this::isChunkTooFar);
        chunkLOD.keySet().removeIf(this::isChunkTooFar);
        lastUpdateTime.keySet().removeIf(this::isChunkTooFar);
    }

    private boolean isChunkTooFar(long chunkPos) {
        int x = ChunkPos.getPackedX(chunkPos);
        int z = ChunkPos.getPackedZ(chunkPos);
        int distance = Math.max(Math.abs(x - lastPlayerChunkX), Math.abs(z - lastPlayerChunkZ));
        return distance > getRenderDistance() + getCleanupExtraDistance();
    }

    private void updateStatistics(long currentTime) {
        if (currentTime - lastStatsUpdate > 1000) { // раз в секунду
            emptyCount = emptyChunks.size();
            lastStatsUpdate = currentTime;
        }
    }

    // === методи для отримання налаштувань з YACLConfig ===

    private int getRenderDistance() {
        // базується на рівні оптимізації
        return switch (config.optimizationLevel) {
            case 0 -> 32; // без оптимізації - далеко бачимо
            case 1 -> 24; // легка оптимізація
            case 2 -> 16; // помірна оптимізація
            case 3 -> 12; // агресивна оптимізація - близько
            default -> 16;
        };
    }

    private boolean shouldUseFrustumCulling() {
        return config.modEnabled && config.optimizeRendering && config.optimizationLevel > 0;
    }

    private boolean shouldUseOcclusionCulling() {
        return config.modEnabled && config.optimizeRendering && config.optimizationLevel > 1;
    }

    private boolean shouldUseLOD() {
        return config.modEnabled && config.optimizeRendering && config.optimizationLevel > 0;
    }

    private boolean shouldCountEntities() {
        return config.modEnabled && config.enableEntityCulling;
    }

    private long getCleanupInterval() {
        return switch (config.optimizationLevel) {
            case 0 -> 10000; // рідко прибираємо
            case 1 -> 5000;  // помірно
            case 2 -> 2000;  // часто
            case 3 -> 1000;  // дуже часто
            default -> 2000;
        };
    }

    private float getEmptyChunkThreshold() {
        return switch (config.optimizationLevel) {
            case 0 -> 95.0f; // тільки майже пусті
            case 1 -> 85.0f; // переважно пусті
            case 2 -> 75.0f; // частково пусті
            case 3 -> 65.0f; // агресивно пропускаємо
            default -> 75.0f;
        };
    }

    private int getAnalysisSampleRate() {
        return switch (config.optimizationLevel) {
            case 0 -> 1;  // повний аналіз кожного блоку
            case 1 -> 2;  // кожен другий блок
            case 2 -> 4;  // кожен четвертий блок
            case 3 -> 8;  // кожен восьмий блок
            default -> 4;
        };
    }

    private int getBaseUpdateInterval() {
        return switch (config.optimizationLevel) {
            case 0 -> 50;   // часто оновлюємо
            case 1 -> 100;  // помірно
            case 2 -> 200;  // рідко
            case 3 -> 500;  // дуже рідко
            default -> 200;
        };
    }

    private double getFrustumCullingFOV() {
        return config.optimizationLevel >= 2 ? Math.toRadians(15) : Math.toRadians(10);
    }

    private float getOcclusionSolidThreshold() {
        return switch (config.optimizationLevel) {
            case 0, 1 -> 90.0f; // тільки дуже щільні чанки
            case 2 -> 80.0f;    // щільні чанки
            case 3 -> 70.0f;    // помірно щільні чанки
            default -> 80.0f;
        };
    }

    private int getOcclusionMinDistance() {
        return config.optimizationLevel >= 2 ? 8 : 12;
    }

    private float getOcclusionThreshold() {
        return switch (config.optimizationLevel) {
            case 0, 1 -> 85.0f;
            case 2 -> 75.0f;
            case 3 -> 65.0f;
            default -> 75.0f;
        };
    }

    private int getEntityCountDistance() {
        return switch (config.optimizationLevel) {
            case 0 -> 16; // далеко рахуємо ентити
            case 1 -> 12;
            case 2 -> 8;
            case 3 -> 4;  // близько тільки
            default -> 8;
        };
    }

    private int getCleanupExtraDistance() {
        return config.optimizationLevel >= 2 ? 4 : 8;
    }

    private int getEntityPriorityBonus() {
        return config.enableEntityCulling ? 5 : 0;
    }

    private int getComplexBlockPriorityBonus() {
        return config.optimizeRendering ? 3 : 0;
    }

    private int getTransparentBlockPriorityBonus() {
        return config.optimizeRendering ? 2 : 0;
    }

    // === методи для використання в міксинах ===

    public boolean shouldSkipChunkRender(long chunkPos) {
        return culledChunks.contains(chunkPos) || emptyChunks.contains(chunkPos);
    }

    public boolean isChunkEmpty(long chunkPos) {
        return emptyChunks.contains(chunkPos);
    }

    public float getChunkLODDetail(long chunkPos) {
        if (!shouldUseLOD()) return 1.0f;

        Integer lod = chunkLOD.get(chunkPos);
        if (lod == null) return 1.0f;
        return LOD_DETAIL[Math.min(lod, LOD_DETAIL.length - 1)];
    }

    public int getChunkPriority(long chunkPos) {
        return chunkPriority.getOrDefault(chunkPos, 50);
    }

    public boolean shouldThrottleChunkUpdate(long chunkPos, long currentTime) {
        Long lastUpdate = lastUpdateTime.get(chunkPos);
        if (lastUpdate == null) return false;

        ChunkData data = chunkCache.get(chunkPos);
        if (data == null) return false;

        int priority = chunkPriority.getOrDefault(chunkPos, 50);
        int baseInterval = getBaseUpdateInterval();
        int interval = Math.max(16, baseInterval + (100 - priority) * 2);

        return currentTime - lastUpdate < interval;
    }

    public String getOptimizationStats() {
        return String.format(
                "Чанки: %d | Порожні: %d | Обрізані: %d | Кеш: %d",
                totalChunks, emptyCount, culledCount, chunkCache.size()
        );
    }

    // === статичний клас для збереження інформації про чанки ===

    public static class ChunkData {
        public boolean isEmpty = false;
        public float airBlockPercentage = 0.0f;
        public float solidBlockPercentage = 0.0f;
        public float transparentBlockPercentage = 0.0f;
        public float complexBlockPercentage = 0.0f;
        public int entityCount = 0;
        public long lastAnalysis = 0;
    }
}