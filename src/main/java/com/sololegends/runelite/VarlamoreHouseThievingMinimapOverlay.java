package com.sololegends.runelite;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import com.google.inject.Inject;
import com.sololegends.panel.NextUpOverlayPanel;
import com.sololegends.runelite.data.Houses;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class VarlamoreHouseThievingMinimapOverlay extends Overlay {

	private final Client client;
	private final VarlamoreHouseThievingConfig config;
	private VarlamoreHouseThievingPlugin plugin;

	@Inject
	private VarlamoreHouseThievingMinimapOverlay(Client client, VarlamoreHouseThievingPlugin plugin,
			VarlamoreHouseThievingConfig config, TooltipManager tooltip_manager) {
		super(plugin);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.client = client;
		this.config = config;
		this.plugin = plugin;
	}

	public static void renderMinimapIcon(Client client, Graphics2D graphics, BufferedImage icon, NPC npc) {
		LocalPoint sw_tile = npc.getLocalLocation();
		if (sw_tile != null) {
			Point minimap_loc = npc.getMinimapLocation();
			if (minimap_loc == null) {
				return;
			}
			OverlayUtil.renderImageLocation(graphics,
					new Point(minimap_loc.getX() - (icon.getWidth() / 2), minimap_loc.getY() - icon.getHeight()),
					icon);

		}
	}

	private void renderEntities(Graphics2D graphics) {
		boolean found_citizen = false;
		for (NPC npc : plugin.getCachedNPCs()) {
			if (npc == null) {
				continue;
			}
			if (npc.getName().equals(VarlamoreHouseThievingPlugin.WEALTHY_CITIZEN_NAME)) {
				found_citizen = true;
				// If they are interacting with child
				if (config.highlightDistractedCitizens() && npc.isInteracting()) {
					Actor a = npc.getInteracting();
					if (a == null || a.getCombatLevel() != 0) {
						continue;
					}

					// Render the Icon
					if (plugin.flick() && (!Houses.inHouse(client.getLocalPlayer()) || config.inHouseDistractionFlashing())) {
						renderMinimapIcon(client, graphics, plugin.icon(), npc);
					}
				}
			}
		}

		if (!found_citizen) {
			NextUpOverlayPanel.resetDistraction();
		}
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
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

}
