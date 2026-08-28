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
  public static final int NOW_THRESHOLD_BONUS_CHEST = 45;
  public static final int WARN_THRESHOLD_OWNER_ARRIVED = 3;

  // system time in milliseconds
  private static long last_distraction = -1;
  private static long owner_left = -1;
  private static long owner_arrived = -1;
  private static long bonus_chest = -1;
  private static String next_house = null;
  private static String next_house_heading = null;
  private static boolean next_house_confirmed = false;
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

  /** Latched on arrival so the countdown runs from the first sighting */
  public static void trackOwnerArrived() {
    if (owner_arrived == -1) {
      owner_arrived = System.currentTimeMillis();
    }
  }

  public static void resetOwnerArrived() {
    owner_arrived = -1;
  }

  public static void trackBonusChest() {
    bonus_chest = System.currentTimeMillis();
  }

  public static void resetBonusChest() {
    bonus_chest = -1;
  }

  public static void trackNextHouse(String heading, String name, boolean confirmed) {
    next_house_heading = heading;
    next_house = name;
    next_house_confirmed = confirmed;
  }

  public static void resetNextHouse() {
    next_house = null;
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

  public static boolean ownerArrived() {
    return owner_arrived != -1;
  }

  public static long sinceOwnerArrived() {
    return (System.currentTimeMillis() - owner_arrived) / 1000;
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
      resetOwnerArrived();
      resetNextHouse();
    }
    // Reset owner countdown if feature turned off, or player not in house
    if (!config.enableOwnerCountdown() || !in_house) {
      resetOwnerArrived();
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

    // Shown inside too, from the moment the owner turns up, so you know where you
    // are headed before you have even climbed out
    if (next_house != null) {
      LineComponentBuilder builder = LineComponent.builder()
          .left(next_house_heading)
          .right(next_house);
      builder.rightColor(next_house_confirmed ? Color.GREEN : Color.ORANGE);
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
    if (owner_arrived != -1) {
      // Counts down what is left of the grace period the owner spends at the door
      long remaining = config.ownerEntryGrace() - sinceOwnerArrived();
      LineComponentBuilder builder = LineComponent.builder()
          .left("Owner at door:")
          .right(remaining > 0 ? remaining + "s" : "GO!");

      if (remaining <= 0) {
        builder.rightColor(Color.RED);
      } else if (remaining <= WARN_THRESHOLD_OWNER_ARRIVED) {
        builder.rightColor(Color.ORANGE);
      } else {
        builder.rightColor(Color.YELLOW);
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
