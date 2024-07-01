package com.sololegends.runelite;

import java.util.HashSet;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.sololegends.panel.NextUpOverlayPanel;
import com.sololegends.runelite.data.Houses;
import com.sololegends.runelite.data.Houses.House;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
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
	public static final int DISTANCE_DOOR_AWAY = 32 * TILE_WIDTH;
	public static final int DISTANCE_OWNER = 5;

	// Sound effects
	// 2115 - Bonus Collected sound ID
	// 3147 - Bonus Available sound ID
	private static final int STATUE_SOUND_EFFECT = 2655;

	@Inject
	private Client client;

	@Inject
	private Notifier notifier;

	@Inject
	private VarlamoreHouseThievingOverlay thieving_overlay;

	@Inject
	private VarlamoreHouseThievingConfig config;

	@Inject
	private OverlayManager overlay_manager;

	@Inject
	private NextUpOverlayPanel next_up_overlay;

	@Override
	protected void startUp() throws Exception {
		log.info("Starting Varlamore House Thieving");
		overlay_manager.add(thieving_overlay);
		overlay_manager.add(next_up_overlay);
	}

	@Override
	protected void shutDown() throws Exception {
		log.info("Stopping Varlamore House Thieving!");
		overlay_manager.remove(thieving_overlay);
		overlay_manager.remove(next_up_overlay);
	}

	public boolean playerInActivity() {
		Player p = client.getLocalPlayer();
		WorldPoint wl = p.getWorldLocation();
		// Optimization to not run through renderings when not in the varlamore city
		if (wl.getRegionID() == 6448 || wl.getRegionID() == 6704) {
			return true;
		}
		return false;
	}

	private static final long NOTIFY_TIMEOUT = 10_000;
	private HashSet<Integer> NOTIFIED = new HashSet<>();
	private long last_notify = -1;
	private boolean npc_hint_active = false;
	private boolean done_stealing_notified = false;

	private final void notify(String message) {
		if (System.currentTimeMillis() - last_notify > NOTIFY_TIMEOUT) {
			notifier.notify(message);
			last_notify = System.currentTimeMillis();
		}
	}

	public NPC[] getCachedNPCs() {
		WorldView wv = client.getTopLevelWorldView();
		return wv == null ? new NPC[0] : wv.npcs().getSparse();
	}

	public Scene getScene() {
		WorldView wv = client.getTopLevelWorldView();
		return wv == null ? null : wv.getScene();
	}

	public int getPlane() {
		return client.getTopLevelWorldView().getPlane();
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (npc_hint_active) {
			client.clearHintArrow();
			npc_hint_active = false;
		}
		// Optimization to not run through renderings when not in the varlamore city
		if (client.getGameState() != GameState.LOGGED_IN || !playerInActivity()) {
			return;
		}
		// ============================================
		// NPC HANDLING
		// ============================================
		for (NPC npc : getCachedNPCs()) {
			if (npc == null) {
				continue;
			}
			if (npc.getId() == VarlamoreHouseThievingPlugin.LAVINIA_ID
					|| npc.getId() == VarlamoreHouseThievingPlugin.CAIUS_ID
					|| npc.getId() == VarlamoreHouseThievingPlugin.VICTOR_ID) {
				// check position relative to the door
				House house = Houses.getHouse(npc.getId());
				if (house == null) {
					continue;
				}
				int dist = npc.getWorldLocation().distanceTo2D(house.door.getWorldLocation());
				if (config.notifyOnReturnHome() && config.enableReturnHomeOverlay()
						&& house.door.isLocked()
						&& house.contains(client.getLocalPlayer().getWorldLocation())
						&& dist < VarlamoreHouseThievingPlugin.DISTANCE_OWNER) {
					if (!NOTIFIED.contains(npc.getId())) {
						notify("The owner is coming home! RUUUUUUNN!");
						NOTIFIED.add(npc.getId());
					}
				} else {
					NOTIFIED.remove(npc.getId());
				}
			}
			if (npc.getName().equals(VarlamoreHouseThievingPlugin.WEALTHY_CITIZEN_NAME)) {
				// If they are interacting with child
				if (npc.isInteracting()) {
					Actor a = npc.getInteracting();
					if (a == null || a.getCombatLevel() != 0) {
						continue;
					}
					// If player not in a house
					boolean in_house_distract = config.inHouseShowDistraction() && Houses.inLaviniaHouse(client.getLocalPlayer());
					if (in_house_distract || !Houses.inHouse(client.getLocalPlayer())) {
						client.setHintArrow(npc);
						npc_hint_active = true;
						if ((config.enableDistractedOverlay() || config.notifyOnDistracted())
								&& !NOTIFIED.contains(npc.getId())) {
							if (config.enableDistractedOverlay()) {
								NextUpOverlayPanel.trackDistraction();
							}
							if (config.notifyOnDistracted()) {
								notify("A Wealthy citizen is being distracted!");
							}
							NOTIFIED.add(npc.getId());
						}
					}
					continue;
				}
				NOTIFIED.remove(npc.getId());
			}
		}
		// ============================================
		// Can't spot anything else check
		// ============================================
		if (config.notifyOnEmptyContainer()) {
			// 15007745 = Full chatbox single text message widget ID
			Widget widget = client.getWidget(15007745);
			if (widget != null && widget.getText() != null
					&& widget.getText().toLowerCase().startsWith("you can't spot anything else worth taking")) {
				if (!done_stealing_notified) {
					done_stealing_notified = true;
					notify("You can't spot anything else worth stealing");
				}
			} else {
				done_stealing_notified = false;
			}
		} else {
			done_stealing_notified = false;
		}
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed sound) {
		int sound_id = sound.getSoundId();
		if (config.disableStatueSoundEffect()
				&& sound_id == VarlamoreHouseThievingPlugin.STATUE_SOUND_EFFECT) {
			sound.consume();
		}
	}

	@Provides
	VarlamoreHouseThievingConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(VarlamoreHouseThievingConfig.class);
	}
}
