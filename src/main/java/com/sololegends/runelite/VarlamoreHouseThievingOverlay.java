package com.sololegends.runelite;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;

import com.google.inject.Inject;
import com.sololegends.panel.NextUpOverlayPanel;
import com.sololegends.runelite.data.Houses;

import net.runelite.api.*;
import net.runelite.api.Menu;
import net.runelite.api.Point;
import net.runelite.api.coords.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.Notifier;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class VarlamoreHouseThievingOverlay extends Overlay {

	private final Client client;
	private final VarlamoreHouseThievingConfig config;
	private WorldPoint tile_hint = null;
	private boolean bonus_check_notified = false;
	private final TooltipManager tooltip_manager;

	private final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##");
	private static final long NOTIFY_TIMEOUT = 30_000;
	private long last_notify = -1;
	@Inject
	private Notifier notifier;

	private VarlamoreHouseThievingPlugin plugin;

	private final void notify(String message) {
		if (System.currentTimeMillis() - last_notify > NOTIFY_TIMEOUT) {
			notifier.notify(message);
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
		if (sw_tile != null) {
			Point icon_loc = npc.getCanvasTextLocation(graphics, "", npc.getLogicalHeight() + 25);
			OverlayUtil.renderImageLocation(graphics,
					new Point(icon_loc.getX() - (icon.getWidth() / 2), icon_loc.getY() - icon.getHeight()),
					icon);
		}
	}

	public static void renderEntity(Client client, Graphics2D graphics, Color color, NPC npc) {
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
			OverlayUtil.renderPolygon(graphics, tilePoly, color);
		}
	}

	private void renderEntities(Graphics2D graphics) {
		boolean found_citizen = false;
		for (NPC npc : plugin.getCachedNPCs()) {
			if (npc == null) {
				continue;
			}
			if (config.highlightHomeOwners() && (npc.getId() == VarlamoreHouseThievingPlugin.LAVINIA_ID
					|| npc.getId() == VarlamoreHouseThievingPlugin.CAIUS_ID
					|| npc.getId() == VarlamoreHouseThievingPlugin.VICTOR_ID)) {
				renderEntity(client, graphics, config.colorHomeOwners(), npc);
			}
			if (npc.getName().equals(VarlamoreHouseThievingPlugin.WEALTHY_CITIZEN_NAME)) {
				found_citizen = true;
				// If they are interacting with child
				if (config.highlightDistractedCitizens() && npc.isInteracting()) {
					Actor a = npc.getInteracting();
					if (a == null || a.getCombatLevel() != 0) {
						continue;
					}
					renderEntity(client, graphics, config.colorDistractedCitizens(), npc);

					// Render the Icon If player not in a house OR has flashing enabled inside the
					// house
					if (plugin.flick() && (!Houses.inHouse(client.getLocalPlayer()) || config.inHouseDistractionFlashing())) {
						renderIcon(client, graphics, plugin.icon(), npc);
					}

				} else if (config.highlightWealthyCitizens()) {
					renderEntity(client, graphics, config.colorWealthyCitizens(), npc);
				}
			}
		}

		if (!found_citizen) {
			NextUpOverlayPanel.resetDistraction();
		}
	}

	private void renderTileObjects(Graphics2D graphics) {
		Scene scene = plugin.getScene();
		Tile[][][] tiles = scene.getTiles();
		if (tile_hint != null) {
			// Clear only if the door is the target
			if (client.hasHintArrow() && client.getHintArrowType() == HintArrowType.COORDINATE
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
		if (box_target == null) {
			bonus_check_notified = false;
		}
		int z = plugin.getPlane();
		boolean has_locked_door = false;
		for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
			for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
				Tile tile = tiles[z][x][y];

				if (tile == null) {
					continue;
				}
				WorldPoint tile_point = tile.getWorldLocation();
				if (config.highlightBonusChests() || config.notifyOnBonusChest() || config.highlightAllChests()) {
					// Box targeted!
					// Get and highlight object
					GameObject[] objs = tile.getGameObjects();
					if (objs != null) {
						for (GameObject obj : objs) {
							if (obj == null) {
								continue;
							}
							// 52008 Box
							// 52010 Wardrobe
							// 52011 Jewellery Box
							if ((obj.getId() == 52008 || obj.getId() == 52010 || obj.getId() == 52011)
									&& obj.getConvexHull() != null) {
								if (config.highlightAllChests()) {
									graphics.setColor(config.colorAllChests());
									graphics.draw(obj.getConvexHull());
								}
								// If this one is a bonus chest
								if (box_target != null && tile_point.equals(box_target)) {
									if (config.highlightBonusChests()) {
										graphics.setColor(config.colorBonusChests());
										graphics.draw(obj.getConvexHull());
									}
									if (!bonus_check_notified && config.notifyOnBonusChest()) {
										notify("Bonus Loot opportunity!");
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
					if (config.highlightLockedDoors()) {
						graphics.setColor(config.colorLockedDoors());
						graphics.draw(wo.getConvexHull());
					}
					// If door is not locked yet, this is first call
					if (!Houses.isLocked(wo.getWorldLocation()) && config.enableReturnHomeOverlay()) {
						NextUpOverlayPanel.trackOwnerLeft();
					}
					// Register door as locked
					Houses.registerLocked(wo.getWorldLocation());
					// Only if not close
					if (!Houses.inHouse(client.getLocalPlayer()) && !client.hasHintArrow()
							&& dist > VarlamoreHouseThievingPlugin.DISTANCE_DOOR) {
						client.setHintArrow(tile.getLocalLocation());
						tile_hint = tile.getWorldLocation();
					}
				}
				if (wo != null && wo.getId() == VarlamoreHouseThievingPlugin.UNLOCKED_DOOR_ID
						&& wo.getConvexHull() != null) {
					Houses.registerUnlocked(wo.getWorldLocation());
				}
				if (config.highlightEscapeWindows() && wo != null && wo.getId() == VarlamoreHouseThievingPlugin.ESCAPE_WINDOW_ID
						&& wo.getConvexHull() != null) {
					graphics.setColor(config.colorEscapeWindows());
					graphics.draw(wo.getConvexHull());
				}
			}
		}
		if (!has_locked_door) {
			NextUpOverlayPanel.resetOwnerLeft();
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
