package com.example;

import com.example.config.YACLConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TemplateModClient implements ClientModInitializer {
    public static YACLConfig config;
    private static KeyBinding configKeyBinding;

    // кеш для HUD елементів
    private long lastHudUpdate = 0;
    private long lastGC = 0;
    private String cachedTime = "";
    private long cachedDays = 0;
    private int cachedPing = 0;
    private boolean messageSent = false;
    private final List<ColoredText> hudLines = new ArrayList<>();

    // статистики для мін/сер/макс - використовуємо ArrayDeque для кращої продуктивності
    private final ArrayDeque<Integer> fpsHistory = new ArrayDeque<>(MAX_HISTORY);
    private final ArrayDeque<Double> memoryHistory = new ArrayDeque<>(MAX_HISTORY);
    private final ArrayDeque<Integer> pingHistory = new ArrayDeque<>(MAX_HISTORY);
    private static final int MAX_HISTORY = 100;

    // кешовані статистики для зменшення обчислень
    private int cachedFpsMin, cachedFpsAvg, cachedFpsMax;
    private double cachedMemoryMin, cachedMemoryAvg, cachedMemoryMax;
    private int cachedPingMin, cachedPingAvg, cachedPingMax;
    private long lastStatsUpdate = 0;
    private static final long STATS_UPDATE_INTERVAL = 1000; // оновлюємо статистики раз на секунду

    // клас для збереження тексту з кольором
    private static class ColoredText {
        final String text;
        final int color;

        ColoredText(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    @Override
    public void onInitializeClient() {
        config = YACLConfig.getInstance();

        // початкова клавіша для конфігу (по замовчуванню O)
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.optimizationmod.config",
                GLFW.GLFW_KEY_O,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // показуємо повідомлення про завантаження
            if (!messageSent && client.player != null && config.modEnabled && config.showNotifications) {
                client.inGameHud.getChatHud().addMessage(Text.translatable("text.optimizationmod.message.loaded"));
                messageSent = true;
            }

            // перевіряємо натискання клавіші конфігу
            while (configKeyBinding.wasPressed()) {
                client.setScreen(YACLConfig.createConfigScreen(client.currentScreen));
            }

            // застосовуємо оптимізації
            applyOptimizations(client);
        });

        HudRenderCallback.EVENT.register(this::renderHud);
        System.out.println("Veltium мод ініціалізовано!");
    }

    private void applyOptimizations(MinecraftClient client) {
        if (!config.modEnabled || config.optimizationLevel == 0 || client.options == null) return;

        long currentTime = System.currentTimeMillis();

        // середній рівень оптимізації
        if (config.optimizationLevel >= 2) {
            client.options.getBobView().setValue(false);
            client.options.getEntityShadows().setValue(false);
        }

        // високий рівень оптимізації
        if (config.optimizationLevel >= 3) {
            client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
            client.options.getGraphicsMode().setValue(net.minecraft.client.option.GraphicsMode.FAST);
            client.options.getParticles().setValue(net.minecraft.particle.ParticlesMode.DECREASED);
        }

        // автоматичне очищення памяті
        if (config.reduceLag && currentTime - lastGC > 30000) {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            if (usedMemory > runtime.maxMemory() * 0.8) {
                System.gc();
                lastGC = currentTime;
            }
        }
    }

    private void renderHud(DrawContext drawContext, RenderTickCounter renderTickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!config.modEnabled || client.player == null || client.getDebugHud().shouldShowDebugHud() || client.options.hudHidden) return;

        long currentTime = System.currentTimeMillis();

        // оновлюємо кеш тільки коли потрібно
        if (currentTime - lastHudUpdate > config.hudUpdateInterval) {
            updateCache(client);
            updateStatistics(client);
            lastHudUpdate = currentTime;
        }

        // застосовуємо масштаб
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().scale(config.hudScale, config.hudScale);
        renderHudElements(drawContext, client);
        drawContext.getMatrices().popMatrix();
    }

    private void updateStatistics(MinecraftClient client) {
        long currentTime = System.currentTimeMillis();

        // збираємо статистику FPS
        int currentFps = client.getCurrentFps();
        if (fpsHistory.size() >= MAX_HISTORY) {
            fpsHistory.removeFirst();
        }
        fpsHistory.addLast(currentFps);

        // збираємо статистику памяті
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100.0;
        if (memoryHistory.size() >= MAX_HISTORY) {
            memoryHistory.removeFirst();
        }
        memoryHistory.addLast(memoryUsagePercent);

        // збираємо статистику пінгу
        if (pingHistory.size() >= MAX_HISTORY) {
            pingHistory.removeFirst();
        }
        pingHistory.addLast(cachedPing);

        // оновлюємо кешовані статистики тільки раз на секунду
        if (currentTime - lastStatsUpdate > STATS_UPDATE_INTERVAL) {
            updateCachedStats();
            lastStatsUpdate = currentTime;
        }
    }

    private void updateCachedStats() {
        // кешуємо FPS статистики
        if (!fpsHistory.isEmpty()) {
            cachedFpsMin = Collections.min(fpsHistory);
            cachedFpsMax = Collections.max(fpsHistory);
            cachedFpsAvg = (int) fpsHistory.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        // кешуємо Memory статистики
        if (!memoryHistory.isEmpty()) {
            cachedMemoryMin = Collections.min(memoryHistory);
            cachedMemoryMax = Collections.max(memoryHistory);
            cachedMemoryAvg = memoryHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        // кешуємо Ping статистики
        if (!pingHistory.isEmpty()) {
            cachedPingMin = Collections.min(pingHistory);
            cachedPingMax = Collections.max(pingHistory);
            cachedPingAvg = (int) pingHistory.stream().mapToInt(Integer::intValue).average().orElse(0);
        }
    }

    // методи для мін/сер/макс статистики - тепер використовують кеш
    private int getFpsMin() { return cachedFpsMin; }
    private int getFpsAvg() { return cachedFpsAvg; }
    private int getFpsMax() { return cachedFpsMax; }
    private double getMemoryMin() { return cachedMemoryMin; }
    private double getMemoryAvg() { return cachedMemoryAvg; }
    private double getMemoryMax() { return cachedMemoryMax; }
    private int getPingMin() { return cachedPingMin; }
    private int getPingAvg() { return cachedPingAvg; }
    private int getPingMax() { return cachedPingMax; }

    private void renderHudElements(DrawContext drawContext, MinecraftClient client) {
        hudLines.clear();

        // показуємо FPS
        if (config.showFpsCounter) {
            int fps = client.getCurrentFps();
            int fpsColor = config.getFpsColor(fps);

            if (config.showAdvancedFps && !fpsHistory.isEmpty()) {
                String fpsText = Text.translatable("text.optimizationmod.hud.fps_stats",
                        fps, getFpsMin(), getFpsAvg(), getFpsMax()).getString();
                hudLines.add(new ColoredText(fpsText, fpsColor));
            } else {
                String fpsText = Text.translatable("text.optimizationmod.hud.fps", fps).getString();
                hudLines.add(new ColoredText(fpsText, fpsColor));
            }
        }

        // показуємо використання памяті
        if (config.showMemoryUsage) {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            long usedMB = usedMemory >> 20; // швидше ніж ділення на 1024*1024
            long maxMB = maxMemory >> 20;
            int percentage = (int)((usedMemory * 100) / maxMemory);
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100.0;
            int memoryColor = config.getMemoryColor(memoryUsagePercent);

            if (config.showAdvancedMemory && !memoryHistory.isEmpty()) {
                String memoryText = Text.translatable("text.optimizationmod.hud.memory_stats",
                        String.format("%dMB/%dMB (%d%%)", usedMB, maxMB, percentage),
                        String.format("%.1f%%", getMemoryMin()),
                        String.format("%.1f%%", getMemoryAvg()),
                        String.format("%.1f%%", getMemoryMax())).getString();
                hudLines.add(new ColoredText(memoryText, memoryColor));
            } else {
                String memoryText = Text.translatable("text.optimizationmod.hud.memory",
                        usedMB, maxMB, percentage).getString();
                hudLines.add(new ColoredText(memoryText, memoryColor));
            }
        }

        // показуємо пінг
        if (config.showPing && client.getNetworkHandler() != null) {
            int pingColor = config.getPingColor(cachedPing);

            if (config.showAdvancedPing && !pingHistory.isEmpty()) {
                String pingText = Text.translatable("text.optimizationmod.hud.ping_stats",
                        cachedPing, getPingMin(), getPingAvg(), getPingMax()).getString();
                hudLines.add(new ColoredText(pingText, pingColor));
            } else {
                String pingText = Text.translatable("text.optimizationmod.hud.ping", cachedPing).getString();
                hudLines.add(new ColoredText(pingText, pingColor));
            }
        }

        // показуємо координати
        if (config.showCoordinates && client.player != null) {
            String coordsText;

            if (config.coordinatesShowDecimals) {
                // з десятковими числами
                double x = Math.rint(client.player.getX() * 10.0) / 10.0;
                double y = Math.rint(client.player.getY() * 10.0) / 10.0;
                double z = Math.rint(client.player.getZ() * 10.0) / 10.0;
                coordsText = Text.translatable("text.optimizationmod.hud.coordinates", x, y, z).getString();
            } else {
                // без десяткових чисел (цілі числа)
                int x = (int) Math.round(client.player.getX());
                int y = (int) Math.round(client.player.getY());
                int z = (int) Math.round(client.player.getZ());
                coordsText = Text.translatable("text.optimizationmod.hud.coordinates_int", x, y, z).getString();
            }

            hudLines.add(new ColoredText(coordsText, config.coordinatesColor));
        }

        // показуємо час
        if (config.showTime) {
            String timeText = Text.translatable("text.optimizationmod.hud.time", cachedTime).getString();
            hudLines.add(new ColoredText(timeText, config.timeColor));
        }

        if (config.showDays && client.world != null) {
            long worldTime = client.world.getTimeOfDay();
            long days = worldTime / 24000L;
            String daysText = Text.translatable("text.optimizationmod.hud.days", days).getString();
            hudLines.add(new ColoredText(daysText, config.daysColor));
        }

        if (hudLines.isEmpty()) return;

        // то не я рахую розміри HUD
        int totalHeight = hudLines.size() * 12 + 8;
        int padding = 4;

        for (int i = 0; i < hudLines.size(); i++) {
            ColoredText line = hudLines.get(i);

            int textWidth = client.textRenderer.getWidth(line.text);

            int finalX = getHudX(client, textWidth);
            int finalY = getHudY(client, totalHeight) + i * 12;

            if (config.hudBackgroundColor != 0 && config.hudBackgroundOpacity > 0) {
                int bgColor = (config.hudBackgroundColor & 0xFFFFFF) | ((int)(config.hudBackgroundOpacity * 255) << 24);
                drawContext.fill(finalX - padding, finalY - padding,
                        finalX + textWidth + padding, finalY + 12 - padding, bgColor);
            }

            if (config.showCoordinates && config.enableCoordinateColors && line.text.contains("XYZ")) {
                renderColoredCoordinates(drawContext, client, line.text, finalX, finalY);
            } else {
                renderTextLine(drawContext, client, line.text, finalX, finalY, line.color);
            }
        }

    }

    // рендеринг координат з кольоровими частинами
    private void renderColoredCoordinates(DrawContext drawContext, MinecraftClient client, String text, int x, int y) {
        int colonIndex = text.indexOf(':');
        if (colonIndex == -1) {
            renderTextLine(drawContext, client, text, x, y, config.coordinatesColor);
            return;
        }

        String prefix = text.substring(0, colonIndex + 2);
        String coordinates = text.substring(colonIndex + 2);
        String[] parts = coordinates.trim().split(" ");

        if (parts.length >= 3) {
            int currentX = x;
            int spacing = config.hudBold ? 2 : 1;

            renderTextLine(drawContext, client, prefix, currentX, y, config.coordinatesColor);
            currentX += client.textRenderer.getWidth(prefix);

            renderTextLine(drawContext, client, parts[0], currentX, y, config.coordinatesXColor);
            currentX += client.textRenderer.getWidth(parts[0]) + spacing;

            renderTextLine(drawContext, client, " ", currentX, y, config.coordinatesColor);
            currentX += client.textRenderer.getWidth(" ");

            renderTextLine(drawContext, client, parts[1], currentX, y, config.coordinatesYColor);
            currentX += client.textRenderer.getWidth(parts[1]) + spacing;

            renderTextLine(drawContext, client, " ", currentX, y, config.coordinatesColor);
            currentX += client.textRenderer.getWidth(" ");

            renderTextLine(drawContext, client, parts[2], currentX, y, config.coordinatesZColor);
        } else {
            renderTextLine(drawContext, client, text, x, y, config.coordinatesColor);
        }
    }

    private void updateCache(MinecraftClient client) {
        // оновлюємо кеш пінгу
        if (client.getNetworkHandler() != null && client.player != null) {
            try {
                var playerEntry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
                cachedPing = playerEntry != null ? playerEntry.getLatency() : 0;
            } catch (Exception e) {
                cachedPing = 0;
            }
        }

        // оновлюємо кеш часу
        LocalDateTime now = LocalDateTime.now();
        cachedTime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private void renderTextLine(DrawContext drawContext, MinecraftClient client, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;

        MutableText mutableText = Text.literal(text);

        if (config.hudBold) mutableText = mutableText.formatted(Formatting.BOLD);

        int finalColor = (color & 0xFFFFFF) | ((int)(Math.max(0.1f, config.hudTextOpacity) * 255) << 24);

        if (config.hudShadow) {
            drawContext.drawTextWithShadow(client.textRenderer, mutableText, x, y, finalColor);
        } else {
            drawContext.drawText(client.textRenderer, mutableText, x, y, finalColor, false);
        }
    }

    private int getHudX(MinecraftClient client, int textWidth) {
        int screenWidth = (int)(client.getWindow().getScaledWidth() / config.hudScale);

        if (config.cornerSnap) {
            return switch (config.hudPosition) {
                case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - textWidth - 1;
                default -> 1;
            };
        } else {
            int scaledOffsetX = (int)(config.hudX / config.hudScale);
            return switch (config.hudPosition) {
                case TOP_RIGHT, BOTTOM_RIGHT -> Math.max(0, screenWidth - textWidth - scaledOffsetX);
                default -> Math.max(0, scaledOffsetX);
            };
        }
    }

    private int getHudY(MinecraftClient client, int hudHeight) {
        int screenHeight = (int)(client.getWindow().getScaledHeight() / config.hudScale);
        int scaledHudHeight = (int)(hudHeight / config.hudScale);

        if (config.cornerSnap) {
            return switch (config.hudPosition) {
                case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - scaledHudHeight - 1;
                default -> 1;
            };
        } else {
            int scaledOffsetY = (int)(config.hudY / config.hudScale);
            return switch (config.hudPosition) {
                case BOTTOM_LEFT, BOTTOM_RIGHT -> Math.max(0, screenHeight - scaledHudHeight - scaledOffsetY);
                default -> Math.max(0, scaledOffsetY);
            };
        }
    }
}