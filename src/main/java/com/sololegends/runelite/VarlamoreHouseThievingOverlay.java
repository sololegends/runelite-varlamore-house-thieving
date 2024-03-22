package com.sololegends.runelite;

import java.awt.*;
import java.util.HashSet;

import com.google.inject.Inject;

import net.runelite.api.*;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.LocalPoint;
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
	private final TooltipManager tooltip_manager;
	private HashSet<Integer> NOTIFIED = new HashSet<>();

	@Inject
	private Notifier notifier;

	@Inject
	private VarlamoreHouseThievingOverlay(Client client, VarlamoreHouseThievingPlugin plugin,
			VarlamoreHouseThievingConfig config, TooltipManager tooltip_manager) {
		super(plugin);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.config = config;
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
				// ! I don't like this maybe rework later
				// if (npc.getLocalLocation()
				// .distanceTo(client.getLocalPlayer().getLocalLocation()) <
				// VarlamoreHouseThievingPlugin.DISTANCE_OWNER) {
				// client.setHintArrow(npc);
				// npc_hint_active = true;
				// }
			}
			if (npc.getName().equals(VarlamoreHouseThievingPlugin.WEALTHY_CITIZEN_NAME)) {
				// If they are interacting with child
				if (config.highlightDistractedCitizens() && npc.isInteracting()) {
					Actor a = npc.getInteracting();
					if (a == null || a.getCombatLevel() != 0) {
						continue;
					}
					client.setHintArrow(npc);
					npc_hint_active = true;
					if (config.notifyOnDistracted() && !NOTIFIED.contains(npc.getId())) {
						notifier.notify("A Wealthy citizen is being distracted!");
						NOTIFIED.add(npc.getId());
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
		int z = client.getPlane();
		for (int x = 0; x < Constants.SCENE_SIZE; ++x) {
			for (int y = 0; y < Constants.SCENE_SIZE; ++y) {
				Tile tile = tiles[z][x][y];

				if (tile == null) {
					continue;
				}
				WallObject wo = tile.getWallObject();
				if (wo != null && wo.getId() == VarlamoreHouseThievingPlugin.LOCKED_DOOR_ID
						&& wo.getConvexHull() != null) {
					if (config.highlightLockedDoors()) {
						graphics.setColor(config.colorLockedDoors());
						graphics.draw(wo.getConvexHull());
					}
					// Only if not close
					if (npc_hint_active == false && client.getLocalPlayer().getLocalLocation()
							.distanceTo(tile.getLocalLocation()) > VarlamoreHouseThievingPlugin.DISTANCE_DOOR) {
						client.setHintArrow(tile.getLocalLocation());
						tile_hint_active = true;
					}
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

		int index = menuEntry.getParam0();

		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		Item item = inventory.getItem(index);
		if (item == null || item.getId() != 29332) {
			return;
		}
		tooltip_manager.add(new Tooltip("Value: " + item.getQuantity() * VarlamoreHouseThievingPlugin.VALUABLE_VALUE));
		return;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		renderEntities(graphics);
		renderTileObjects(graphics);
		renderInventory(graphics);
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
