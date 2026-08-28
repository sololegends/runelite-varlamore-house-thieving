package com.sololegends.runelite.path;

import java.util.*;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

/**
 * Breadth first search across the loaded scene's collision map, used to draw a
 * walkable route rather than a straight line through the walls.
 *
 * <p>
 * Targets are frequently un-walkable themselves (a door, a chest), so the
 * search keeps the reachable tile that lands closest to the target and routes
 * there instead of giving up.
 * </p>
 *
 * <p>
 * Not thread safe, the scratch buffers are reused between searches to keep the
 * per frame allocation down. Overlays render on the client thread so each
 * instance stays owned by a single caller.
 * </p>
 */
public class Pathfinder {

	private static final int BLOCKED = CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
			| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION
			| CollisionDataFlag.BLOCK_MOVEMENT_FULL
			| CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;

	// Cardinals first so the reconstructed path prefers straight lines
	private static final int[] DX = { 0, 1, 0, -1, 1, 1, -1, -1 };
	private static final int[] DY = { 1, 0, -1, 0, 1, -1, -1, 1 };
	private static final int[] SIDE = {
			CollisionDataFlag.BLOCK_MOVEMENT_NORTH,
			CollisionDataFlag.BLOCK_MOVEMENT_EAST,
			CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,
			CollisionDataFlag.BLOCK_MOVEMENT_WEST,
			CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST,
			CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST,
			CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST,
			CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST
	};

	private static final int SIZE = Constants.SCENE_SIZE;
	private static final int TILES = SIZE * SIZE;
	private static final int UNVISITED = -1;
	private static final int ROOT = -2;

	// Doors open and objects move while you stand still, so never trust a cached
	// route for longer than this
	private static final long CACHE_TIMEOUT = 2_000;

	private final int[] parent = new int[TILES];
	private final int[] queue = new int[TILES];

	private WorldPoint cached_start = null;
	private WorldPoint cached_target = null;
	private long cached_at = 0;
	private List<WorldPoint> cached_path = Collections.emptyList();

	/**
	 * Route from the player to the target, re-calculated only once the player or
	 * the target has moved, or the cache has gone stale.
	 */
	public List<WorldPoint> path(Client client, WorldPoint target) {
		Player player = client == null ? null : client.getLocalPlayer();
		WorldView wv = client == null ? null : client.getTopLevelWorldView();
		if (player == null || wv == null || target == null) {
			return reset();
		}
		WorldPoint start = player.getWorldLocation();
		if (start == null) {
			return reset();
		}
		if (start.equals(cached_start) && target.equals(cached_target)
				&& System.currentTimeMillis() - cached_at < CACHE_TIMEOUT) {
			return cached_path;
		}
		cached_start = start;
		cached_target = target;
		cached_at = System.currentTimeMillis();
		cached_path = search(wv, start, target);
		return cached_path;
	}

	public List<WorldPoint> reset() {
		cached_start = null;
		cached_target = null;
		cached_at = 0;
		cached_path = Collections.emptyList();
		return cached_path;
	}

	private List<WorldPoint> search(WorldView wv, WorldPoint start, WorldPoint target) {
		Scene scene = wv.getScene();
		CollisionData[] maps = wv.getCollisionMaps();
		int plane = wv.getPlane();
		if (scene == null || maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) {
			return Collections.emptyList();
		}
		return search(maps[plane].getFlags(), scene.getBaseX(), scene.getBaseY(), plane, start, target);
	}

	/**
	 * Split out from the scene lookup so the search itself can be exercised
	 * against a hand built collision map.
	 */
	List<WorldPoint> search(int[][] flags, int base_x, int base_y, int plane, WorldPoint start, WorldPoint target) {
		final int start_x = start.getX() - base_x;
		final int start_y = start.getY() - base_y;
		if (!inScene(start_x, start_y)) {
			return Collections.emptyList();
		}
		// The target may sit outside the scene, the distance heuristic still walks us
		// in the right direction
		final int target_x = target.getX() - base_x;
		final int target_y = target.getY() - base_y;

		Arrays.fill(parent, UNVISITED);
		final int start_index = index(start_x, start_y);
		parent[start_index] = ROOT;

		int best_index = start_index;
		int best_distance = distance(start_x, start_y, target_x, target_y);

		int head = 0;
		int tail = 0;
		queue[tail++] = start_index;
		while (head < tail) {
			final int current = queue[head++];
			final int x = current / SIZE;
			final int y = current % SIZE;
			if (x == target_x && y == target_y) {
				best_index = current;
				break;
			}
			for (int d = 0; d < DX.length; d++) {
				final int nx = x + DX[d];
				final int ny = y + DY[d];
				if (!inScene(nx, ny)) {
					continue;
				}
				final int next = index(nx, ny);
				if (parent[next] != UNVISITED || !passable(flags, x, y, nx, ny, d)) {
					continue;
				}
				parent[next] = current;
				// Breadth first, so the first tile to reach a distance owns the shortest
				// route to it
				final int dist = distance(nx, ny, target_x, target_y);
				if (dist < best_distance) {
					best_distance = dist;
					best_index = next;
				}
				queue[tail++] = next;
			}
		}
		return reconstruct(best_index, base_x, base_y, plane);
	}

	private boolean passable(int[][] flags, int x, int y, int nx, int ny, int d) {
		if (!inScene(nx, ny)) {
			return false;
		}
		if ((flags[nx][ny] & BLOCKED) != 0 || (flags[x][y] & SIDE[d]) != 0) {
			return false;
		}
		if (d < 4) {
			return true;
		}
		// Diagonals also need both of the cardinals they cut the corner between
		final int dx = DX[d];
		final int dy = DY[d];
		return passable(flags, x, y, x + dx, y, dx > 0 ? 1 : 3)
				&& passable(flags, x, y, x, y + dy, dy > 0 ? 0 : 2);
	}

	private List<WorldPoint> reconstruct(int end, int base_x, int base_y, int plane) {
		final LinkedList<WorldPoint> path = new LinkedList<>();
		int current = end;
		while (current >= 0) {
			path.addFirst(new WorldPoint(base_x + (current / SIZE), base_y + (current % SIZE), plane));
			// ROOT marks the start tile, UNVISITED should be unreachable from here
			current = parent[current];
			if (current == ROOT || current == UNVISITED) {
				break;
			}
		}
		return path;
	}

	private static int index(int x, int y) {
		return x * SIZE + y;
	}

	private static boolean inScene(int x, int y) {
		return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
	}

	/** Chebyshev, movement is 8 directional */
	private static int distance(int x, int y, int tx, int ty) {
		return Math.max(Math.abs(x - tx), Math.abs(y - ty));
	}
}
