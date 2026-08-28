package com.sololegends.runelite.data;

import java.util.ArrayList;
import java.util.List;

import com.sololegends.runelite.VarlamoreHouseThievingPlugin;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

public class Houses {

	/**
	 * A house you have already been inside stays off the shortlist until it is
	 * worth robbing again, there is nothing left in it to steal. The timeout is a
	 * safety net for when you never walk back past the door to see it re-lock.
	 */
	private static final long VISITED_TIMEOUT = 120_000;

	/**
	 * A door re-locking only means fresh loot once the owner has actually been
	 * home and left again, which takes minutes. Anything sooner is the door
	 * object flickering as you walk in and out, so it is ignored.
	 */
	private static final long VISITED_MIN_AGE = 60_000;

	private static List<House> HOUSES = new ArrayList<>() {
		{
			// Lavinia
			add(
					new House(VarlamoreHouseThievingPlugin.LAVINIA_ID, "Lavinia",
							1666, 3080, 1676, 3094,
							new Door(1668, 3094),
							new Escape(1670, 3088)));
			// Victor
			add(
					new House(VarlamoreHouseThievingPlugin.VICTOR_ID, "Victor",
							1632, 3093, 1640, 3103,
							new Door(1636, 3103),
							new Escape(1637, 3090)));
			// Cauis
			add(
					new House(VarlamoreHouseThievingPlugin.CAIUS_ID, "Caius",
							1631, 3117, 1640, 3124,
							new Door(1631, 3121),
							new Escape(1638, 3124)));
		}
	};

	public static boolean inLaviniaHouse(Player player) {
		for (House house : HOUSES) {
			if (house.id == VarlamoreHouseThievingPlugin.LAVINIA_ID
					&& house.contains(player.getWorldLocation())) {
				return true;
			}
		}
		return false;
	}

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

	/** Marks the house you are standing in as already robbed */
	public static void registerVisited(Player player) {
		House house = getHouse(player);
		if (house != null) {
			house.setVisited();
		}
	}

	/**
	 * Every house still worth walking to, the one you are stood in and the ones
	 * you have already emptied are dropped.
	 */
	public static List<House> getCandidates(WorldPoint from) {
		List<House> candidates = new ArrayList<>();
		for (House house : HOUSES) {
			if (house.contains(from) || house.isVisited()) {
				continue;
			}
			candidates.add(house);
		}
		return candidates;
	}

	/**
	 * The closest candidate whose door is confirmed locked, meaning the owner is
	 * out and the house is ready to be robbed right now.
	 */
	public static House getNearestAvailable(WorldPoint from) {
		House nearest = null;
		int nearest_dist = Integer.MAX_VALUE;
		for (House house : getCandidates(from)) {
			if (!house.door.isLocked()) {
				continue;
			}
			int dist = from.distanceTo2D(house.door.getWorldLocation());
			if (dist < nearest_dist) {
				nearest_dist = dist;
				nearest = house;
			}
		}
		return nearest;
	}

	/**
	 * A holding spot roughly equidistant from every house still in play, for when
	 * no door is confirmed open yet. Waiting here keeps you the same short walk
	 * from whichever owner leaves first.
	 */
	public static WorldPoint getStagingPoint(List<House> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		int x = 0;
		int y = 0;
		for (House house : candidates) {
			x += house.door.x;
			y += house.door.y;
		}
		return new WorldPoint(x / candidates.size(), y / candidates.size(), 0);
	}

	/** True when the point sits inside the house that owns the given door */
	public static boolean insideHouseOf(WorldPoint door, WorldPoint point) {
		for (House house : HOUSES) {
			if (house.door.is(door)) {
				return house.contains(point);
			}
		}
		return false;
	}

	public static void registerLocked(WorldPoint door) {
		for (House house : HOUSES) {
			if (house.door.is(door)) {
				// A fresh lock means the owner has been home and gone out again, so
				// whatever you took last time has been restocked
				if (house.door.setLocked()) {
					house.clearVisitedIfSettled();
				}
			}
		}
	}

	public static boolean isLocked(WorldPoint door) {
		for (House house : HOUSES) {
			if (house.door.is(door)) {
				return house.door.isLocked();
			}
		}
		return false;
	}

	public static void registerUnlocked(WorldPoint door) {
		for (House house : HOUSES) {
			if (house.door.is(door)) {
				// Deliberately says nothing about whether the house has restocked. An
				// unlocked door is the owner being home OR the one you opened yourself,
				// and nothing about the door itself tells the two apart, from inside or
				// out. Only seeing the owner indoors, or the door re-locking, does
				house.door.setUnlocked();
			}
		}
	}

	/**
	 * The owner is stood in their own house, which is the one unambiguous sign it
	 * will be worth robbing again once they next head out.
	 */
	public static void registerOwnerHome(int owner) {
		House house = getHouse(owner);
		if (house != null) {
			house.clearVisited();
		}
	}

	public static class House {
		public final int x1, y1, x2, y2;
		public final int id;
		public final String name;
		public final Door door;
		public final Escape escape;
		private long visited_at = -1;

		public House(int id, String name, int x1, int y1, int x2, int y2, Door door, Escape escape) {
			this.id = id;
			this.name = name;
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

		public boolean isVisited() {
			return visited_at != -1 && System.currentTimeMillis() - visited_at < VISITED_TIMEOUT;
		}

		public void setVisited() {
			visited_at = System.currentTimeMillis();
		}

		public void clearVisited() {
			visited_at = -1;
		}

		/** Package visible so the ageing of a visit can be exercised in tests */
		void setVisitedAt(long at) {
			visited_at = at;
		}

		/** Clears the visit only once it is old enough to have been a real trip */
		public void clearVisitedIfSettled() {
			if (visited_at != -1 && System.currentTimeMillis() - visited_at > VISITED_MIN_AGE) {
				clearVisited();
			}
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

		/** Returns true when this call is the transition into locked */
		public boolean setLocked() {
			boolean was_locked = locked;
			locked = true;
			return !was_locked;
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
