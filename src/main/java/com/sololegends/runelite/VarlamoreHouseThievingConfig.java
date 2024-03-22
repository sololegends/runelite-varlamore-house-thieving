package com.sololegends.runelite;

import java.awt.Color;

import net.runelite.client.config.*;

@ConfigGroup("Temple Trekking Bog Helper")
public interface VarlamoreHouseThievingConfig extends Config {

	@ConfigItem(position = 1, section = "General", keyName = "notify_on_distracted", name = "Notify When Distracted", description = "Trigger a notification when a Wealthy Citizen is being distracted")
	default boolean notifyOnDistracted() {
		return true;
	}

	@ConfigSection(name = "Highlight Options", description = "turn on and off highlights", position = 10)
	String highlights_section = "highlights";

	@ConfigItem(position = 10, section = highlights_section, keyName = "highlight_doors", name = "Locked Doors", description = "Highlight door of a house ready to be robbed")
	default boolean highlightLockedDoors() {
		return true;
	}

	@ConfigItem(position = 11, section = highlights_section, keyName = "highlight_escape", name = "Escape Window", description = "Highlights escape windows")
	default boolean highlightEscapeWindows() {
		return true;
	}

	@ConfigItem(position = 12, section = highlights_section, keyName = "highlight_wealthy_citizen", name = "Wealthy Citizens", description = "Highlights Wealthy citizens")
	default boolean highlightWealthyCitizens() {
		return true;
	}

	@ConfigItem(position = 13, section = highlights_section, keyName = "highlight_distracted", name = "Distracted Citizens", description = "Highlights distracted citizens")
	default boolean highlightDistractedCitizens() {
		return true;
	}

	@ConfigItem(position = 14, section = highlights_section, keyName = "highlight_homeowner", name = "Home Owners", description = "Highlights home owners")
	default boolean highlightHomeOwners() {
		return true;
	}

	@ConfigSection(name = "Styling", description = "Stylize it!", position = 20)
	String styling_section = "styling";

	@ConfigItem(position = 20, section = styling_section, keyName = "color_doors", name = "Locked Doors", description = "Highlight color for door of a house ready to be robbed")
	default Color colorLockedDoors() {
		return Color.GREEN;
	}

	@ConfigItem(position = 21, section = styling_section, keyName = "color_escape", name = "Escape Window", description = "Highlight color for escape windows")
	default Color colorEscapeWindows() {
		return Color.RED;
	}

	@ConfigItem(position = 22, section = styling_section, keyName = "color_wealthy_citizen", name = "Wealthy Citizens", description = "Highlight color for Wealthy citizens")
	default Color colorWealthyCitizens() {
		return Color.CYAN;
	}

	@ConfigItem(position = 23, section = styling_section, keyName = "color_distracted", name = "Distracted Citizens", description = "Highlight color for distracted citizens")
	default Color colorDistractedCitizens() {
		return Color.GREEN;
	}

	@ConfigItem(position = 24, section = styling_section, keyName = "color_homeowner", name = "Home Owners", description = "Highlight color for home owners")
	default Color colorHomeOwners() {
		return Color.RED;
	}

}
