package com.sololegends.panel;

import java.awt.*;

import javax.inject.Inject;

import com.sololegends.runelite.VarlamoreHouseThievingConfig;
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
  public static final int WARN_THRESHOLD_BONUS_CHEST = 30;
  public static final int NOW_THRESHOLD_BONUS_CHEST = 60;

  // system time in milliseconds
  private static long last_distraction = -1;
  private static long owner_left = -1;
  private static long bonus_chest = -1;
  private VarlamoreHouseThievingPlugin plugin;
  private VarlamoreHouseThievingConfig config;

  @Inject
  private Client client;

  @Inject
  private NextUpOverlayPanel(VarlamoreHouseThievingPlugin plugin, VarlamoreHouseThievingConfig config) {
    super(plugin);
    this.plugin = plugin;
    this.config = config;
    setPosition(OverlayPosition.TOP_CENTER);
    setPriority(Overlay.PRIORITY_LOW);
  }

  public static void trackDistraction() {
    last_distraction = System.currentTimeMillis();
  }

  public static void resetDistraction() {
    last_distraction = -1;
  }

  public static void trackOwnerLeft() {
    owner_left = System.currentTimeMillis();
  }

  public static void resetOwnerLeft() {
    owner_left = -1;
  }

  public static void trackBonusChest() {
    bonus_chest = System.currentTimeMillis();
  }

  public static void resetBonusChest() {
    bonus_chest = -1;
  }

  public static long now() {
    return System.currentTimeMillis();
  }

  public static long sinceOwnerLeft() {
    return (System.currentTimeMillis() - owner_left) / 1000;
  }

  public static long sinceBonusChest() {
    return (System.currentTimeMillis() - bonus_chest) / 1000;
  }

  public static long sinceDistraction() {
    return (System.currentTimeMillis() - last_distraction) / 1000;
  }

  // 98, 132, 176
  @Override
  public Dimension render(Graphics2D graphics) {
    boolean in_house = Houses.inHouse(client.getLocalPlayer());
    // Reset counters on not in market
    if (!plugin.playerInActivity()) {
      resetOwnerLeft();
      resetDistraction();
      resetBonusChest();
    }
    // Reset owner counter if feature turned off
    if (!config.enableReturnHomeOverlay()) {
      resetOwnerLeft();
    }
    // Reset distraction counter if feature turned off
    if (!config.enableDistractedOverlay()) {
      resetDistraction();
    }
    // Reset bonus chest counter if feature turned off, or player not in house
    if (!config.enableBonusChestOverlay() || !in_house) {
      resetBonusChest();
    }

    if (plugin.playerInActivity()
        && ((config.inHouseShowDistraction() && Houses.inLaviniaHouse(client.getLocalPlayer()))
            || !in_house)
        && last_distraction != -1) {
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

    // Return now if player NOT in a house
    // Everything after here required being in a house
    if (!in_house) {
      return super.render(graphics);
    }
    if (owner_left != -1) {
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
    }
    if (bonus_chest != -1) {
      // If player in a house
      long since = sinceBonusChest();
      LineComponentBuilder builder = LineComponent.builder()
          .left("Time since Bonus Chest:")
          .right(since + "s");

      if (since > NOW_THRESHOLD_BONUS_CHEST) {
        builder.rightColor(Color.RED);
      } else if (since > WARN_THRESHOLD_BONUS_CHEST) {
        builder.rightColor(Color.ORANGE);
      }

      panelComponent.getChildren().add(builder.build());
    }
    return super.render(graphics);
  }
}
