package com.sololegends.runelite.data;

import java.util.ArrayList;
import java.util.List;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

public class Houses {

	private static List<House> HOUSES = new ArrayList<>() {
		{
			// Lavinia
			add(new House("Lavinia", 1666, 3080, 1676, 3094));
			// Victor
			add(new House("Victor", 1632, 3093, 1640, 3103));
			// Cauis
			add(new House("Cauis", 1631, 3117, 1640, 3124));
		}
	};

	public static boolean inHouse(Player player) {
		for (House house : HOUSES) {
			if (house.contains(player.getWorldLocation())) {
				return true;
			}
		}
		return false;
	}

	public static House getHouse(Player player) {
		for (House house : HOUSES) {
			if (house.contains(player.getWorldLocation())) {
				return house;
			}
		}
		return null;
	}

	public static class House {
		public final int x1, y1, x2, y2;
		public final String name;

		public House(String name, int x1, int y1, int x2, int y2) {
			this.name = name;
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
		}

		public boolean contains(WorldPoint point) {
			return point.getX() >= x1 && point.getX() <= x2
					&& point.getY() >= y1 && point.getY() <= y2;
		}

		@Override
		public int hashCode() {
			return x1 + x2 + y1 + y2;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof House) {
				House tm = (House) obj;
				return tm.x1 == x1 && tm.y1 == y1 && tm.x2 == x2 && tm.y2 == y2;
			}
			return false;
		}
	}
}