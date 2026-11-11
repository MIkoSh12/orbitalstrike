package com.example.orbitalstrike;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.List;

public class OrbitalStrikeMod implements ModInitializer {
	public static final String MOD_ID = "orbitalstrike";

	/** Активные удары. Трогаем только из серверного потока. */
	public static final List<OrbitalStrike> ACTIVE = new ArrayList<>();

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				OrbitalCommand.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(server ->
				ACTIVE.removeIf(OrbitalStrike::tick));

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE.clear());
	}
}
