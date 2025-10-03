package com.example;

import com.example.config.YACLConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class OptimizationModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// використовуєм YACL
		return YACLConfig::createConfigScreen;
	}
}