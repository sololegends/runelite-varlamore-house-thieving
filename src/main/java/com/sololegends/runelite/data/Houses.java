package com.sololegends.runelite.data;

import java.util.ArrayList;
import java.util.List;

import com.sololegends.runelite.VarlamoreHouseThievingPlugin;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

public class Houses {

	private static List<House> HOUSES = new ArrayList<>() {
		{
			// Lavinia
			add(
					new House(VarlamoreHouseThievingPlugin.LAVINIA_ID,
							1666, 3080, 1676, 3094,
							new Door(1668, 3094),
							new Escape(1670, 3088)));
			// Victor
			add(
					new House(VarlamoreHouseThievingPlugin.VICTOR_ID,
							1632, 3093, 1640, 3103,
							new Door(1636, 3103),
							new Escape(1637, 3090)));
			// Cauis
			add(
					new House(VarlamoreHouseThievingPlugin.CAIUS_ID,
							1631, 3117, 1640, 3124,
							new Door(1631, 3121),
							new Escape(1638, 3124)));
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

	public static House getHouse(int owner) {
		for (House house : HOUSES) {
			if (house.id == owner) {
				return house;
			}
		}
		return null;
	}

	public static void registerLocked(WorldPoint door) {
		for (House house : HOUSES) {
			if (house.door.is(door)) {
				house.door.setLocked();
			}
		}
	}

	public static void registerUnlocked(WorldPoint door) {
		for (House house : HOUSES) {
			if (house.door.is(door)) {
				house.door.setUnlocked();
			}
		}
	}

	public static class House {
		public final int x1, y1, x2, y2;
		public final int id;
		public final Door door;
		public final Escape escape;

		public House(int id, int x1, int y1, int x2, int y2, Door door, Escape escape) {
			this.id = id;
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.door = door;
			this.escape = escape;
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

	public static class Door {
		public final int x, y;
		private boolean locked = false;

		public Door(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public WorldPoint getWorldLocation() {
			return new WorldPoint(x, y, 0);
		}

		public boolean isLocked() {
			return locked;
		}

		public void setLocked() {
			locked = true;
		}

		public void setUnlocked() {
			locked = false;
		}

		public boolean is(WorldPoint point) {
			return point.getX() == x && point.getY() == y;
		}

		@Override
		public int hashCode() {
			return x + y;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Door) {
				Door tm = (Door) obj;
				return tm.x == x && tm.y == y;
			}
			return false;
		}
	}

	public static class Escape extends Door {
		public Escape(int x, int y) {
			super(x, y);
		}
	}
}