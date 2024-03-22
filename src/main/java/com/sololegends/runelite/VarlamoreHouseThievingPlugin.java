package com.sololegends.runelite;

import com.google.inject.Inject;
import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(name = "House Thieving Varlamore", description = "Highlights useful things when robbing houses in Varlamore", tags = {
		"thieving", "houses", "varlamore" })
public class VarlamoreHouseThievingPlugin extends Plugin {

	public static final int LAVINIA_ID = 13312;
	public static final int VICTOR_ID = 13313;
	public static final int CAIUS_ID = 13314;
	public static final String WEALTHY_CITIZEN_NAME = "Wealthy citizen";

	public static final int LOCKED_DOOR_ID = 51998;
	public static final int UNLOCKED_DOOR_ID = 51999;
	public static final int ESCAPE_WINDOW_ID = 52998;

	public static final int VALUABLE_VALUE = 55;
	public static final int TIME_UNTIL_RETURN = 0;
	public static final int TILE_WIDTH = 128;
	public static final int DISTANCE_DOOR = 12 * TILE_WIDTH;
	public static final int DISTANCE_OWNER = 10 * TILE_WIDTH;

	@Inject
	private Client client;

	@Inject
	private VarlamoreHouseThievingOverlay bog_overlay;

	@Inject
	private OverlayManager overlay_manager;

	@Override
	protected void startUp() throws Exception {
		log.info("Starting Varlamore House Thieving");
		overlay_manager.add(bog_overlay);
	}

	@Override
	protected void shutDown() throws Exception {
		log.info("Stopping Varlamore House Thieving!");
		overlay_manager.remove(bog_overlay);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (event.getType() != ChatMessageType.GAMEMESSAGE) {
			return;
		}
		// TODO: Something with the chat messages?
	}

	@Subscribe
	public void onGameTick(GameTick event) {
	}

	@Provides
	VarlamoreHouseThievingConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(VarlamoreHouseThievingConfig.class);
	}
}
