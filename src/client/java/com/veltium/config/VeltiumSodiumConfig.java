package com.veltium.config;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ExternalPageBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class VeltiumSodiumConfig implements ConfigEntryPoint {

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        YACLConfig config = YACLConfig.getInstance();
        StorageEventHandler save = config::save;

        OptionPageBuilder hudPage = builder.createOptionPage()
                .setName(Component.translatable("text.veltium.sodium.page.hud"));
        hudPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("text.veltium.sodium.group.main"))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:mod_enabled"))
                        .setName(Component.translatable("text.veltium.option.mod_enabled"))
                        .setTooltip(tooltip("text.veltium.option.mod_enabled.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.modEnabled = val, () -> config.modEnabled)
                        .setDefaultValue(true))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_notifications"))
                        .setName(Component.translatable("text.veltium.option.show_notifications"))
                        .setTooltip(tooltip("text.veltium.option.show_notifications.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showNotifications = val, () -> config.showNotifications)
                        .setDefaultValue(true)));
        hudPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("text.veltium.sodium.group.hud_elements"))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_fps"))
                        .setName(Component.translatable("text.veltium.option.show_fps"))
                        .setTooltip(tooltip("text.veltium.option.show_fps.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showFpsCounter = val, () -> config.showFpsCounter)
                        .setDefaultValue(true))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_memory"))
                        .setName(Component.translatable("text.veltium.option.show_memory"))
                        .setTooltip(tooltip("text.veltium.option.show_memory.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showMemoryUsage = val, () -> config.showMemoryUsage)
                        .setDefaultValue(false))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_ping"))
                        .setName(Component.translatable("text.veltium.option.show_ping"))
                        .setTooltip(tooltip("text.veltium.option.show_ping.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showPing = val, () -> config.showPing)
                        .setDefaultValue(false))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_coordinates"))
                        .setName(Component.translatable("text.veltium.option.show_coordinates"))
                        .setTooltip(tooltip("text.veltium.option.show_coordinates.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showCoordinates = val, () -> config.showCoordinates)
                        .setDefaultValue(true))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_time"))
                        .setName(Component.translatable("text.veltium.option.show_time"))
                        .setTooltip(tooltip("text.veltium.option.show_time.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showTime = val, () -> config.showTime)
                        .setDefaultValue(false))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_days"))
                        .setName(Component.translatable("text.veltium.option.show_days"))
                        .setTooltip(tooltip("text.veltium.option.show_days.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showDays = val, () -> config.showDays)
                        .setDefaultValue(false))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_world_time"))
                        .setName(Component.translatable("text.veltium.option.show_world_time"))
                        .setTooltip(tooltip("text.veltium.option.show_world_time.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showWorldTime = val, () -> config.showWorldTime)
                        .setDefaultValue(false)));
        hudPage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("text.veltium.sodium.group.advanced_stats"))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_advanced_fps"))
                        .setName(Component.translatable("text.veltium.option.show_advanced_fps"))
                        .setTooltip(tooltip("text.veltium.option.show_advanced_fps.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showAdvancedFps = val, () -> config.showAdvancedFps)
                        .setDefaultValue(false))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_advanced_memory"))
                        .setName(Component.translatable("text.veltium.option.show_advanced_memory"))
                        .setTooltip(tooltip("text.veltium.option.show_advanced_memory.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showAdvancedMemory = val, () -> config.showAdvancedMemory)
                        .setDefaultValue(false))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:show_advanced_ping"))
                        .setName(Component.translatable("text.veltium.option.show_advanced_ping"))
                        .setTooltip(tooltip("text.veltium.option.show_advanced_ping.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.showAdvancedPing = val, () -> config.showAdvancedPing)
                        .setDefaultValue(false)));

        OptionPageBuilder appearancePage = builder.createOptionPage()
                .setName(Component.translatable("text.veltium.sodium.page.appearance"));
        appearancePage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("text.veltium.sodium.group.text"))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:hud_shadow"))
                        .setName(Component.translatable("text.veltium.option.hud_shadow"))
                        .setTooltip(tooltip("text.veltium.option.hud_shadow.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.hudShadow = val, () -> config.hudShadow)
                        .setDefaultValue(true))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:hud_bold"))
                        .setName(Component.translatable("text.veltium.option.hud_bold"))
                        .setTooltip(tooltip("text.veltium.option.hud_bold.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.hudBold = val, () -> config.hudBold)
                        .setDefaultValue(false))
                .addOption(builder.createIntegerOption(Identifier.parse("veltium:hud_text_opacity"))
                        .setName(Component.translatable("text.veltium.option.hud_text_opacity"))
                        .setTooltip(tooltip("text.veltium.option.hud_text_opacity.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.hudTextOpacity = val / 100.0f, () -> (int) (config.hudTextOpacity * 100))
                        .setDefaultValue(100)
                        .setRange(10, 100, 5)
                        .setValueFormatter(val -> Component.literal(val + "%"))));
        appearancePage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("text.veltium.sodium.group.position"))
                .addOption(builder.createEnumOption(Identifier.parse("veltium:hud_position"), YACLConfig.HudPosition.class)
                        .setName(Component.translatable("text.veltium.option.hud_position"))
                        .setTooltip(tooltip("text.veltium.option.hud_position.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.hudPosition = val, () -> config.hudPosition)
                        .setDefaultValue(YACLConfig.HudPosition.TOP_LEFT)
                        .setElementNameProvider(pos -> Component.translatable("text.veltium.hud_position." + pos.name().toLowerCase())))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:corner_snap"))
                        .setName(Component.translatable("text.veltium.option.corner_snap"))
                        .setTooltip(tooltip("text.veltium.option.corner_snap.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.cornerSnap = val, () -> config.cornerSnap)
                        .setDefaultValue(false))
                .addOption(builder.createIntegerOption(Identifier.parse("veltium:hud_x"))
                        .setName(Component.translatable("text.veltium.option.hud_x"))
                        .setTooltip(tooltip("text.veltium.option.hud_x.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.hudX = val, () -> config.hudX)
                        .setDefaultValue(10)
                        .setEnabledProvider(state -> !state.readEnumOption(Identifier.parse("veltium:hud_position"), YACLConfig.HudPosition.class).isCenterX(),
                                Identifier.parse("veltium:hud_position"))
                        .setControlHiddenWhenDisabled(false)
                        .setRange(0, 500, 1)
                        .setValueFormatter(val -> Component.literal(val + "px")))
                .addOption(builder.createIntegerOption(Identifier.parse("veltium:hud_y"))
                        .setName(Component.translatable("text.veltium.option.hud_y"))
                        .setTooltip(tooltip("text.veltium.option.hud_y.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.hudY = val, () -> config.hudY)
                        .setDefaultValue(10)
                        .setRange(0, 500, 1)
                        .setValueFormatter(val -> Component.literal(val + "px")))
                .addOption(builder.createIntegerOption(Identifier.parse("veltium:hud_scale"))
                        .setName(Component.translatable("text.veltium.option.hud_scale"))
                        .setTooltip(tooltip("text.veltium.option.hud_scale.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.hudScale = val / 100.0f, () -> (int) (config.hudScale * 100))
                        .setDefaultValue(100)
                        .setRange(50, 300, 10)
                        .setValueFormatter(val -> Component.literal(val + "%"))));
        appearancePage.addOptionGroup(builder.createOptionGroup()
                .setName(Component.translatable("text.veltium.sodium.group.coordinates"))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:coordinates_show_decimals"))
                        .setName(Component.translatable("text.veltium.option.coordinates_show_decimals"))
                        .setTooltip(tooltip("text.veltium.option.coordinates_show_decimals.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.coordinatesShowDecimals = val, () -> config.coordinatesShowDecimals)
                        .setDefaultValue(true)
                        .setEnabledProvider(state -> state.readBooleanOption(Identifier.parse("veltium:show_coordinates")),
                                Identifier.parse("veltium:show_coordinates"))
                        .setControlHiddenWhenDisabled(false))
                .addOption(builder.createBooleanOption(Identifier.parse("veltium:enable_coordinate_colors"))
                        .setName(Component.translatable("text.veltium.option.enable_coordinate_colors"))
                        .setTooltip(tooltip("text.veltium.option.enable_coordinate_colors.tooltip"))
                        .setStorageHandler(save)
                        .setBinding(val -> config.enableCoordinateColors = val, () -> config.enableCoordinateColors)
                        .setDefaultValue(false)
                        .setEnabledProvider(state -> state.readBooleanOption(Identifier.parse("veltium:show_coordinates")),
                                Identifier.parse("veltium:show_coordinates"))
                        .setControlHiddenWhenDisabled(false)));

        ExternalPageBuilder advancedPage = builder.createExternalPage()
                .setName(Component.translatable("text.veltium.sodium.page.advanced"))
                .setScreenConsumer((Screen screen) -> Minecraft.getInstance().setScreen(YACLConfig.createConfigScreen(screen)));

        builder.registerOwnModOptions()
                .setIcon(Identifier.parse("template-mod:icon.png"))
                .addPage(hudPage)
                .addPage(appearancePage)
                .addPage(advancedPage);
    }

    private static Component tooltip(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.AQUA);
    }
}