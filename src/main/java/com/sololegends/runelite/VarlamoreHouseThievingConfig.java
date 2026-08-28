package com.sololegends.runelite;

import java.awt.Color;

import com.sololegends.runelite.render.Highlighter;

import net.runelite.client.config.*;

@ConfigGroup("Varlamore House Thieving")
public interface VarlamoreHouseThievingConfig extends Config {

	@ConfigSection(name = "Features", description = "turn on and off features", position = 0)

	String feature_section = "features";

	@ConfigItem(position = 1, section = feature_section, keyName = "enable_distracted_overlay", name = "When Distracted Overlay", description = "Enable Overlay counting up since the last distracted citizen")
	default boolean enableDistractedOverlay() {
		return true;
	}

	@ConfigItem(position = 2, section = feature_section, keyName = "enable_return_home_overlay", name = "Owner Returning Overlay", description = "Enable Overlay counting up from home owner leaving")
	default boolean enableReturnHomeOverlay() {
		return true;
	}

	@ConfigItem(position = 3, section = feature_section, keyName = "enable_bonus_chest_overlay", name = "Bonus chest Overlay", description = "Enable Overlay counting up since the last bonus chest")
	default boolean enableBonusChestOverlay() {
		return true;
	}

	@ConfigItem(position = 4, section = feature_section, keyName = "disable_blessed_statue_sound", name = "Disable Bonus Item Sounds", description = "Disables the sound effect when you receive a blessed statue or jewelry")
	default boolean disableStatueSoundEffect() {
		return false;
	}

	@ConfigItem(position = 5, section = feature_section, keyName = "disable_flashing_distraction_icon", name = "Disable Distracted Icon Flashing", description = "Disables the flashing of the distracted icon, icon will still show")
	default boolean disableIconFlashing() {
		return false;
	}

	@ConfigItem(position = 6, section = feature_section, keyName = "hide_door_in_house", name = "Hide Door Inside House", description = "Stop highlighting the locked door of the house you are currently inside")
	default boolean hideDoorInHouse() {
		return false;
	}

	@ConfigItem(position = 7, section = feature_section, keyName = "path_to_next_house", name = "Path To Next House", description = "Draws a walkable path to the nearest house that is ready to be robbed, shown once you are out of a house")
	default boolean pathToNextHouse() {
		return false;
	}

	@ConfigItem(position = 8, section = feature_section, keyName = "path_to_bonus_chest", name = "Path To Bonus Chest", description = "Draws a walkable path to the bonus loot chest while you are inside a house")
	default boolean pathToBonusChest() {
		return false;
	}

	@ConfigItem(position = 9, section = feature_section, keyName = "enable_owner_countdown", name = "Owner At Door Countdown", description = "Counts down the grace period from the game's own warning that the homeowner is coming back")
	default boolean enableOwnerCountdown() {
		return false;
	}

	@Range(min = 1, max = 30)
	@Units(Units.SECONDS)
	@ConfigItem(position = 10, section = feature_section, keyName = "owner_entry_grace", name = "Owner Entry Grace", description = "How long you get between the warning and the owner walking in. Jagex do not publish this, use Measure Owner Grace under Debugging to find your own figure")
	default int ownerEntryGrace() {
		return 9;
	}

	@ConfigItem(position = 11, section = feature_section, keyName = "stage_between_houses", name = "Stage Between Houses", description = "While no house is confirmed ready, path to a holding spot between the houses still in play, so you are an equally short walk from whichever owner leaves first. Needs Path To Next House")
	default boolean stageBetweenHouses() {
		return true;
	}

	@ConfigItem(position = 12, section = feature_section, keyName = "quiet_while_looting", name = "Quiet While Looting", description = "Hides the highlights while you are actually working a chest inside a house. A bonus chest still shows, and everything comes back the moment the owner turns up at the door")
	default boolean quietWhileLooting() {
		return false;
	}

	@ConfigItem(position = 13, section = feature_section, keyName = "bonus_chest_timer", name = "Bonus Chest Timer", description = "Counts down above the bonus chest, so you can tell at a glance whether it is worth crossing the room for")
	default boolean bonusChestTimer() {
		return false;
	}

	@Range(min = 1, max = 120)
	@Units(Units.SECONDS)
	@ConfigItem(position = 14, section = feature_section, keyName = "bonus_chest_window", name = "Bonus Chest Window", description = "How long a bonus chest stays bonus. Jagex do not publish this, the default comes from a measured 7.8s, which is 13 game ticks. Measure Owner Grace under Debugging reports each window it sees, so you can check it against your own")
	default int bonusChestWindow() {
		return 8;
	}

	@ConfigSection(name = "Notifications", description = "turn on and off notification", position = 10)
	String notifications_section = "notification";

