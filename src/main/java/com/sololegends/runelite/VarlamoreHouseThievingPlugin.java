package com.sololegends.runelite;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.sololegends.panel.NextUpOverlayPanel;
import com.sololegends.runelite.data.Houses;
import com.sololegends.runelite.data.Houses.House;
import com.sololegends.runelite.debug.GraceMeter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(name = "House Thieving Varlamore", description = "Highlights useful things when robbing houses in Varlamore", tags = {
		"thieving", "houses", "varlamore", "wealthy", "citizen" })
public class VarlamoreHouseThievingPlugin extends Plugin {

	public static final int LAVINIA_ID = 13312;
	public static final int VICTOR_ID = 13313;
	public static final int CAIUS_ID = 13314;
	public static final String WEALTHY_CITIZEN_NAME = "Wealthy citizen";

	public static final int LOCKED_DOOR_ID = 51998;
	public static final int UNLOCKED_DOOR_ID = 51999;
	public static final int ESCAPE_WINDOW_ID = 52998;

	public static final int VALUABLE_VALUE = 55;
	public static final int VALUABLE_GLORY_VALUE = 65;
	public static final int TIME_UNTIL_RETURN = 0;
	public static final int TILE_WIDTH = 128;
	public static final int DISTANCE_DOOR = 8 * TILE_WIDTH;
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

