package com.sololegends.runelite;

import java.awt.Color;

import net.runelite.client.config.*;

@ConfigGroup("Varlamore House Thieving")
public interface VarlamoreHouseThievingConfig extends Config {

	@ConfigSection(name = "Features", description = "turn on and off features", position = 0)

	String feature_section = "features";

	@ConfigItem(position = 1, section = feature_section, keyName = "enable_distracted_overlay", name = "When Distracted Overlay", description = "Enable Wealthy Citizen is being distracted count up")
	default boolean enableDistractedOverlay() {
		return true;
	}

	@ConfigItem(position = 2, section = feature_section, keyName = "enable_return_home_overlay", name = "Owner Returning Overlay", description = "Enable Overlay House owner is returning count up")
	default boolean enableReturnHomeOverlay() {
		return true;
	}

	@ConfigSection(name = "Notifications", description = "turn on and off notification", position = 10)
	String notifications_section = "notification";

	@ConfigItem(position = 11, section = notifications_section, keyName = "notify_on_distracted", name = "Notify When Distracted", description = "Trigger a notification when a Wealthy Citizen is being distracted")
	default boolean notifyOnDistracted() {
		return true;
	}

	@ConfigItem(position = 12, section = notifications_section, keyName = "notify_on_bonus", name = "Notify On Bonus Chest", description = "Trigger a notification when a bonus chest appears")
	default boolean notifyOnBonusChest() {
		return true;
	}

	@ConfigItem(position = 13, section = notifications_section, keyName = "notify_on_return_home", name = "Notify On Owner Returning", description = "Trigger a notification when the house owner is returning")
	default boolean notifyOnReturnHome() {
		return true;
	}

	@ConfigItem(position = 14, section = notifications_section, keyName = "notify_on_empty_container", name = "Notify On Empty Container", description = "Trigger a notification when the container you're stealing from is empty")
	default boolean notifyOnEmptyContainer() {
		return true;
	}

	@ConfigSection(name = "Highlight Options", description = "turn on and off highlights", position = 20)
	String highlights_section = "highlights";

	@ConfigItem(position = 20, section = highlights_section, keyName = "highlight_doors", name = "Locked Doors", description = "Highlight door of a house ready to be robbed")
	default boolean highlightLockedDoors() {
		return true;
	}

	@ConfigItem(position = 21, section = highlights_section, keyName = "highlight_escape", name = "Escape Window", description = "Highlights escape windows")
	default boolean highlightEscapeWindows() {
		return true;
	}

	@ConfigItem(position = 22, section = highlights_section, keyName = "highlight_wealthy_citizen", name = "Wealthy Citizens", description = "Highlights Wealthy citizens")
	default boolean highlightWealthyCitizens() {
		return true;
	}

	@ConfigItem(position = 23, section = highlights_section, keyName = "highlight_distracted", name = "Distracted Citizens", description = "Highlights distracted citizens")
	default boolean highlightDistractedCitizens() {
		return true;
	}

	@ConfigItem(position = 24, section = highlights_section, keyName = "highlight_homeowner", name = "Home Owners", description = "Highlights home owners")
	default boolean highlightHomeOwners() {
		return true;
	}

	@ConfigItem(position = 25, section = highlights_section, keyName = "highlight_bonus_chest", name = "Bonus Chests", description = "Highlights bonus chests")
	default boolean highlightBonusChests() {
		return true;
	}

	@ConfigSection(name = "Styling", description = "Stylize it!", position = 30)
	String styling_section = "styling";

	@Alpha
	@ConfigItem(position = 30, section = styling_section, keyName = "color_doors", name = "Locked Doors", description = "Highlight color for door of a house ready to be robbed")
	default Color colorLockedDoors() {
		return Color.GREEN;
	}

	@Alpha
	@ConfigItem(position = 31, section = styling_section, keyName = "color_escape", name = "Escape Window", description = "Highlight color for escape windows")
	default Color colorEscapeWindows() {
		return Color.RED;
	}

	@Alpha
	@ConfigItem(position = 32, section = styling_section, keyName = "color_wealthy_citizen", name = "Wealthy Citizens", description = "Highlight color for Wealthy citizens")
	default Color colorWealthyCitizens() {
		return Color.CYAN;
	}

	@Alpha
	@ConfigItem(position = 33, section = styling_section, keyName = "color_distracted", name = "Distracted Citizens", description = "Highlight color for distracted citizens")
	default Color colorDistractedCitizens() {
		return Color.GREEN;
	}

	@Alpha
	@ConfigItem(position = 34, section = styling_section, keyName = "color_homeowner", name = "Home Owners", description = "Highlight color for home owners")
	default Color colorHomeOwners() {
		return Color.RED;
	}

	@Alpha
	@ConfigItem(position = 35, section = styling_section, keyName = "color_bonus_chest", name = "Bonus Chests", description = "Highlight color for bonus chests")
	default Color colorBonusChests() {
		return Color.GREEN;
	}

}
