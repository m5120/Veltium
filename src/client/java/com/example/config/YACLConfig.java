package com.example.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Color;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class YACLConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("veltium-config.json");
    private static YACLConfig INSTANCE = null;

    public static YACLConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new YACLConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    // === ГОЛОВНІ НАЛАШТУВАННЯ ===
    public boolean modEnabled = true;
    public boolean showNotifications = true;
    public boolean cornerSnap = false;

    // === ОСНОВНІ НАЛАШТУВАННЯ ===
    public int optimizationLevel = 0;
    public int maxParticles = 100;

    // === ОПТИМІЗАЦІЯ БІЛЬШІСТЬ false ТОМУ-ЩО ЦЕ ДОСИТЬ ПОГАНА ОПТИМІЗАЦІЯ І НЕСТАБІЛЬНА ===
    public boolean reduceLag = false;
    public boolean optimizeRendering = false;
    public boolean cullParticles = true;
    public boolean enableEntityCulling = false;
    public boolean optimizeChunks = false;
    public boolean fastMath = false;
    public boolean smartMemoryManagement = false;
    public int tickOptimizationLevel = 1;

    // === ЕЛЕМЕНТИ HUD ===
    public boolean showFpsCounter = true;
    public boolean showMemoryUsage = false;
    public boolean showPing = false;
    public boolean showCoordinates = true;
    public boolean showTime = false;

    // === РОЗШИРЕНА СТАТИСТИКА ===
    public boolean showAdvancedFps = false;
    public boolean showAdvancedMemory = false;
    public boolean showAdvancedPing = false;

    // === ПОЗИЦІЯ HUD ===
    public HudPosition hudPosition = HudPosition.TOP_LEFT;
    public int hudX = 10;
    public int hudY = 10;

    // === ЗОВНІШНІЙ ВИГЛЯД ===
    public int hudUpdateInterval = 25;
    public boolean hudShadow = true;
    public boolean hudBold = false;
    public float hudScale = 1.0f;
    public float hudTextOpacity = 1.0f;
    public float hudBackgroundOpacity = 0.8f;

    // === КОЛЬОРИ ===
    public int hudBackgroundColor = 0x000000;

    // FPS кольори
    public int fpsGoodColor = 0x55FF55;
    public int fpsMediumColor = 0xFFFF55;
    public int fpsBadColor = 0xFF5555;

    // Кольори пам'яті
    public int memoryGoodColor = 0x55FF55;
    public int memoryMediumColor = 0xFFFF55;
    public int memoryBadColor = 0xFF5555;

    // Кольори пінгу
    public int pingGoodColor = 0x55FF55;
    public int pingMediumColor = 0xFFFF55;
    public int pingBadColor = 0xFF5555;

    // Кольори координат
    public boolean enableCoordinateColors = false;
    public int coordinatesColor = 0xFFFFFF;
    public int coordinatesXColor = 0xFF5555;
    public int coordinatesYColor = 0x55FF55;
    public int coordinatesZColor = 0x5555FF;

    // Колір часу
    public int timeColor = 0xFFFFFF;

    // === ENUM ДЛЯ ПОЗИЦІЙ HUD ===
    public enum HudPosition implements NameableEnum {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        @Override
        public Text getDisplayName() {
            return Text.translatable("text.optimizationmod.hud_position." + name().toLowerCase());
        }

        // Метод для переключення на наступну позицію
        public HudPosition next() {
            HudPosition[] values = values();
            int currentIndex = this.ordinal();
            int nextIndex = (currentIndex + 1) % values.length;
            return values[nextIndex];
        }
    }

    // === МЕТОДИ ДЛЯ КОЛЬОРІВ ===
    private static Color intToColor(int color) {
        return new Color(color);
    }

    private static int colorToInt(Color color) {
        return color.getRGB() & 0xFFFFFF;
    }

    public int getFpsColor(int fps) {
        if (fps >= 60) return fpsGoodColor;
        if (fps >= 30) return fpsMediumColor;
        return fpsBadColor;
    }

    public int getMemoryColor(double memoryUsagePercentage) {
        if (memoryUsagePercentage < 70) return memoryGoodColor;
        if (memoryUsagePercentage < 90) return memoryMediumColor;
        return memoryBadColor;
    }

    public int getPingColor(int ping) {
        if (ping < 50) return pingGoodColor;
        if (ping < 100) return pingMediumColor;
        return pingBadColor;
    }

    // === ПЕРЕВІРКА НАЛАШТУВАНЬ ===
    public boolean shouldReduceLag() {
        return modEnabled && reduceLag && optimizationLevel > 0;
    }

    public boolean shouldCullEntities() {
        return modEnabled && enableEntityCulling && optimizationLevel > 0;
    }

    public boolean shouldOptimizeChunks() {
        return modEnabled && optimizeChunks && optimizationLevel > 0;
    }

    public int getEffectiveParticleLimit() {
        if (!modEnabled || optimizationLevel == 0) return maxParticles;

        float multiplier = switch (optimizationLevel) {
            case 1 -> 0.9f;
            case 2 -> 0.7f;
            case 3 -> 0.5f;
            default -> 1.0f;
        };

        return (int) (maxParticles * multiplier);
    }

    // === ЗАВАНТАЖЕННЯ ТА ЗБЕРЕЖЕННЯ ===
    public void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                    YACLConfig loadedConfig = GSON.fromJson(reader, YACLConfig.class);
                    if (loadedConfig != null) {
                        copyFrom(loadedConfig);
                        System.out.println("Конфігурацію Veltium завантажено успішно!");
                    }
                }
            } else {
                save();
                System.out.println("Створено нову конфігурацію Veltium!");
            }
        } catch (IOException e) {
            System.err.println("Помилка завантаження конфігурації Veltium: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(this, writer);
                if (showNotifications) {
                    System.out.println("Конфігурацію Veltium збережено успішно!");
                }
            }
        } catch (IOException e) {
            System.err.println("Помилка збереження конфігурації Veltium: " + e.getMessage());
        }
    }

    private void copyFrom(YACLConfig other) {
        this.modEnabled = other.modEnabled;
        this.showNotifications = other.showNotifications;
        this.cornerSnap = other.cornerSnap;
        this.optimizationLevel = other.optimizationLevel;
        this.maxParticles = other.maxParticles;
        this.reduceLag = other.reduceLag;
        this.optimizeRendering = other.optimizeRendering;
        this.cullParticles = other.cullParticles;
        this.enableEntityCulling = other.enableEntityCulling;
        this.optimizeChunks = other.optimizeChunks;
        this.fastMath = other.fastMath;
        this.smartMemoryManagement = other.smartMemoryManagement;
        this.tickOptimizationLevel = other.tickOptimizationLevel;
        this.showFpsCounter = other.showFpsCounter;
        this.showMemoryUsage = other.showMemoryUsage;
        this.showPing = other.showPing;
        this.showCoordinates = other.showCoordinates;
        this.showTime = other.showTime;
        this.showAdvancedFps = other.showAdvancedFps;
        this.showAdvancedMemory = other.showAdvancedMemory;
        this.showAdvancedPing = other.showAdvancedPing;
        this.hudPosition = other.hudPosition != null ? other.hudPosition : HudPosition.TOP_LEFT;
        this.hudX = other.hudX;
        this.hudY = other.hudY;
        this.hudUpdateInterval = other.hudUpdateInterval;
        this.hudShadow = other.hudShadow;
        this.hudBold = other.hudBold;
        this.hudScale = other.hudScale;
        this.hudTextOpacity = other.hudTextOpacity;
        this.hudBackgroundOpacity = other.hudBackgroundOpacity;
        this.hudBackgroundColor = other.hudBackgroundColor;
        this.fpsGoodColor = other.fpsGoodColor;
        this.fpsMediumColor = other.fpsMediumColor;
        this.fpsBadColor = other.fpsBadColor;
        this.memoryGoodColor = other.memoryGoodColor;
        this.memoryMediumColor = other.memoryMediumColor;
        this.memoryBadColor = other.memoryBadColor;
        this.pingGoodColor = other.pingGoodColor;
        this.pingMediumColor = other.pingMediumColor;
        this.pingBadColor = other.pingBadColor;
        this.enableCoordinateColors = other.enableCoordinateColors;
        this.coordinatesColor = other.coordinatesColor;
        this.coordinatesXColor = other.coordinatesXColor;
        this.coordinatesYColor = other.coordinatesYColor;
        this.coordinatesZColor = other.coordinatesZColor;
        this.timeColor = other.timeColor;
    }

    // === ГРАФІЧНИЙ ІНТЕРФЕЙС ===
    public static Screen createConfigScreen(Screen parent) {
        YACLConfig config = getInstance();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.translatable("text.optimizationmod.config.title"))

                // === HUD НАЛАШТУВАННЯ ===
                .category(ConfigCategory.createBuilder()
                        .name(Text.translatable("text.optimizationmod.category.hud"))
                        .tooltip(Text.translatable("text.optimizationmod.category.hud.tooltip"))

                        // Головний перемикач
                        .option(createBooleanOption(
                                "text.optimizationmod.option.mod_enabled",
                                "text.optimizationmod.option.mod_enabled.tooltip",
                                true,
                                () -> config.modEnabled,
                                val -> config.modEnabled = val))

                        // Сповіщення
                        .option(createBooleanOption(
                                "text.optimizationmod.option.show_notifications",
                                "text.optimizationmod.option.show_notifications.tooltip",
                                true,
                                () -> config.showNotifications,
                                val -> config.showNotifications = val))

                        // Роздільник
                        .option(LabelOption.create(Text.translatable("text.optimizationmod.separator.hud_elements")))

                        // Елементи HUD
                        .option(createBooleanOption(
                                "text.optimizationmod.option.show_fps",
                                "text.optimizationmod.option.show_fps.tooltip",
                                true,
                                () -> config.showFpsCounter,
                                val -> config.showFpsCounter = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.show_memory",
                                "text.optimizationmod.option.show_memory.tooltip",
                                false,
                                () -> config.showMemoryUsage,
                                val -> config.showMemoryUsage = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.show_ping",
                                "text.optimizationmod.option.show_ping.tooltip",
                                false,
                                () -> config.showPing,
                                val -> config.showPing = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.show_coordinates",
                                "text.optimizationmod.option.show_coordinates.tooltip",
                                true,
                                () -> config.showCoordinates,
                                val -> config.showCoordinates = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.show_time",
                                "text.optimizationmod.option.show_time.tooltip",
                                false,
                                () -> config.showTime,
                                val -> config.showTime = val))

                        // Розширена статистика
                        .option(LabelOption.create(Text.translatable("text.optimizationmod.separator.advanced_stats")))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.show_advanced_fps",
                                "text.optimizationmod.option.show_advanced_fps.tooltip",
                                false,
                                () -> config.showAdvancedFps,
                                val -> config.showAdvancedFps = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.show_advanced_memory",
                                "text.optimizationmod.option.show_advanced_memory.tooltip",
                                false,
                                () -> config.showAdvancedMemory,
                                val -> config.showAdvancedMemory = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.show_advanced_ping",
                                "text.optimizationmod.option.show_advanced_ping.tooltip",
                                false,
                                () -> config.showAdvancedPing,
                                val -> config.showAdvancedPing = val))

                        // Розмір та позиція
                        .option(LabelOption.create(Text.translatable("text.optimizationmod.separator.size_position")))

                        .option(Option.<Float>createBuilder()
                                .name(Text.translatable("text.optimizationmod.option.hud_scale"))
                                .description(OptionDescription.of(Text.translatable("text.optimizationmod.option.hud_scale.tooltip")))
                                .binding(1.0f, () -> config.hudScale, val -> config.hudScale = val)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.5f, 3.0f).step(0.1f))
                                .build())

                        // HUD Position як перемикач
                        .option(Option.<HudPosition>createBuilder()
                                .name(Text.translatable("text.optimizationmod.option.hud_position"))
                                .description(OptionDescription.of(Text.translatable("text.optimizationmod.option.hud_position.tooltip")))
                                .binding(HudPosition.TOP_LEFT,
                                        () -> config.hudPosition,
                                        val -> config.hudPosition = val)
                                .controller(opt -> CyclingListControllerBuilder.create(opt)
                                        .values(java.util.Arrays.asList(HudPosition.values()))
                                        .formatValue(pos -> pos.getDisplayName()))
                                .build())

                        .option(createBooleanOption(
                                "text.optimizationmod.option.corner_snap",
                                "text.optimizationmod.option.corner_snap.tooltip",
                                false,
                                () -> config.cornerSnap,
                                val -> config.cornerSnap = val))

                        .option(Option.<Integer>createBuilder()
                                .name(Text.translatable("text.optimizationmod.option.hud_x"))
                                .description(OptionDescription.of(Text.translatable("text.optimizationmod.option.hud_x.tooltip")))
                                .binding(10, () -> config.hudX, val -> config.hudX = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 500).step(1))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Text.translatable("text.optimizationmod.option.hud_y"))
                                .description(OptionDescription.of(Text.translatable("text.optimizationmod.option.hud_y.tooltip")))
                                .binding(10, () -> config.hudY, val -> config.hudY = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 500).step(1))
                                .build())

                        // Зовнішній вигляд
                        .option(LabelOption.create(Text.translatable("text.optimizationmod.separator.text_appearance")))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.hud_shadow",
                                "text.optimizationmod.option.hud_shadow.tooltip",
                                true,
                                () -> config.hudShadow,
                                val -> config.hudShadow = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.hud_bold",
                                "text.optimizationmod.option.hud_bold.tooltip",
                                false,
                                () -> config.hudBold,
                                val -> config.hudBold = val))

                        .option(Option.<Float>createBuilder()
                                .name(Text.translatable("text.optimizationmod.option.hud_text_opacity"))
                                .description(OptionDescription.of(Text.translatable("text.optimizationmod.option.hud_text_opacity.tooltip")))
                                .binding(1.0f, () -> config.hudTextOpacity, val -> config.hudTextOpacity = val)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.1f, 1.0f).step(0.1f))
                                .build())

                        // Кольори
                        .option(LabelOption.create(Text.translatable("text.optimizationmod.separator.colors")))

                        .option(Option.<Color>createBuilder()
                                .name(Text.translatable("text.optimizationmod.option.coordinates_color"))
                                .description(OptionDescription.of(Text.translatable("text.optimizationmod.option.coordinates_color.tooltip")))
                                .binding(intToColor(0xFFFFFF), () -> intToColor(config.coordinatesColor), val -> config.coordinatesColor = colorToInt(val))
                                .controller(ColorControllerBuilder::create)
                                .build())

                        // Кольори координат
                        .group(OptionGroup.createBuilder()
                                .name(Text.translatable("text.optimizationmod.category.coordinate_colors"))
                                .collapsed(true)

                                .option(createBooleanOption(
                                        "text.optimizationmod.option.enable_coordinate_colors",
                                        "text.optimizationmod.option.enable_coordinate_colors.tooltip",
                                        false,
                                        () -> config.enableCoordinateColors,
                                        val -> config.enableCoordinateColors = val))

                                .option(createColorOption(
                                        "text.optimizationmod.option.coordinates_x_color",
                                        "text.optimizationmod.option.coordinates_x_color.tooltip",
                                        0xFF5555,
                                        () -> config.coordinatesXColor,
                                        val -> config.coordinatesXColor = val))

                                .option(createColorOption(
                                        "text.optimizationmod.option.coordinates_y_color",
                                        "text.optimizationmod.option.coordinates_y_color.tooltip",
                                        0x55FF55,
                                        () -> config.coordinatesYColor,
                                        val -> config.coordinatesYColor = val))

                                .option(createColorOption(
                                        "text.optimizationmod.option.coordinates_z_color",
                                        "text.optimizationmod.option.coordinates_z_color.tooltip",
                                        0x5555FF,
                                        () -> config.coordinatesZColor,
                                        val -> config.coordinatesZColor = val))
                                .build())

                        .option(createColorOption(
                                "text.optimizationmod.option.time_color",
                                "text.optimizationmod.option.time_color.tooltip",
                                0xFFFFFF,
                                () -> config.timeColor,
                                val -> config.timeColor = val))

                        // Кольорові групи
                        .group(createFpsColorGroup(config))
                        .group(createMemoryColorGroup(config))
                        .group(createPingColorGroup(config))

                        .build())

                // === ОПТИМІЗАЦІЯ ===
                .category(ConfigCategory.createBuilder()
                        .name(Text.translatable("text.optimizationmod.category.optimization"))
                        .tooltip(Text.translatable("text.optimizationmod.category.optimization.tooltip"))

                        .option(Option.<Integer>createBuilder()
                                .name(Text.translatable("text.optimizationmod.option.optimization_level"))
                                .description(OptionDescription.of(Text.translatable("text.optimizationmod.option.optimization_level.tooltip")))
                                .binding(0, () -> config.optimizationLevel, val -> config.optimizationLevel = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 3).step(1))
                                .build())

                        .option(createBooleanOption(
                                "text.optimizationmod.option.reduce_lag",
                                "text.optimizationmod.option.reduce_lag.tooltip",
                                false,
                                () -> config.reduceLag,
                                val -> config.reduceLag = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.optimize_rendering",
                                "text.optimizationmod.option.optimize_rendering.tooltip",
                                false,
                                () -> config.optimizeRendering,
                                val -> config.optimizeRendering = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.cull_particles",
                                "text.optimizationmod.option.cull_particles.tooltip",
                                true,
                                () -> config.cullParticles,
                                val -> config.cullParticles = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.entity_culling",
                                "text.optimizationmod.option.entity_culling.tooltip",
                                false,
                                () -> config.enableEntityCulling,
                                val -> config.enableEntityCulling = val))

                        .option(createBooleanOption(
                                "text.optimizationmod.option.optimize_chunks",
                                "text.optimizationmod.option.optimize_chunks.tooltip",
                                false,
                                () -> config.optimizeChunks,
                                val -> config.optimizeChunks = val))

                        .build())

                .save(config::save)
                .build()
                .generateScreen(parent);
    }

    // === ДОПОМІЖНІ МЕТОДИ ===
    private static Option<Boolean> createBooleanOption(String nameKey, String tooltipKey,
                                                       boolean defaultValue,
                                                       java.util.function.Supplier<Boolean> getter,
                                                       java.util.function.Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Text.translatable(nameKey))
                .description(OptionDescription.of(Text.translatable(tooltipKey)))
                .binding(defaultValue, getter, setter)
                .controller(opt -> BooleanControllerBuilder.create(opt)
                        .formatValue(val -> val ?
                                Text.translatable("text.optimizationmod.yes").formatted(net.minecraft.util.Formatting.GREEN) :
                                Text.translatable("text.optimizationmod.no").formatted(net.minecraft.util.Formatting.RED)))
                .build();
    }

    private static Option<Color> createColorOption(String nameKey, String tooltipKey,
                                                   int defaultValue,
                                                   java.util.function.Supplier<Integer> getter,
                                                   java.util.function.Consumer<Integer> setter) {
        return Option.<Color>createBuilder()
                .name(Text.translatable(nameKey))
                .description(OptionDescription.of(Text.translatable(tooltipKey)))
                .binding(intToColor(defaultValue), () -> intToColor(getter.get()), val -> setter.accept(colorToInt(val)))
                .controller(ColorControllerBuilder::create)
                .build();
    }

    private static OptionGroup createFpsColorGroup(YACLConfig config) {
        return OptionGroup.createBuilder()
                .name(Text.translatable("text.optimizationmod.category.fps_colors"))
                .collapsed(true)
                .option(createColorOption(
                        "text.optimizationmod.option.fps_good_color",
                        "text.optimizationmod.option.fps_good_color.tooltip",
                        0x55FF55,
                        () -> config.fpsGoodColor,
                        val -> config.fpsGoodColor = val))
                .option(createColorOption(
                        "text.optimizationmod.option.fps_medium_color",
                        "text.optimizationmod.option.fps_medium_color.tooltip",
                        0xFFFF55,
                        () -> config.fpsMediumColor,
                        val -> config.fpsMediumColor = val))
                .option(createColorOption(
                        "text.optimizationmod.option.fps_bad_color",
                        "text.optimizationmod.option.fps_bad_color.tooltip",
                        0xFF5555,
                        () -> config.fpsBadColor,
                        val -> config.fpsBadColor = val))
                .build();
    }

    private static OptionGroup createMemoryColorGroup(YACLConfig config) {
        return OptionGroup.createBuilder()
                .name(Text.translatable("text.optimizationmod.category.memory_colors"))
                .collapsed(true)
                .option(createColorOption(
                        "text.optimizationmod.option.memory_good_color",
                        "text.optimizationmod.option.memory_good_color.tooltip",
                        0x55FF55,
                        () -> config.memoryGoodColor,
                        val -> config.memoryGoodColor = val))
                .option(createColorOption(
                        "text.optimizationmod.option.memory_medium_color",
                        "text.optimizationmod.option.memory_medium_color.tooltip",
                        0xFFFF55,
                        () -> config.memoryMediumColor,
                        val -> config.memoryMediumColor = val))
                .option(createColorOption(
                        "text.optimizationmod.option.memory_bad_color",
                        "text.optimizationmod.option.memory_bad_color.tooltip",
                        0xFF5555,
                        () -> config.memoryBadColor,
                        val -> config.memoryBadColor = val))
                .build();
    }

    private static OptionGroup createPingColorGroup(YACLConfig config) {
        return OptionGroup.createBuilder()
                .name(Text.translatable("text.optimizationmod.category.ping_colors"))
                .collapsed(true)
                .option(createColorOption(
                        "text.optimizationmod.option.ping_good_color",
                        "text.optimizationmod.option.ping_good_color.tooltip",
                        0x55FF55,
                        () -> config.pingGoodColor,
                        val -> config.pingGoodColor = val))
                .option(createColorOption(
                        "text.optimizationmod.option.ping_medium_color",
                        "text.optimizationmod.option.ping_medium_color.tooltip",
                        0xFFFF55,
                        () -> config.pingMediumColor,
                        val -> config.pingMediumColor = val))
                .option(createColorOption(
                        "text.optimizationmod.option.ping_bad_color",
                        "text.optimizationmod.option.ping_bad_color.tooltip",
                        0xFF5555,
                        () -> config.pingBadColor,
                        val -> config.pingBadColor = val))
                .build();
    }
}