	@ConfigItem(position = 11, section = notifications_section, keyName = "notify_on_distracted", name = "When Distracted", description = "Trigger a notification when a Wealthy Citizen is being distracted")
	default Notification notifyOnDistracted() {
		return Notification.ON;
	}

	@ConfigItem(position = 12, section = notifications_section, keyName = "notify_on_time_since_distraction", name = "Time Since Distraction", description = "Trigger a notification when the time since a distraction reaches a certain value. 0 turns it off")
	default int notifyOnTimeSinceDistraction() {
		return 0;
	}

	@ConfigItem(position = 13, section = notifications_section, keyName = "notify_on_bonus", name = "Bonus Chest", description = "Trigger a notification when a bonus chest appears")
	default Notification notifyOnBonusChest() {
		return Notification.ON;
	}

	@ConfigItem(position = 14, section = notifications_section, keyName = "notify_on_return_home", name = "Owner Returning", description = "Trigger a notification when the house owner is returning")
	default Notification notifyOnReturnHome() {
		return Notification.ON;
	}

	@ConfigItem(position = 15, section = notifications_section, keyName = "notify_on_empty_container", name = "Empty Container", description = "Trigger a notification when the container you're stealing from is empty")
	default Notification notifyOnEmptyContainer() {
		return Notification.ON;
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

	@ConfigItem(position = 26, section = highlights_section, keyName = "highlight_all_chests", name = "All Chests", description = "Highlights all the intractable chests/cabinets in houses")
	default boolean highlightAllChests() {
		return false;
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

	@Alpha
	@ConfigItem(position = 36, section = styling_section, keyName = "color_all_chests", name = "All Chests", description = "Highlight color for all intractable chests/cabinets in houses")
	default Color colorAllChests() {
		return new Color(0, 1, 1, 0.5f);
	}

	@ConfigItem(position = 37, section = styling_section, keyName = "arrow_icon", name = "User Arrow Icon", description = "Uses an flashing arrow instead of thieving icon for distracted citizens")
	default boolean useArrowIcon() {
		return false;
	}

	@Range(min = Highlighter.MIN_THICKNESS, max = Highlighter.MAX_THICKNESS)
	@ConfigItem(position = 38, section = styling_section, keyName = "outline_thickness", name = "Outline Thickness", description = "Thickness of every highlight outline drawn by the plugin")
	default int outlineThickness() {
		return 2;
	}

	@Range(min = 0, max = 255)
	@ConfigItem(position = 39, section = styling_section, keyName = "fill_opacity", name = "Fill Opacity", description = "Opacity of the fill drawn inside every highlight, in the highlight's own color. 0 draws the outline only")
	default int fillOpacity() {
		return 0;
	}

	@Alpha
	@ConfigItem(position = 40, section = styling_section, keyName = "color_path", name = "Path", description = "Color of the path drawn to the next house and to the bonus loot chest")
	default Color colorPath() {
		return Color.CYAN;
	}

	@Range(min = Highlighter.MIN_THICKNESS, max = Highlighter.MAX_THICKNESS)
	@ConfigItem(position = 41, section = styling_section, keyName = "path_width", name = "Path Width", description = "Thickness of the path line")
	default int pathWidth() {
		return 3;
	}

	@Alpha
	@ConfigItem(position = 42, section = styling_section, keyName = "color_path_staging", name = "Path (Staging)", description = "Color of the dashed path drawn to the holding spot, when no house is confirmed ready yet")
	default Color colorPathStaging() {
		return Color.ORANGE;
	}

	@ConfigSection(name = "Inside House", description = "Configure behaviour whilst you're in a house", position = 40)
	String in_house_section = "in_house";

	@ConfigItem(position = 40, section = in_house_section, keyName = "in_house_distraction_overlay", name = "Distracted Counter in House", description = "Show the distracted citizen counter while you're in Lavinia's house")
	default boolean inHouseShowDistraction() {
		return false;
	}

	@ConfigItem(position = 41, section = in_house_section, keyName = "in_house_distraction_icon", name = "Distracted Notice in House", description = "Show the distracted citizen icon, and alert, while you're in Lavinia's house")
	default boolean inHouseDistractionAlerting() {
		return false;
	}

	@ConfigSection(name = "Debugging", description = "Debugging options", position = 60)
	String debugging_section = "debugging";

	@ConfigItem(position = 60, section = debugging_section, keyName = "debugging_icon_size", name = "Icon Size", description = "Set the distracted Icon size")
	default int debugIconSize() {
		return 25;
	}

	@ConfigItem(position = 61, section = debugging_section, keyName = "debug_grace_meter", name = "Measure Owner Grace", description = "Times how long you actually get between the owner turning up and the owner walking in, and reports it to the chatbox. Turn it back off to get a suggested Owner Entry Grace value")
	default boolean debugGraceMeter() {
		return false;
	}

}
