package com.sololegends.runelite;

import java.awt.*;
import java.util.HashSet;

import com.google.inject.Inject;
import com.sololegends.panel.NextUpOverlayPanel;
import com.sololegends.runelite.data.Houses;
import com.sololegends.runelite.data.Houses.House;

import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.Notifier;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class VarlamoreHouseThievingOverlay extends Overlay {

	private final Client client;
	private final VarlamoreHouseThievingConfig config;
	private boolean tile_hint_active = false;
	private boolean npc_hint_active = false;
	private boolean bonus_check_notified = false;
	private final TooltipManager tooltip_manager;
	private HashSet<Integer> NOTIFIED = new HashSet<>();

	@Inject
	private Notifier notifier;

	private VarlamoreHouseThievingPlugin plugin;

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

	public static void renderEntity(Client client, Graphics2D graphics, Color color, NPC npc) {
		LocalPoint sw_tile = npc.getLocalLocation();
		NPCComposition npcComposition = npc.getTransformedComposition();
		if (sw_tile != null) {
			final int size = npcComposition.getSize();
			final LocalPoint centerLp = new LocalPoint(
					sw_tile.getX() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2,
					sw_tile.getY() + Perspective.LOCAL_TILE_SIZE * (size - 1) / 2);
			Polygon tilePoly = Perspective.getCanvasTileAreaPoly(client, centerLp, size);
			if (tilePoly == null) {
				return;
			}
			OverlayUtil.renderPolygon(graphics, tilePoly, color);
		}
	}

	private void renderEntities(Graphics2D graphics) {
		if (npc_hint_active) {
			client.clearHintArrow();
			npc_hint_active = false;
		}
		for (NPC npc : client.getNpcs()) {
			if (config.highlightHomeOwners() && (npc.getId() == VarlamoreHouseThievingPlugin.LAVINIA_ID
					|| npc.getId() == VarlamoreHouseThievingPlugin.CAIUS_ID
					|| npc.getId() == VarlamoreHouseThievingPlugin.VICTOR_ID)) {
				renderEntity(client, graphics, config.colorHomeOwners(), npc);
				// check position relative to the door
				House house = Houses.getHouse(npc.getId());
				if (house == null) {
					continue;
				}
				int dist = npc.getWorldLocation().distanceTo2D(house.door.getWorldLocation());
				if (config.notifyOnReturnHome()
						&& house.door.isLocked()
						&& house.contains(client.getLocalPlayer().getWorldLocation())
						&& dist < VarlamoreHouseThievingPlugin.DISTANCE_OWNER) {
					if (!NOTIFIED.contains(npc.getId())) {
						notifier.notify("The owner is coming home! RUUUUUUNN!");
						NOTIFIED.add(npc.getId());
					}
				} else {
					NOTIFIED.remove(npc.getId());
				}
			}
			if (npc.getName().equals(VarlamoreHouseThievingPlugin.WEALTHY_CITIZEN_NAME)) {
				// If they are interacting with child
				if (config.highlightDistractedCitizens() && npc.isInteracting()) {
					Actor a = npc.getInteracting();
					if (a == null || a.getCombatLevel() != 0) {
						continue;
					}
					// If player not in a house
					if (!Houses.inHouse(client.getLocalPlayer())) {
						client.setHintArrow(npc);
						npc_hint_active = true;
						if (config.notifyOnDistracted() && !NOTIFIED.contains(npc.getId())) {
							NextUpOverlayPanel.trackDistraction();
							notifier.notify("A Wealthy citizen is being distracted!");
							NOTIFIED.add(npc.getId());
						}
					}
					renderEntity(client, graphics, config.colorDistractedCitizens(), npc);
					continue;
				} else if (config.highlightWealthyCitizens()) {
					renderEntity(client, graphics, config.colorWealthyCitizens(), npc);
				}
				NOTIFIED.remove(npc.getId());
			}
		}
	}

	private void renderTileObjects(Graphics2D graphics) {
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		if (tile_hint_active) {
			client.clearHintArrow();
			tile_hint_active = false;
		}
		WorldPoint box_target = null;
		if (client.hasHintArrow() && client.getHintArrowType() == HintArrowType.COORDINATE) {
			box_target = client.getHintArrowPoint();
		}
		if (box_target == null) {
			bonus_check_notified = false;
		}
		int z = client.getPlane();
		for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
			for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
				Tile tile = tiles[z][x][y];

				if (tile == null) {
					continue;
				}
				WorldPoint tile_point = tile.getWorldLocation();
				if (tile_point.equals(box_target)) {
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
								if (config.highlightBonusChests()) {
									graphics.setColor(config.colorBonusChests());
									graphics.draw(obj.getConvexHull());
								}
								if (!bonus_check_notified && config.notifyOnBonusChest()) {
									notifier.notify("Bonus Loot opportunity!");
									bonus_check_notified = true;
								}
							}
						}
					}
				}
				int dist = client.getLocalPlayer().getLocalLocation()
						.distanceTo(tile.getLocalLocation());
				if (dist > VarlamoreHouseThievingPlugin.DISTANCE_DOOR_AWAY) {
					continue;
				}
				WallObject wo = tile.getWallObject();
				if (wo != null && wo.getId() == VarlamoreHouseThievingPlugin.LOCKED_DOOR_ID
						&& wo.getConvexHull() != null) {
					if (config.highlightLockedDoors()) {
						graphics.setColor(config.colorLockedDoors());
						graphics.draw(wo.getConvexHull());
					}
					// If door is not locked yet, this is first call
					if (!Houses.isLocked(wo.getWorldLocation())) {
						NextUpOverlayPanel.trackOwnerLeft();
					}
					// Register door as locked
					Houses.registerLocked(wo.getWorldLocation());
					// Only if not close
					if (!Houses.inHouse(client.getLocalPlayer()) && npc_hint_active == false
							&& dist > VarlamoreHouseThievingPlugin.DISTANCE_DOOR) {
						client.setHintArrow(tile.getLocalLocation());
						tile_hint_active = true;
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
	}

	private void renderInventory(Graphics2D graphics) {
		MenuEntry[] menuEntries = client.getMenuEntries();

		if (menuEntries.length < 1) {
			return;
		}

		MenuEntry menuEntry = menuEntries[menuEntries.length - 1];
		int widgetId = menuEntry.getParam1();

		if (widgetId != ComponentID.INVENTORY_CONTAINER) {
			return;
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		Item item = inventory.getItem(menuEntry.getParam0());
		if (item == null || item.getId() != 29332) {
			return;
		}
		tooltip_manager.add(new Tooltip("Value: " + item.getQuantity() * VarlamoreHouseThievingPlugin.VALUABLE_VALUE));
		return;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return null;
		}
		// Optimization to not run through renderings when not in the varlamore city
		if (plugin.playerInMarket()) {
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
