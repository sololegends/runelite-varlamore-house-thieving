package com.sololegends.panel;

import java.awt.*;

import javax.inject.Inject;

import com.sololegends.runelite.VarlamoreHouseThievingPlugin;
import com.sololegends.runelite.data.Houses;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.LineComponent.LineComponentBuilder;

public class NextUpOverlayPanel extends OverlayPanel {

  public static final int WARN_THRESHOLD_DISTRACTED = 70;
  public static final int NOW_THRESHOLD_DISTRACTED = 90;
  public static final int WARN_THRESHOLD_OWNER_RET = 150;
  public static final int NOW_THRESHOLD_OWNER_RET = 160;

  // system time in milliseconds
  private static long last_distraction = -1;
  private static long owner_left = -1;
  private VarlamoreHouseThievingPlugin plugin;

  @Inject
  private Client client;

  @Inject
  private NextUpOverlayPanel(VarlamoreHouseThievingPlugin plugin) {
    super(plugin);
    this.plugin = plugin;
    setPosition(OverlayPosition.TOP_CENTER);
    setPriority(Overlay.PRIORITY_LOW);
  }

  public static void trackDistraction() {
    last_distraction = System.currentTimeMillis();
  }

  public static void trackOwnerLeft() {
    owner_left = System.currentTimeMillis();
  }

  public static long now() {
    return System.currentTimeMillis();
  }

  public static long sinceOwnerLeft() {
    return (System.currentTimeMillis() - owner_left) / 1000;
  }

  public static long sinceDistraction() {
    return (System.currentTimeMillis() - last_distraction) / 1000;
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    // REset counters on not in market
    if (!plugin.playerInMarket()) {
      owner_left = -1;
      last_distraction = -1;
    }
    if (Houses.inHouse(client.getLocalPlayer()) && owner_left != -1) {
      // If player in a house
      long since = sinceOwnerLeft();
      LineComponentBuilder builder = LineComponent.builder()
          .left("Time since Owner left:")
          .right(since + "s");

      if (since > NOW_THRESHOLD_OWNER_RET) {
        builder.rightColor(Color.RED);
      } else if (since > WARN_THRESHOLD_OWNER_RET) {
        builder.rightColor(Color.ORANGE);
      }

      panelComponent.getChildren().add(builder.build());
    } else if (plugin.playerInMarket() && last_distraction != -1) {
      // If player in the market and not in the house
      long since = sinceDistraction();
      LineComponentBuilder builder = LineComponent.builder()
          .left("Time since distraction:")
          .right(since + "s");

      if (since > NOW_THRESHOLD_DISTRACTED) {
        builder.rightColor(Color.RED);
      } else if (since > WARN_THRESHOLD_DISTRACTED) {
        builder.rightColor(Color.ORANGE);
      }

      panelComponent.getChildren().add(builder.build());
    }
    return super.render(graphics);
  }
}
