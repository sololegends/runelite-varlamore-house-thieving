package com.sololegends.runelite;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

import javax.imageio.ImageIO;

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
import net.runelite.client.events.ConfigChanged;
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
	private VarlamoreHouseThievingMinimapOverlay thieving_minimap_overlay;

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
		overlay_manager.add(thieving_minimap_overlay);
		overlay_manager.add(next_up_overlay);
	}

	@Override
	protected void shutDown() throws Exception {
		log.info("Stopping Varlamore House Thieving!");
		overlay_manager.remove(thieving_overlay);
		overlay_manager.remove(thieving_minimap_overlay);
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
	private boolean distraction_alerted = false;
	private long last_notify = -1;
	private boolean done_stealing_notified = false;
	private long flick_threshold = 450;
	private long last_flick = 0;
	private boolean flick = false;

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

	private BufferedImage ICON = null;

	private void reloadIcon() {
		try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("icon.png")) {
			int icon_width = config.debugIconSize();
			ICON = new BufferedImage(icon_width, icon_width, BufferedImage.TYPE_INT_ARGB);
			BufferedImage icon = ImageIO.read(is);
			int w = icon.getWidth();
			double scale_x = ((double) icon_width) / w;
			int h = icon.getHeight();
			double scale_y = ((double) icon_width) / h;
			AffineTransform at = new AffineTransform();
			at.scale(scale_x, scale_y);
			AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
			ICON = scaleOp.filter(icon, ICON);
		} catch (IOException e) {
			System.err.println("Failed to load varlamore thieving icon:");
			e.printStackTrace();
		}
	}

	public BufferedImage icon() {
		if (ICON == null) {
			reloadIcon();
		}
		return ICON;
	}

	public boolean flick() {
		return flick || config.disableIconFlashing();
	}

	@Subscribe
	public void onClientTick(ClientTick event) {
		if (System.currentTimeMillis() - last_flick > flick_threshold) {
			last_flick = System.currentTimeMillis();
			flick = !flick;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		// Optimization to not run through renderings when not in the varlamore city
		if (client.getGameState() != GameState.LOGGED_IN || !playerInActivity()) {
			return;
		}
		// ============================================
		// NPC HANDLING
		// ============================================
		boolean in_house_distract = config.inHouseShowDistraction() && Houses.inLaviniaHouse(client.getLocalPlayer());
		boolean in_house = Houses.inHouse(client.getLocalPlayer());
		boolean found_distracted = false;
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
					found_distracted = true;
					continue;
				}
			}
		}
		// If we found a distracted citizen alert if needed
		if (found_distracted && !distraction_alerted) {
			// If player not in a house
			if (in_house_distract || !in_house) {
				if ((config.enableDistractedOverlay() || config.notifyOnDistracted())) {
					distraction_alerted = true;
					if (config.enableDistractedOverlay()) {
						NextUpOverlayPanel.trackDistraction();
					}
					if (config.notifyOnDistracted()) {
						notify("A Wealthy citizen is being distracted!");
					}
				}
			}
		} else if (!found_distracted) {
			distraction_alerted = false;
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

		// ============================================
		// Time threshold notifications
		// ============================================
		// Time since last distraction notification check
		long time_since_last_distraction = NextUpOverlayPanel.sinceDistraction();

		if (// If distraction overlay is enabled OR notifications on distracted is enabled
		(config.enableDistractedOverlay() || config.notifyOnDistracted())
				// If player configured to track distraction in house OR player is not in a
				// house at all
				&& (in_house_distract || !in_house)
				// If threshold is configured and has been met
				&& config.notifyOnTimeSinceDistraction() > 0
				&& config.notifyOnTimeSinceDistraction() == time_since_last_distraction) {
			String second_or_seconds = config.notifyOnTimeSinceDistraction() == 1 ? " second" : " seconds";
			notify("It has been " + time_since_last_distraction + second_or_seconds + " since the last distraction");
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

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		// Update whitelist when whitelist config changed
		if (event.getKey().equals("debugging_icon_size")) {
			reloadIcon();
		}
	}

	@Provides
	VarlamoreHouseThievingConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(VarlamoreHouseThievingConfig.class);
	}
}
