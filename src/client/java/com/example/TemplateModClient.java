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
    private int cachedPing = 0;
    private boolean messageSent = false;
    private final List<ColoredText> hudLines = new ArrayList<>();

    // статистики для мін/сер/макс
    private final List<Integer> fpsHistory = new ArrayList<>();
    private final List<Double> memoryHistory = new ArrayList<>();
    private final List<Integer> pingHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 100;

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
                "key.optimizationmod.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.optimizationmod"));

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
        // збираємо статистику FPS
        int currentFps = client.getCurrentFps();
        fpsHistory.add(currentFps);
        if (fpsHistory.size() > MAX_HISTORY) {
            fpsHistory.removeFirst();
        }

        // збираємо статистику памяті
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100.0;
        memoryHistory.add(memoryUsagePercent);
        if (memoryHistory.size() > MAX_HISTORY) {
            memoryHistory.removeFirst();
        }

        // збираємо статистику пінгу
        pingHistory.add(cachedPing);
        if (pingHistory.size() > MAX_HISTORY) {
            pingHistory.removeFirst();
        }
    }

    // методи для мін/сер/макс статистики
    private int getFpsMin() {
        return fpsHistory.isEmpty() ? 0 : Collections.min(fpsHistory);
    }

    private int getFpsAvg() {
        return fpsHistory.isEmpty() ? 0 : (int) fpsHistory.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private int getFpsMax() {
        return fpsHistory.isEmpty() ? 0 : Collections.max(fpsHistory);
    }

    private double getMemoryMin() {
        return memoryHistory.isEmpty() ? 0 : Collections.min(memoryHistory);
    }

    private double getMemoryAvg() {
        return memoryHistory.isEmpty() ? 0 : memoryHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double getMemoryMax() {
        return memoryHistory.isEmpty() ? 0 : Collections.max(memoryHistory);
    }

    private int getPingMin() {
        return pingHistory.isEmpty() ? 0 : Collections.min(pingHistory);
    }

    private int getPingAvg() {
        return pingHistory.isEmpty() ? 0 : (int) pingHistory.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private int getPingMax() {
        return pingHistory.isEmpty() ? 0 : Collections.max(pingHistory);
    }

    private void renderHudElements(DrawContext drawContext, MinecraftClient client) {
        hudLines.clear();

        // показуємо FPS
        if (config.showFpsCounter) {
            int fps = client.getCurrentFps();
            int fpsColor = config.getFpsColor(fps);

            if (config.showAdvancedFps && !fpsHistory.isEmpty()) {
                int fpsMin = getFpsMin();
                int fpsAvg = getFpsAvg();
                int fpsMax = getFpsMax();
                String fpsText = Text.translatable("text.optimizationmod.hud.fps_stats",
                        fps, fpsMin, fpsAvg, fpsMax).getString();
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
            long usedMB = usedMemory / (1024 * 1024);
            long maxMB = maxMemory / (1024 * 1024);
            int percentage = (int)((usedMemory * 100) / maxMemory);
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100.0;
            int memoryColor = config.getMemoryColor(memoryUsagePercent);

            if (config.showAdvancedMemory && !memoryHistory.isEmpty()) {
                double memoryMin = getMemoryMin();
                double memoryAvg = getMemoryAvg();
                double memoryMax = getMemoryMax();
                String memoryText = Text.translatable("text.optimizationmod.hud.memory_stats",
                        String.format("%dMB/%dMB (%d%%)", usedMB, maxMB, percentage),
                        String.format("%.1f", memoryMin),
                        String.format("%.1f", memoryAvg),
                        String.format("%.1f", memoryMax)).getString();
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
                int pingMin = getPingMin();
                int pingAvg = getPingAvg();
                int pingMax = getPingMax();
                String pingText = Text.translatable("text.optimizationmod.hud.ping_stats",
                        cachedPing, pingMin, pingAvg, pingMax).getString();
                hudLines.add(new ColoredText(pingText, pingColor));
            } else {
                String pingText = Text.translatable("text.optimizationmod.hud.ping", cachedPing).getString();
                hudLines.add(new ColoredText(pingText, pingColor));
            }
        }

        // показуємо координати
        if (config.showCoordinates && client.player != null) {
            double x = Math.round(client.player.getX() * 10.0) / 10.0;
            double y = Math.round(client.player.getY() * 10.0) / 10.0;
            double z = Math.round(client.player.getZ() * 10.0) / 10.0;

            String coordsText = Text.translatable("text.optimizationmod.hud.coordinates", x, y, z).getString();
            hudLines.add(new ColoredText(coordsText, config.coordinatesColor));
        }

        // показуємо час
        if (config.showTime) {
            String timeText = Text.translatable("text.optimizationmod.hud.time", cachedTime).getString();
            hudLines.add(new ColoredText(timeText, config.timeColor));
        }

        if (hudLines.isEmpty()) return;

        // обчислюємо розміри HUD
        int maxWidth = 0;
        for (ColoredText line : hudLines) {
            int width = client.textRenderer.getWidth(line.text);
            if (width > maxWidth) maxWidth = width;
        }

        int totalHeight = hudLines.size() * 12 + 8;
        int padding = 4;

        int baseX = getHudX(client, maxWidth + padding * 2);
        int baseY = getHudY(client, totalHeight);

        // рендеримо фон якщо потрібно
        if (config.hudBackgroundColor != 0 && config.hudBackgroundOpacity > 0) {
            int bgColor = (config.hudBackgroundColor & 0xFFFFFF) | ((int)(config.hudBackgroundOpacity * 255) << 24);
            drawContext.fill(baseX - padding, baseY - padding,
                    baseX + maxWidth + padding, baseY + totalHeight - padding, bgColor);
        }

        int scaledX = (int)(baseX / config.hudScale);
        int scaledY = (int)(baseY / config.hudScale);

        // рендеримо текст з кольоровими частинами для координат
        for (int i = 0; i < hudLines.size(); i++) {
            ColoredText line = hudLines.get(i);
            int lineY = scaledY + i * 12;

            if (config.showCoordinates && config.enableCoordinateColors && line.text.contains("XYZ")) {
                renderColoredCoordinates(drawContext, client, line.text, scaledX, lineY);
            } else {
                renderTextLine(drawContext, client, line.text, scaledX, lineY, line.color);
            }
        }
    }

    // рендеринг координат з кольоровими частинами
    private void renderColoredCoordinates(DrawContext drawContext, MinecraftClient client, String text, int x, int y) {
        // знаходимо позицію після "XYZ: "
        int colonIndex = text.indexOf(':');
        if (colonIndex == -1) {
            renderTextLine(drawContext, client, text, x, y, config.coordinatesColor);
            return;
        }

        String prefix = text.substring(0, colonIndex + 2); // "XYZ: "
        String coordinates = text.substring(colonIndex + 2); // "123.4 567.8 901.2"

        String[] parts = coordinates.trim().split(" ");
        if (parts.length >= 3) {
            int currentX = x;

            // рендеримо префікс
            renderTextLine(drawContext, client, prefix, currentX, y, config.coordinatesColor);
            currentX += client.textRenderer.getWidth(prefix);

            // рендеримо X (червоний)
            renderTextLine(drawContext, client, parts[0], currentX, y, config.coordinatesXColor);
            currentX += client.textRenderer.getWidth(parts[0] + " ");

            // рендеримо Y (зелений)
            renderTextLine(drawContext, client, parts[1], currentX, y, config.coordinatesYColor);
            currentX += client.textRenderer.getWidth(parts[1] + " ");

            // рендеримо Z (синій)
            renderTextLine(drawContext, client, parts[2], currentX, y, config.coordinatesZColor);
        } else {
            // запасний варіант - звичайний рендеринг
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

    private int getHudX(MinecraftClient client, int hudWidth) {
        int screenWidth = client.getWindow().getScaledWidth();

        if (config.cornerSnap) {
            return switch (config.hudPosition) {
                case "top-right", "bottom-right" -> (int)((screenWidth - hudWidth - 1) / config.hudScale);
                default -> (int)(1 / config.hudScale);
            };
        } else {
            return switch (config.hudPosition) {
                case "top-right", "bottom-right" -> Math.max(0, (int)((screenWidth - hudWidth - config.hudX) / config.hudScale));
                default -> Math.max(0, (int)(config.hudX / config.hudScale));
            };
        }
    }

    private int getHudY(MinecraftClient client, int hudHeight) {
        int screenHeight = client.getWindow().getScaledHeight();

        if (config.cornerSnap) {
            return switch (config.hudPosition) {
                case "bottom-left", "bottom-right" -> (int)((screenHeight - hudHeight - 1) / config.hudScale);
                default -> (int)(1 / config.hudScale);
            };
        } else {
            return switch (config.hudPosition) {
                case "bottom-left", "bottom-right" -> Math.max(0, (int)((screenHeight - hudHeight - config.hudY) / config.hudScale));
                default -> Math.max(0, (int)(config.hudY / config.hudScale));
            };
        }
    }
}