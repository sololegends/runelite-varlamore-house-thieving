package com.sololegends.runelite;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.List;

import com.google.inject.Inject;
import com.sololegends.panel.NextUpOverlayPanel;
import com.sololegends.runelite.data.Houses;
import com.sololegends.runelite.data.Houses.House;
import com.sololegends.runelite.path.Pathfinder;
import com.sololegends.runelite.render.Highlighter;

import net.runelite.api.*;
import net.runelite.api.Menu;
import net.runelite.api.Point;
import net.runelite.api.coords.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class VarlamoreHouseThievingOverlay extends Overlay {

	private final Client client;
	private final VarlamoreHouseThievingConfig config;
	private WorldPoint tile_hint = null;
	private boolean bonus_check_notified = false;
	private WorldPoint bonus_target = null;
	private final TooltipManager tooltip_manager;
	private final Pathfinder house_path = new Pathfinder();
	private final Pathfinder bonus_path = new Pathfinder();

	private static final int PATH_REACH_TOLERANCE = 2;
	private static final int ESCAPE_TIMER_HEIGHT = 120;
	private static final int BONUS_TIMER_HEIGHT = 120;

	private final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##");
	private static final long NOTIFY_TIMEOUT = 5_000;
	private long last_notify = -1;
	@Inject
	private Notifier notifier;

	private VarlamoreHouseThievingPlugin plugin;

	private final void notify(Notification notification, String message) {
		if (System.currentTimeMillis() - last_notify > NOTIFY_TIMEOUT) {
			notifier.notify(notification, message);
			last_notify = System.currentTimeMillis();
		}
	}

	@Inject
	private VarlamoreHouseThievingOverlay(Client client, VarlamoreHouseThievingPlugin plugin,
			VarlamoreHouseThievingConfig config, TooltipManager tooltip_manager) {
		super(plugin);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		this.tooltip_manager = tooltip_manager;
	}

	public static void renderIcon(Client client, Graphics2D graphics, BufferedImage icon, NPC npc) {
		LocalPoint sw_tile = npc.getLocalLocation();
		if (sw_tile != null && icon != null) {
			Point icon_loc = npc.getCanvasTextLocation(graphics, "", npc.getLogicalHeight() + 25);
			OverlayUtil.renderImageLocation(graphics,
					new Point(icon_loc.getX() - (icon.getWidth() / 2), icon_loc.getY() - icon.getHeight()),
					icon);
		}
	}

	public static void renderEntity(Client client, Graphics2D graphics, Color color, NPC npc,
			int thickness, int fill_opacity) {
		LocalPoint sw_tile = npc.getLocalLocation();
		NPCComposition npcComposition = npc.getTransformedComposition();
		if (sw_tile != null) {
			final int size = npcComposition.getSize();
			final LocalPoint centerLp = new LocalPoint(
					sw_tile.getX() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2,
					sw_tile.getY() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2,
					client.getTopLevelWorldView());
			Polygon tilePoly = Perspective.getCanvasTileAreaPoly(client, centerLp, size);
			if (tilePoly == null) {
				return;
			}
			Highlighter.render(graphics, tilePoly, color, thickness, fill_opacity);
		}
	}

	private void renderEntities(Graphics2D graphics) {
		boolean found_citizen = false;
		final int thickness = config.outlineThickness();
		final int fill = config.fillOpacity();
		final boolean quiet = plugin.quietWhileLooting();
		for (NPC npc : plugin.getCachedNPCs()) {
			if (npc == null) {
				continue;
			}
			if (!quiet && config.highlightHomeOwners() && (npc.getId() == VarlamoreHouseThievingPlugin.LAVINIA_ID
					|| npc.getId() == VarlamoreHouseThievingPlugin.CAIUS_ID
					|| npc.getId() == VarlamoreHouseThievingPlugin.VICTOR_ID)) {
				renderEntity(client, graphics, config.colorHomeOwners(), npc, thickness, fill);
			}
			if (npc.getName().equals(VarlamoreHouseThievingPlugin.WEALTHY_CITIZEN_NAME)) {
				found_citizen = true;
				if (quiet) {
					continue;
				}
				// If they are interacting with child
				if (config.highlightDistractedCitizens() && npc.isInteracting()) {
					Actor a = npc.getInteracting();
					if (a == null || a.getCombatLevel() != 0) {
						continue;
					}
					renderEntity(client, graphics, config.colorDistractedCitizens(), npc, thickness, fill);

					// Render the Icon If player not in a house OR has flashing enabled inside the
					// house
					if (plugin.flick() && (!Houses.inHouse(client.getLocalPlayer()) || config.inHouseDistractionAlerting())) {
						renderIcon(client, graphics, plugin.icon(), npc);
					}

				} else if (config.highlightWealthyCitizens()) {
					renderEntity(client, graphics, config.colorWealthyCitizens(), npc, thickness, fill);
				}
			}
		}

		if (!found_citizen) {
			NextUpOverlayPanel.resetDistraction();
		}
	}

	private boolean clientActuallyHasHintArrow() {
		return client.hasHintArrow()
				&& (client.getHintArrowType() == HintArrowType.COORDINATE
						&& !(client.getHintArrowPoint().getX() == 0
								&& client.getHintArrowPoint().getY() == 0
								&& client.getHintArrowPoint().getPlane() == 0))
				|| client.getHintArrowType() == HintArrowType.NPC
				|| client.getHintArrowType() == HintArrowType.PLAYER;
	}

	private void renderTileObjects(Graphics2D graphics) {
		Scene scene = plugin.getScene();
		Tile[][][] tiles = scene.getTiles();
		if (tile_hint != null) {
			// Clear only if the door is the target
			if (clientActuallyHasHintArrow() && client.getHintArrowType() == HintArrowType.COORDINATE
					&& client.getHintArrowPoint().getX() == tile_hint.getX()
					&& client.getHintArrowPoint().getY() == tile_hint.getY()) {
				client.clearHintArrow();
			}
			tile_hint = null;
		}
		WorldPoint box_target = null;
		if (client.getHintArrowType() == HintArrowType.COORDINATE) {
			box_target = client.getHintArrowPoint();
		}
		if (box_target == null || (box_target.getX() == 0 && box_target.getY() == 0 && box_target.getPlane() == 0)) {
			bonus_check_notified = false;
			client.clearHintArrow();
		}
		int z = plugin.getPlane();
		boolean has_locked_door = false;
		final int thickness = config.outlineThickness();
		final int fill = config.fillOpacity();
		// Re-established below only while the chest is actually in the scene
		bonus_target = null;
		final boolean in_house = Houses.inHouse(client.getLocalPlayer());
		// A bonus chest is worth breaking the quiet for, nothing else in here is
		final boolean quiet = plugin.quietWhileLooting();
		for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
			for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
				Tile tile = tiles[z][x][y];

				if (tile == null) {
					continue;
				}
				WorldPoint tile_point = tile.getWorldLocation();
				if (config.highlightBonusChests() || config.notifyOnBonusChest().isEnabled() || config.highlightAllChests()
						|| config.pathToBonusChest() || config.bonusChestTimer()) {
					// Box targeted!
					// Get and highlight object
					GameObject[] objs = tile.getGameObjects();
					if (objs != null) {
						for (GameObject obj : objs) {
							if (obj == null) {
								continue;
							}
							if (VarlamoreHouseThievingPlugin.isContainer(obj.getId())
									&& obj.getConvexHull() != null) {
								if (!quiet && config.highlightAllChests()) {
									Highlighter.render(graphics, obj.getConvexHull(), config.colorAllChests(), thickness, fill);
								}
								// If this one is a bonus chest
								if (box_target != null && tile_point.equals(box_target)) {
									bonus_target = tile_point;
									if (config.highlightBonusChests()) {
										Highlighter.render(graphics, obj.getConvexHull(), config.colorBonusChests(), thickness, fill);
									}
									if (config.bonusChestTimer()) {
										renderBonusTimer(graphics, obj);
									}
									if (!bonus_check_notified) {
										if (config.notifyOnBonusChest().isEnabled()) {
											notify(config.notifyOnBonusChest(), "Bonus Loot opportunity!");
										}
										NextUpOverlayPanel.trackBonusChest();
										bonus_check_notified = true;
									}
								}
							}
						}
					}
				}

				// Check if door is out of update distance now
				int dist = client.getLocalPlayer().getLocalLocation()
						.distanceTo(tile.getLocalLocation());
				if (dist > VarlamoreHouseThievingPlugin.DISTANCE_DOOR_AWAY) {
					continue;
				}
				// Look for doors that need highlighting
				WallObject wo = tile.getWallObject();
				if (wo != null && wo.getId() == VarlamoreHouseThievingPlugin.LOCKED_DOOR_ID
						&& wo.getConvexHull() != null) {
					has_locked_door = true;
					// Once you are inside, the door you came through is just noise
					boolean own_door = in_house
							&& Houses.insideHouseOf(wo.getWorldLocation(), client.getLocalPlayer().getWorldLocation());
					if (!quiet && config.highlightLockedDoors() && !(config.hideDoorInHouse() && own_door)) {
						Highlighter.render(graphics, wo.getConvexHull(), config.colorLockedDoors(), thickness, fill);
					}
					// If door is not locked yet, this is first call
					if (!Houses.isLocked(wo.getWorldLocation()) && config.enableReturnHomeOverlay()) {
						NextUpOverlayPanel.trackOwnerLeft();
					}
					// Register door as locked
					Houses.registerLocked(wo.getWorldLocation());
					// Only if not close
					if (!in_house && !clientActuallyHasHintArrow()
							&& dist > VarlamoreHouseThievingPlugin.DISTANCE_DOOR) {
						client.setHintArrow(tile.getLocalLocation());
						tile_hint = tile.getWorldLocation();
					}
				}
				if (wo != null && wo.getId() == VarlamoreHouseThievingPlugin.UNLOCKED_DOOR_ID
						&& wo.getConvexHull() != null) {
					Houses.registerUnlocked(wo.getWorldLocation());
				}
				if (wo != null && wo.getId() == VarlamoreHouseThievingPlugin.ESCAPE_WINDOW_ID
						&& wo.getConvexHull() != null) {
					if (!quiet && config.highlightEscapeWindows()) {
						Highlighter.render(graphics, wo.getConvexHull(), config.colorEscapeWindows(), thickness, fill);
					}
					// Fleeing, you are looking at the window rather than the panel, so put
					// the countdown where your eyes already are
					if (config.enableOwnerCountdown()) {
						renderEscapeCountdown(graphics, wo);
					}
				}
			}
		}
		if (!has_locked_door) {
			NextUpOverlayPanel.resetOwnerLeft();
		}
		plugin.trackBonusChest(bonus_target != null);
	}

	private void renderEscapeCountdown(Graphics2D graphics, WallObject window) {
		if (!NextUpOverlayPanel.ownerArrived()) {
			return;
		}
		long remaining = config.ownerEntryGrace() - NextUpOverlayPanel.sinceOwnerArrived();
		String text = remaining > 0 ? remaining + "s" : "GO!";
		Color color = Color.YELLOW;
		if (remaining <= 0) {
			color = Color.RED;
		} else if (remaining <= NextUpOverlayPanel.WARN_THRESHOLD_OWNER_ARRIVED) {
			color = Color.ORANGE;
		}
		Point location = window.getCanvasTextLocation(graphics, text, ESCAPE_TIMER_HEIGHT);
		if (location != null) {
			OverlayUtil.renderTextLocation(graphics, location, text, color);
		}
	}

	/**
	 * Counts the configured window down over the chest. Once it overruns it keeps
	 * counting up instead of going negative, so a window set too short shows as
	 * plainly wrong rather than quietly lying.
	 */
	private void renderBonusTimer(Graphics2D graphics, GameObject obj) {
		long remaining = plugin.bonusRemaining();
		String text = remaining >= 0 ? remaining + "s" : "+" + (-remaining) + "s";
		Color color = config.colorBonusChests();
		// Proportional, so the warning still means something whether the window is
		// set to the few seconds it seems to run to or to something much longer
		long warn = Math.max(1, config.bonusChestWindow() / 3);
		if (remaining < 0) {
			color = Color.RED;
		} else if (remaining <= warn) {
			color = Color.ORANGE;
		}
		Point location = obj.getCanvasTextLocation(graphics, text, BONUS_TIMER_HEIGHT);
		if (location != null) {
			OverlayUtil.renderTextLocation(graphics, location, text, color);
		}
	}

	/**
	 * Paths are mutually exclusive by design, the bonus chest one only applies
	 * inside a house and the next house one only applies outside of one.
	 */
	private void renderPaths(Graphics2D graphics) {
		Player player = client.getLocalPlayer();
		if (player == null) {
			return;
		}
		if (config.pathToBonusChest() && bonus_target != null && Houses.inHouse(player)) {
			house_path.reset();
			NextUpOverlayPanel.resetNextHouse();
			renderPath(graphics, bonus_path.path(client, bonus_target), config.colorPath(), false);
			return;
		}
		bonus_path.reset();
		final boolean in_house = Houses.inHouse(player);
		// Inside with nothing to run from, you are still working this house
		if (!config.pathToNextHouse() || (in_house && !plugin.ownerReturning())) {
			house_path.reset();
			NextUpOverlayPanel.resetNextHouse();
			return;
		}
		WorldPoint from = player.getWorldLocation();
		House next = Houses.getNearestAvailable(from);
		WorldPoint target = next == null ? null : next.door.getWorldLocation();
		String heading = "Next house:";
		String label = next == null ? null : next.name;
		Color color = config.colorPath();
		boolean dashed = false;
		if (target == null) {
			// Nothing confirmed open, so hold between whatever is still in play
			// rather than leaving you standing in the doorway wondering
			List<House> candidates = Houses.getCandidates(from);
			target = config.stageBetweenHouses() ? Houses.getStagingPoint(candidates) : null;
			// With one house left there is nothing to hold between, it is just a
			// walk to the only door still worth trying
			heading = candidates.size() > 1 ? "Hold between:" : "Try:";
			label = names(candidates);
			color = config.colorPathStaging();
			dashed = true;
		}
		if (target == null) {
			house_path.reset();
			NextUpOverlayPanel.resetNextHouse();
			return;
		}
		NextUpOverlayPanel.trackNextHouse(heading, label, !dashed);

		List<WorldPoint> path = house_path.path(client, target);
		// Being inside the house bounds does not mean you are still indoors, they run
		// well past the building. The route settles it. Shut in with a locked door
		// there is no walkable way out at all, so the search can only hand back a stub
		// wandering the room, and an open door would route you into the owner stood at
		// it. Either way the line is worthless until you are out of the window, at
		// which point the target becomes genuinely reachable
		House current = Houses.getHouse(player);
		if (current != null
				&& (!reaches(path, target) || usesDoor(path, current.door.getWorldLocation()))) {
			return;
		}
		renderPath(graphics, path, color, dashed);
	}

	/** Whether the route actually arrives, rather than dead ending on the way */
	private static boolean reaches(List<WorldPoint> path, WorldPoint target) {
		if (path.isEmpty()) {
			return false;
		}
		// Doors and chests are not walkable themselves, so landing beside one counts
		return path.get(path.size() - 1).distanceTo2D(target) <= PATH_REACH_TOLERANCE;
	}

	private static boolean usesDoor(List<WorldPoint> path, WorldPoint door) {
		for (WorldPoint point : path) {
			if (point.getX() == door.getX() && point.getY() == door.getY()) {
				return true;
			}
		}
		return false;
	}

	private static String names(List<House> houses) {
		StringBuilder names = new StringBuilder();
		for (House house : houses) {
			if (names.length() > 0) {
				names.append(" & ");
			}
			names.append(house.name);
		}
		return names.toString();
	}

	private void renderPath(Graphics2D graphics, List<WorldPoint> path, Color color, boolean dashed) {
		if (path == null || path.size() < 2) {
			return;
		}
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null) {
			return;
		}
		final Stroke original = graphics.getStroke();
		final float width = Math.max(Highlighter.MIN_THICKNESS,
				Math.min(Highlighter.MAX_THICKNESS, config.pathWidth()));
		graphics.setColor(color);
		// Dashes mark a route we are guessing at rather than one we have confirmed
		graphics.setStroke(dashed
				? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
						new float[] { width * 3f, width * 3f }, 0f)
				: new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Point previous = null;
		for (WorldPoint point : path) {
			LocalPoint local = LocalPoint.fromWorld(wv, point);
			// Off screen tiles simply break the line rather than skewing it
			Point canvas = local == null ? null : Perspective.localToCanvas(client, local, wv.getPlane());
			if (previous != null && canvas != null) {
				graphics.drawLine(previous.getX(), previous.getY(), canvas.getX(), canvas.getY());
			}
			previous = canvas;
		}
		graphics.setStroke(original);
		// The holding spot is a bare patch of market with nothing to aim at, so mark
		// where the line lands. A confirmed route ends at a door you can already see
		if (dashed) {
			LocalPoint end = LocalPoint.fromWorld(wv, path.get(path.size() - 1));
			if (end != null) {
				Highlighter.render(graphics, Perspective.getCanvasTilePoly(client, end), color,
						config.outlineThickness(), config.fillOpacity());
			}
		}
	}

	private String formatMoney(long total) {
		if (total >= 1000000000) {
			return MONEY_FORMAT.format(total / 1000000000D) + " B";
		} else if (total >= 1000000) {
			return MONEY_FORMAT.format(total / 1000000D) + " M";
		} else if (total >= 1000) {
			return MONEY_FORMAT.format(total / 1000D) + " K";
		}
		return total + "";
	}

	private void renderInventory(Graphics2D graphics) {
		Menu menu = client.getMenu();
		MenuEntry[] menuEntries = menu.getMenuEntries();

		if (menuEntries.length < 1) {
			return;
		}

		MenuEntry menuEntry = menuEntries[menuEntries.length - 1];
		int widgetId = menuEntry.getParam1();

		if (widgetId != InterfaceID.INVENTORY << 16) {
			return;
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		Item item = inventory.getItem(menuEntry.getParam0());
		if (item == null || item.getId() != 29332) {
			return;
		}
		int multi = VarlamoreHouseThievingPlugin.VALUABLE_VALUE;
		if (client.getVarpValue(VarPlayerID.COLOSSEUM_GLORY) >= 8000) {
			multi = VarlamoreHouseThievingPlugin.VALUABLE_GLORY_VALUE;
		}
		tooltip_manager.add(new Tooltip("Value: " + formatMoney(item.getQuantity() * multi)));
		return;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return null;
		}
		// Optimization to not run through renderings when not in the varlamore city
		if (plugin.playerInActivity()) {
			try {
				renderEntities(graphics);
				renderTileObjects(graphics);
				renderPaths(graphics);
				renderInventory(graphics);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public Direction getNearestDirection(int angle) {
		int round = angle >>> 9;
		int up = angle & 256;
		if (up != 0) {
			// round up
			++round;
		}
		switch (round & 3) {
			case 0:
				return Direction.SOUTH;
			case 1:
				return Direction.WEST;
			case 2:
				return Direction.NORTH;
			case 3:
				return Direction.EAST;
			default:
				throw new IllegalStateException();
		}
	}
}