	private boolean containerEmpty() {
		// 15007745 = Full chatbox single text message widget ID
		Widget widget = client.getWidget(15007747);
		return widget != null && widget.getText() != null
				&& widget.getText().toLowerCase().startsWith("you can't spot anything else worth taking");
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
	private final GraceMeter grace_meter = new GraceMeter();

	/**
	 * The container searching animation. Matching it directly beats inferring the
	 * activity from what you last clicked, which could not tell looting apart from
	 * alching, chiselling, or switching tabs.
	 *
	 * <p>
	 * Looting a bonus chest plays 11091 first, but that runs straight into this
	 * one, so the tail below carries the quiet across and there is nothing to be
	 * gained from matching it separately.
	 * </p>
	 */
	private static final int LOOTING_ANIMATION = 11075;

	/**
	 * Covers the gap between rolls so the highlights do not strobe back on. Kept
	 * generous because every exit that wants to be prompt is signalled outright,
	 * an emptied container, leaving the house, or the owner coming back.
	 */
	private static final long LOOTING_TAIL = 2_000;
	private long last_looting = -1;
	private boolean owner_returning = false;



	/**
	 * The game's own warning, measured as the only dependable anchor. Owner
	 * proximity fires whenever the NPC happens to render, which came out anywhere
	 * between 2.4s and 9.6s before the same event.
	 */
	private static final String OWNER_WARNING = "homeowner coming back";
	private static final long WARNING_TIMEOUT = 30_000;
	private long warned_at = -1;
	private int warned_house = -1;

	private long bonus_since = -1;
	private int last_animation = -1;

	/**
	 * Driven from the overlay every frame so the window can be timed from the
	 * moment the chest is marked to the moment the mark goes.
	 */
	public void trackBonusChest(boolean present) {
		if (present) {
			if (bonus_since == -1) {
				bonus_since = System.currentTimeMillis();
			}
			return;
		}
		if (bonus_since != -1) {
			long lifetime = System.currentTimeMillis() - bonus_since;
			bonus_since = -1;
			if (config.debugGraceMeter()) {
				report(String.format("Bonus chest window lasted %.1fs, though looting it ends it early",
						lifetime / 1000D));
			}
		}
	}

	/** Seconds left of the configured window, negative once it has overrun */
	public long bonusRemaining() {
		if (bonus_since == -1) {
			return 0;
		}
		long elapsed = System.currentTimeMillis() - bonus_since;
		return ((config.bonusChestWindow() * 1000L) - elapsed) / 1000;
	}

	/** 52008 Box, 52010 Wardrobe, 52011 Jewellery Box */
	public static boolean isContainer(int id) {
		return id == 52008 || id == 52010 || id == 52011;
	}

	public boolean ownerReturning() {
		return owner_returning;
	}

	/** Heads down working a chest, rather than walking, waiting or escaping */
	public boolean isLooting() {
		Player player = client.getLocalPlayer();
		if (player == null || !Houses.inHouse(player)) {
			return false;
		}
		return last_looting != -1 && System.currentTimeMillis() - last_looting < LOOTING_TAIL;
	}

	/**
	 * True while you are heads down looting and nothing needs your attention. The
	 * bonus chest is drawn regardless, and the owner turning up drops the quiet
	 * entirely so the escape window comes back.
	 */
	public boolean quietWhileLooting() {
		return config.quietWhileLooting() && !owner_returning && isLooting();
	}

	private static final String CHAT_PREFIX = "[House Thieving] ";
	private boolean reporting = false;

	/**
	 * Goes to the chatbox so it can be read live, and to the log for later.
	 *
	 * <p>
	 * Posting to the chatbox raises a ChatMessage of its own, so this must never
	 * be reachable from a ChatMessage handler that could match its own output.
	 * The type, the prefix and this flag are three independent stops on that.
	 * </p>
	 */
	private void report(String message) {
		log.info("[grace meter] {}", message);
		if (reporting) {
			return;
		}
		reporting = true;
		try {
			client.addChatMessage(ChatMessageType.CONSOLE, "", CHAT_PREFIX + message, null);
		} finally {
			reporting = false;
		}
	}

	private final void notify(Notification config, String message) {
		if (System.currentTimeMillis() - last_notify > NOTIFY_TIMEOUT) {
			notifier.notify(config, message);
			last_notify = System.currentTimeMillis();
		}
	}

	public List<NPC> getCachedNPCs() {
		WorldView wv = client.getTopLevelWorldView();
		return wv == null ? new ArrayList<NPC>()
				: wv.npcs()
						.stream()
						.collect(Collectors.toCollection(ArrayList::new));
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
		int icon_width = config.debugIconSize();
		ICON = new BufferedImage(icon_width, icon_width, BufferedImage.TYPE_INT_ARGB);
		String icon_name = config.useArrowIcon() ? "icon_arrow.png" : "icon.png";
		try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(icon_name)) {
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
			log.error("Failed to load varlamore thieving icon:", e);
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
			owner_returning = false;
			last_looting = -1;
			warned_house = -1;
			bonus_since = -1;
			return;
		}
		// ============================================
		// NPC HANDLING
		// ============================================
		boolean in_house_distract = config.inHouseShowDistraction() && Houses.inLaviniaHouse(client.getLocalPlayer());
		boolean in_house = Houses.inHouse(client.getLocalPlayer());
		// Remember the house we are in so we are not routed back to it once emptied
		Houses.registerVisited(client.getLocalPlayer());
		boolean found_distracted = false;
		boolean owner_at_door = false;
		grace_meter.expire();
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
				if (config.debugGraceMeter()) {
					if (house.contains(npc.getWorldLocation())) {
						// Owner has crossed their own threshold, which is the moment you are
						// caught, so every stopwatch for this house stops here
						for (String line : grace_meter.settle(house.id, house.name)) {
							report(line);
						}
					} else if (dist < VarlamoreHouseThievingPlugin.DISTANCE_OWNER) {
						grace_meter.arm(house.id, GraceMeter.ANCHOR_DOOR);
					}
				}
				// Owner is back at their own door with us still inside
				if (house.door.isLocked()
						&& house.contains(client.getLocalPlayer().getWorldLocation())
						&& dist < VarlamoreHouseThievingPlugin.DISTANCE_OWNER) {
					owner_at_door = true;
					NextUpOverlayPanel.trackOwnerArrived();
					if (config.notifyOnReturnHome().isEnabled() && !NOTIFIED.contains(npc.getId())) {
						notify(config.notifyOnReturnHome(), "The owner is coming home! RUUUUUUNN!");
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
		House current = Houses.getHouse(client.getLocalPlayer());
		boolean warned = warned_house != -1 && current != null && current.id == warned_house
				&& System.currentTimeMillis() - warned_at < WARNING_TIMEOUT;
		if (!warned) {
			warned_house = -1;
		}
		owner_returning = owner_at_door || warned;
		// Walking and idling are pose animations, so an animation here means you are
		// actually working a chest
		int animation = client.getLocalPlayer().getAnimation();
		if (in_house && animation == LOOTING_ANIMATION) {
			last_looting = System.currentTimeMillis();
		}
		// Idle is skipped rather than recorded, otherwise an animation that stutters
		// back and forth to idle would report on every single roll
		if (in_house && animation != -1) {
			if (config.debugGraceMeter() && animation != last_animation) {
				report("animation while in house: " + animation
						+ (animation == LOOTING_ANIMATION ? " (looting)" : " (not looting)"));
			}
			last_animation = animation;
		}
		if (!owner_returning) {
			NextUpOverlayPanel.resetOwnerArrived();
		}
		// If we found a distracted citizen alert if needed
		if (found_distracted && !distraction_alerted) {
			// If player not in a house
			if (in_house_distract || !in_house) {
				if ((config.enableDistractedOverlay() || config.notifyOnDistracted().isEnabled())) {
					distraction_alerted = true;
					if (config.enableDistractedOverlay()) {
						NextUpOverlayPanel.trackDistraction();
					}
					// Cancel notification IF player in house and config says to
					if (config.notifyOnDistracted().isEnabled()
							&& (!Houses.inHouse(client.getLocalPlayer()) || config.inHouseDistractionAlerting())) {
						notify(config.notifyOnDistracted(), "A Wealthy citizen is being distracted!");
					}
				}
			}
		} else if (!found_distracted) {
			distraction_alerted = false;
		}
		// ============================================
		// Can't spot anything else check
		// ============================================
		boolean container_empty = containerEmpty();
		if (container_empty) {
			// This one is done, so you are looking around again rather than looting.
			// Waiting on the animation to lapse holds the highlights back for no
			// reason at exactly the point you want them
			last_looting = -1;
		}
		if (config.notifyOnEmptyContainer().isEnabled() && container_empty) {
			if (!done_stealing_notified) {
				done_stealing_notified = true;
				notify(config.notifyOnEmptyContainer(), "You can't spot anything else worth stealing");
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
		(config.enableDistractedOverlay() || config.notifyOnDistracted().isEnabled())
				// If player configured to track distraction in house OR player is not in a
				// house at all
				&& (in_house_distract || !in_house)
				// If threshold is configured and has been met
				&& config.notifyOnTimeSinceDistraction() > 0
				&& config.notifyOnTimeSinceDistraction() == time_since_last_distraction) {
			String second_or_seconds = config.notifyOnTimeSinceDistraction() == 1 ? " second" : " seconds";
			notify(config.notifyOnDistracted(),
					"It has been " + time_since_last_distraction + second_or_seconds + " since the last distraction");
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (event.getType() != ChatMessageType.GAMEMESSAGE) {
			return;
		}
		String message = event.getMessage();
		// Never react to our own output, it quotes the message that triggered it
		if (message.startsWith(CHAT_PREFIX)) {
			return;
		}
		String lower = message.toLowerCase();
		if (config.debugGraceMeter() && lower.contains("owner")) {
			// Logged raw, the live wording carries a colour tag ahead of the text
			report("game message: " + message);
		}
		// The message arrives with a colour tag prefix, so match on the text within
		if (!lower.contains(OWNER_WARNING)) {
			return;
		}
		House house = Houses.getHouse(client.getLocalPlayer());
		if (house == null) {
			return;
		}
		warned_at = System.currentTimeMillis();
		warned_house = house.id;
		NextUpOverlayPanel.trackOwnerArrived();
		if (config.debugGraceMeter()) {
			grace_meter.arm(house.id, GraceMeter.ANCHOR_CHAT);
		}
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed sound) {
		int sound_id = sound.getSoundId();
		if (sound_id == VarlamoreHouseThievingPlugin.STATUE_SOUND_EFFECT
				&& config.disableStatueSoundEffect()
				&& Houses.inHouse(client.getLocalPlayer())) {
			sound.consume();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		// Update whitelist when whitelist config changed
		if (event.getKey().equals("debugging_icon_size") ||
				event.getKey().equals("arrow_icon")) {
			reloadIcon();
		}
		if (event.getKey().equals("debug_grace_meter")) {
			if (config.debugGraceMeter()) {
				grace_meter.reset();
				report("Grace meter on. Rob as normal, escape, then watch the owner walk in.");
			} else if (grace_meter.hasSamples()) {
				report(grace_meter.report());
				int safe = grace_meter.safeGraceSeconds();
				if (safe >= 0) {
					report("Suggested Owner Entry Grace: " + safe + "s, the quickest arrival measured");
				}
			}
		}
	}

	@Provides
	VarlamoreHouseThievingConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(VarlamoreHouseThievingConfig.class);
	}
}
