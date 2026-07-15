package com.veltium;

import com.veltium.config.YACLConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Veltium implements ClientModInitializer {
    public static YACLConfig config;
    private static KeyMapping configKeyMapping;

    private long lastHudUpdate = 0;
    private String cachedTime = "";
    private long cachedDays = 0;
    private int cachedPing = 0;
    private boolean messageSent = false;
    private final List<ColoredText> hudLines = new ArrayList<>();

    private String cachedWorldTime = "";
    private boolean cachedIsDay = true;

    private final ArrayDeque<Integer> fpsHistory = new ArrayDeque<>(MAX_HISTORY);
    private final ArrayDeque<Double> memoryHistory = new ArrayDeque<>(MAX_HISTORY);
    private final ArrayDeque<Integer> pingHistory = new ArrayDeque<>(MAX_HISTORY);
    private static final int MAX_HISTORY = 100;

    private int cachedFpsMin, cachedFpsAvg, cachedFpsMax;
    private double cachedMemoryMin, cachedMemoryAvg, cachedMemoryMax;
    private int cachedPingMin, cachedPingAvg, cachedPingMax;
    private long lastStatsUpdate = 0;
    private static final long STATS_UPDATE_INTERVAL = 1000;

    private static class ColoredText {
        final String text;
        final int color;
        final boolean strikethrough;

        ColoredText(String text, int color) {
            this(text, color, false);
        }

        ColoredText(String text, int color, boolean strikethrough) {
            this.text = text;
            this.color = color;
            this.strikethrough = strikethrough;
        }
    }

    @Override
    public void onInitializeClient() {
        config = YACLConfig.getInstance();

        configKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.optimizationmod.config",
                GLFW.GLFW_KEY_I,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!messageSent && client.player != null && config.modEnabled && config.showNotifications) {
                client.gui.hud.getChat().addClientSystemMessage(Component.translatable("text.optimizationmod.message.loaded"));
                messageSent = true;
            }

            while (configKeyMapping.consumeClick()) {
                client.gui.setScreen(YACLConfig.createConfigScreen(client.gui.screen()));
            }
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath("veltium", "main_hud"),
                this::renderHud
        );

        System.out.println("Veltium, maybe it works");
    }

    private void renderHud(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();

        if (!config.modEnabled
                || client.player == null
                || client.getDebugOverlay().showDebugScreen()
                || client.gui.hud.isHidden()) return;

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastHudUpdate > config.hudUpdateInterval) {
            updateCache(client);
            updateStatistics(client);
            lastHudUpdate = currentTime;
        }

        renderHudElements(guiGraphics, client);
    }

    private void updateStatistics(Minecraft client) {
        long currentTime = System.currentTimeMillis();

        int currentFps = client.getFps();
        if (fpsHistory.size() >= MAX_HISTORY) fpsHistory.removeFirst();
        fpsHistory.addLast(currentFps);

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100.0;
        if (memoryHistory.size() >= MAX_HISTORY) memoryHistory.removeFirst();
        memoryHistory.addLast(memoryUsagePercent);

        if (pingHistory.size() >= MAX_HISTORY) pingHistory.removeFirst();
        pingHistory.addLast(cachedPing);

        if (currentTime - lastStatsUpdate > STATS_UPDATE_INTERVAL) {
            updateCachedStats();
            lastStatsUpdate = currentTime;
        }
    }

    private void updateCachedStats() {
        if (!fpsHistory.isEmpty()) {
            cachedFpsMin = Collections.min(fpsHistory);
            cachedFpsMax = Collections.max(fpsHistory);
            cachedFpsAvg = (int) fpsHistory.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        if (!memoryHistory.isEmpty()) {
            cachedMemoryMin = Collections.min(memoryHistory);
            cachedMemoryMax = Collections.max(memoryHistory);
            cachedMemoryAvg = memoryHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        if (!pingHistory.isEmpty()) {
            cachedPingMin = Collections.min(pingHistory);
            cachedPingMax = Collections.max(pingHistory);
            cachedPingAvg = (int) pingHistory.stream().mapToInt(Integer::intValue).average().orElse(0);
        }
    }

    private int getFpsMin() { return cachedFpsMin; }
    private int getFpsAvg() { return cachedFpsAvg; }
    private int getFpsMax() { return cachedFpsMax; }
    private double getMemoryMin() { return cachedMemoryMin; }
    private double getMemoryAvg() { return cachedMemoryAvg; }
    private double getMemoryMax() { return cachedMemoryMax; }
    private int getPingMin() { return cachedPingMin; }
    private int getPingAvg() { return cachedPingAvg; }
    private int getPingMax() { return cachedPingMax; }

    private void renderHudElements(GuiGraphicsExtractor guiGraphics, Minecraft client) {
        hudLines.clear();

        if (config.showFpsCounter) {
            int fps = client.getFps();
            int fpsColor = config.getFpsColor(fps);

            if (config.showAdvancedFps && !fpsHistory.isEmpty()) {
                String fpsText = Component.translatable("text.optimizationmod.hud.fps_stats",
                        fps, getFpsMin(), getFpsAvg(), getFpsMax()).getString();
                hudLines.add(new ColoredText(fpsText, fpsColor));
            } else {
                String fpsText = Component.translatable("text.optimizationmod.hud.fps", fps).getString();
                hudLines.add(new ColoredText(fpsText, fpsColor));
            }
        }

        if (config.showMemoryUsage) {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            long usedMB = usedMemory >> 20;
            long maxMB = maxMemory >> 20;
            int percentage = (int)((usedMemory * 100) / maxMemory);
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100.0;
            int memoryColor = config.getMemoryColor(memoryUsagePercent);

            if (config.showAdvancedMemory && !memoryHistory.isEmpty()) {
                String memoryText = Component.translatable("text.optimizationmod.hud.memory_stats",
                        String.format("%dMB/%dMB (%d%%)", usedMB, maxMB, percentage),
                        String.format("%.1f%%", getMemoryMin()),
                        String.format("%.1f%%", getMemoryAvg()),
                        String.format("%.1f%%", getMemoryMax())).getString();
                hudLines.add(new ColoredText(memoryText, memoryColor));
            } else {
                String memoryText = Component.translatable("text.optimizationmod.hud.memory",
                        usedMB, maxMB, percentage).getString();
                hudLines.add(new ColoredText(memoryText, memoryColor));
            }
        }

        if (config.showPing && client.getConnection() != null) {
            boolean isSinglePlayer = client.hasSingleplayerServer();
            int pingColor = isSinglePlayer ? 0x888780 : config.getPingColor(cachedPing);

            String pingString;
            if (config.showAdvancedPing && !pingHistory.isEmpty()) {
                pingString = Component.translatable("text.optimizationmod.hud.ping_stats",
                        cachedPing, getPingMin(), getPingAvg(), getPingMax()).getString();
            } else {
                pingString = Component.translatable("text.optimizationmod.hud.ping", cachedPing).getString();
            }

            hudLines.add(new ColoredText(pingString, pingColor, isSinglePlayer));
        }

        if (config.showCoordinates && client.player != null) {
            String coordsText;

            if (config.coordinatesShowDecimals) {
                double x = Math.rint(client.player.getX() * 10.0) / 10.0;
                double y = Math.rint(client.player.getY() * 10.0) / 10.0;
                double z = Math.rint(client.player.getZ() * 10.0) / 10.0;
                coordsText = Component.translatable("text.optimizationmod.hud.coordinates", x, y, z).getString();
            } else {
                int x = (int) Math.round(client.player.getX());
                int y = (int) Math.round(client.player.getY());
                int z = (int) Math.round(client.player.getZ());
                coordsText = Component.translatable("text.optimizationmod.hud.coordinates_int", x, y, z).getString();
            }

            hudLines.add(new ColoredText(coordsText, config.coordinatesColor));
        }

        if (config.showTime) {
            String timeText = Component.translatable("text.optimizationmod.hud.time", cachedTime).getString();
            hudLines.add(new ColoredText(timeText, config.timeColor));
        }

        if (config.showDays && client.level != null) {
            String daysText = Component.translatable("text.optimizationmod.hud.days", cachedDays).getString();
            hudLines.add(new ColoredText(daysText, config.daysColor));
        }

        if (config.showWorldTime && client.level != null && !cachedWorldTime.isEmpty()) {
            String phaseKey = cachedIsDay
                    ? "text.optimizationmod.hud.day"
                    : "text.optimizationmod.hud.night";
            String phaseLocalized = Component.translatable(phaseKey).getString();

            String worldTimeText = Component.translatable(
                    "text.optimizationmod.hud.world_time",
                    cachedWorldTime,
                    phaseLocalized
            ).getString();

            int color = cachedIsDay ? config.dayColor : config.nightColor;
            hudLines.add(new ColoredText(worldTimeText, color));
        }

        if (hudLines.isEmpty()) return;

        final int LINE_H = 10;
        final int BACKGROUND_PADDING_X = 5;
        final int BACKGROUND_PADDING_TOP = 3;
        final int BACKGROUND_PADDING_BOTTOM = 3;
        final int BACKGROUND_CORNER_CUT = 2;
        int totalRawHeight = hudLines.size() * LINE_H;

        final int BOTTOM_CENTER_MARGIN = 39;

        int screenWidth  = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        if (config.hudBackgroundEnabled) {
            int maxTextWidth = 0;
            for (ColoredText line : hudLines) {
                maxTextWidth = Math.max(maxTextWidth, getHudTextWidth(client, line));
            }

            int backgroundWidth = maxTextWidth + BACKGROUND_PADDING_X * 2;
            int backgroundHeight = (hudLines.size() - 1) * LINE_H + client.font.lineHeight + BACKGROUND_PADDING_TOP + BACKGROUND_PADDING_BOTTOM;

            int baseX;
            int baseY;

            if (config.cornerSnap) {
                baseX = switch (config.hudPosition) {
                    case TOP_RIGHT, BOTTOM_RIGHT   -> screenWidth - (int)(backgroundWidth * config.hudScale) - 1;
                    case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - (int)(backgroundWidth * config.hudScale)) / 2;
                    default                        -> 1;
                };
                baseY = switch (config.hudPosition) {
                    case BOTTOM_LEFT, BOTTOM_RIGHT ->
                            screenHeight - (int)(backgroundHeight * config.hudScale) - 1;
                    case BOTTOM_CENTER ->
                            screenHeight - BOTTOM_CENTER_MARGIN - (int)(backgroundHeight * config.hudScale);
                    default -> 1;
                };
            } else {
                baseX = switch (config.hudPosition) {
                    case TOP_RIGHT, BOTTOM_RIGHT   -> Math.max(0, screenWidth - (int)(backgroundWidth * config.hudScale) - config.hudX);
                    case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - (int)(backgroundWidth * config.hudScale)) / 2;
                    default                        -> Math.max(0, config.hudX);
                };
                baseY = switch (config.hudPosition) {
                    case BOTTOM_LEFT, BOTTOM_RIGHT ->
                            Math.max(0, screenHeight - (int)(backgroundHeight * config.hudScale) - config.hudY);
                    case BOTTOM_CENTER ->
                            screenHeight - BOTTOM_CENTER_MARGIN - (int)(backgroundHeight * config.hudScale) - config.hudY;
                    default -> Math.max(0, config.hudY);
                };
            }

            baseX = clampToScreen(baseX, screenWidth, (int)(backgroundWidth * config.hudScale));
            baseY = clampToScreen(baseY, screenHeight, (int)(backgroundHeight * config.hudScale));

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate((float) baseX, (float) baseY);
            guiGraphics.pose().scale(config.hudScale, config.hudScale);

            int cornerCut = config.hudBackgroundCorners ? BACKGROUND_CORNER_CUT : 0;
            renderLineBackground(guiGraphics, backgroundWidth, backgroundHeight, cornerCut);

            for (int i = 0; i < hudLines.size(); i++) {
                ColoredText line = hudLines.get(i);
                int textWidth = getHudTextWidth(client, line);
                int textX = switch (config.hudPosition) {
                    case TOP_RIGHT, BOTTOM_RIGHT -> BACKGROUND_PADDING_X + maxTextWidth - textWidth;
                    case TOP_CENTER, BOTTOM_CENTER -> BACKGROUND_PADDING_X + (maxTextWidth - textWidth) / 2;
                    default -> BACKGROUND_PADDING_X;
                };
                int textY = BACKGROUND_PADDING_TOP + i * LINE_H;

                if (config.showCoordinates && config.enableCoordinateColors && line.text.contains("XYZ")) {
                    renderColoredCoordinates(guiGraphics, client, line.text, textX, textY);
                } else {
                    renderTextLine(guiGraphics, client, line.text, textX, textY, line.color, line.strikethrough);
                }
            }

            guiGraphics.pose().popMatrix();
            return;
        }

        for (int i = 0; i < hudLines.size(); i++) {
            ColoredText line = hudLines.get(i);

            int textWidth = getHudTextWidth(client, line);

            int baseX;
            int baseY;

            if (config.cornerSnap) {
                baseX = switch (config.hudPosition) {
                    case TOP_RIGHT, BOTTOM_RIGHT   -> screenWidth - (int)(textWidth * config.hudScale) - 1;
                    case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - (int)(textWidth * config.hudScale)) / 2;
                    default                        -> 1;
                };
                baseY = switch (config.hudPosition) {
                    case BOTTOM_LEFT, BOTTOM_RIGHT ->
                            screenHeight - (int)(totalRawHeight * config.hudScale) + (int)(i * LINE_H * config.hudScale) - 1;
                    case BOTTOM_CENTER ->
                            screenHeight - BOTTOM_CENTER_MARGIN - (int)(totalRawHeight * config.hudScale) + (int)(i * LINE_H * config.hudScale);
                    default -> 1 + (int)(i * LINE_H * config.hudScale);
                };
            } else {
                baseX = switch (config.hudPosition) {
                    case TOP_RIGHT, BOTTOM_RIGHT   -> Math.max(0, screenWidth - (int)(textWidth * config.hudScale) - config.hudX);
                    case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - (int)(textWidth * config.hudScale)) / 2;
                    default                        -> Math.max(0, config.hudX);
                };
                baseY = switch (config.hudPosition) {
                    case BOTTOM_LEFT, BOTTOM_RIGHT ->
                            Math.max(0, screenHeight - (int)(totalRawHeight * config.hudScale) - config.hudY) + (int)(i * LINE_H * config.hudScale);
                    case BOTTOM_CENTER ->
                            screenHeight - BOTTOM_CENTER_MARGIN - (int)(totalRawHeight * config.hudScale) - config.hudY + (int)(i * LINE_H * config.hudScale);
                    default -> Math.max(0, config.hudY) + (int)(i * LINE_H * config.hudScale);
                };
            }

            baseX = clampToScreen(baseX, screenWidth, (int)(textWidth * config.hudScale));
            baseY = clampToScreen(baseY, screenHeight, (int)(LINE_H * config.hudScale));

            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate((float) baseX, (float) baseY);
            guiGraphics.pose().scale(config.hudScale, config.hudScale);

            if (config.showCoordinates && config.enableCoordinateColors && line.text.contains("XYZ")) {
                renderColoredCoordinates(guiGraphics, client, line.text, 0, 0);
            } else {
                renderTextLine(guiGraphics, client, line.text, 0, 0, line.color, line.strikethrough);
            }

            guiGraphics.pose().popMatrix();
        }
    }

    private void renderLineBackground(GuiGraphicsExtractor guiGraphics, int width, int height, int cornerCut) {
        int alpha = (int)(Math.max(0.0f, Math.min(1.0f, config.hudBackgroundOpacity)) * 255);
        if (alpha <= 0) return;

        int color = (alpha << 24) | (config.hudBackgroundColor & 0xFFFFFF);
        int cut = Math.max(0, Math.min(cornerCut, Math.min(width, height) / 2));

        if (cut == 0) {
            guiGraphics.fill(0, 0, width, height, color);
            return;
        }

        guiGraphics.fill(cut, 0, width - cut, cut, color);
        guiGraphics.fill(0, cut, width, height - cut, color);
        guiGraphics.fill(cut, height - cut, width - cut, height, color);
    }

    private void renderColoredCoordinates(GuiGraphicsExtractor guiGraphics, Minecraft client, String text, int x, int y) {
        int colonIndex = text.indexOf(':');
        if (colonIndex == -1) {
            renderTextLine(guiGraphics, client, text, x, y, config.coordinatesColor, false);
            return;
        }

        String prefix = text.substring(0, colonIndex + 2);
        String coordinates = text.substring(colonIndex + 2);
        String[] parts = coordinates.trim().split(" ");

        if (parts.length >= 3) {
            int currentX = x;

            renderTextLine(guiGraphics, client, prefix, currentX, y, config.coordinatesColor, false);
            currentX += client.font.width(prefix);

            renderTextLine(guiGraphics, client, parts[0], currentX, y, config.coordinatesXColor, false);
            currentX += client.font.width(parts[0]);

            renderTextLine(guiGraphics, client, " ", currentX, y, config.coordinatesColor, false);
            currentX += client.font.width(" ");

            renderTextLine(guiGraphics, client, parts[1], currentX, y, config.coordinatesYColor, false);
            currentX += client.font.width(parts[1]);

            renderTextLine(guiGraphics, client, " ", currentX, y, config.coordinatesColor, false);
            currentX += client.font.width(" ");

            renderTextLine(guiGraphics, client, parts[2], currentX, y, config.coordinatesZColor, false);
        } else {
            renderTextLine(guiGraphics, client, text, x, y, config.coordinatesColor, false);
        }
    }

    private void updateCache(Minecraft client) {
        if (client.getConnection() != null && client.player != null) {
            try {
                var playerEntry = client.getConnection().getPlayerInfo(client.player.getUUID());
                cachedPing = playerEntry != null ? playerEntry.getLatency() : 0;
            } catch (Exception e) {
                cachedPing = 0;
            }
        }

        cachedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        if (client.level != null) {
            long worldTime = client.level.getOverworldClockTime() % 24000L;
            cachedIsDay = worldTime < 12000L;

            long totalSeconds = worldTime * 1200L / 24000L;
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            cachedWorldTime = String.format("%02d:%02d", minutes, seconds);

            cachedDays = client.level.getOverworldClockTime() / 24000L;
        }
    }

    private void renderTextLine(GuiGraphicsExtractor guiGraphics, Minecraft client, String text, int x, int y, int color) {
        renderTextLine(guiGraphics, client, text, x, y, color, false);
    }

    private void renderTextLine(GuiGraphicsExtractor guiGraphics, Minecraft client, String text, int x, int y, int color, boolean strikethrough) {
        if (text == null || text.isEmpty()) return;

        int alpha = (int)(Math.max(0.1f, config.hudTextOpacity) * 255);
        int finalColor = (alpha << 24) | (color & 0xFFFFFF);

        guiGraphics.text(client.font, createHudText(text, strikethrough), x, y, finalColor, config.hudShadow);
    }

    private int getHudTextWidth(Minecraft client, ColoredText line) {
        if (line == null || line.text == null || line.text.isEmpty()) return 0;
        return client.font.width(createHudText(line.text, line.strikethrough));
    }

    private int clampToScreen(int position, int screenSize, int elementSize) {
        return Math.max(0, Math.min(position, Math.max(0, screenSize - elementSize)));
    }

    private MutableComponent createHudText(String text, boolean strikethrough) {
        MutableComponent mutableText = Component.literal(text);

        if (config.hudBold) mutableText = mutableText.withStyle(ChatFormatting.BOLD);
        if (strikethrough) mutableText = mutableText.withStyle(ChatFormatting.STRIKETHROUGH);

        return mutableText;
    }
}