package com.sololegends.runelite.path;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.coords.WorldPoint;

/**
 * The search runs against a hand built collision map, the client is never
 * involved so the walls, corners and unreachable target handling can all be
 * asserted directly.
 */
public class PathfinderTest {

	private static final int BASE_X = 1600;
	private static final int BASE_Y = 3000;
	private static final int SIZE = Constants.SCENE_SIZE;

	private static final int SOLID = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

	private int[][] openScene() {
		return new int[SIZE][SIZE];
	}

	private WorldPoint world(int scene_x, int scene_y) {
		return new WorldPoint(BASE_X + scene_x, BASE_Y + scene_y, 0);
	}

	private List<WorldPoint> path(int[][] flags, WorldPoint start, WorldPoint target) {
		return new Pathfinder().search(flags, BASE_X, BASE_Y, 0, start, target);
	}

	private void assertContiguous(List<WorldPoint> path) {
		for (int i = 1; i < path.size(); i++) {
			WorldPoint a = path.get(i - 1);
			WorldPoint b = path.get(i);
			int dx = Math.abs(a.getX() - b.getX());
			int dy = Math.abs(a.getY() - b.getY());
			assertTrue("step " + i + " jumps from " + a + " to " + b, dx <= 1 && dy <= 1 && (dx + dy) > 0);
		}
	}

	@Test
	public void findsStraightPathAcrossOpenGround() {
		List<WorldPoint> path = path(openScene(), world(10, 10), world(10, 20));

		assertEquals(world(10, 10), path.get(0));
		assertEquals(world(10, 20), path.get(path.size() - 1));
		// Ten tiles north, plus the tile we started on
		assertEquals(11, path.size());
		assertContiguous(path);
	}

	@Test
	public void usesDiagonalsWhenAvailable() {
		List<WorldPoint> path = path(openScene(), world(10, 10), world(20, 20));

		assertEquals(world(20, 20), path.get(path.size() - 1));
		// Diagonal movement makes this ten steps rather than twenty
		assertEquals(11, path.size());
		assertContiguous(path);
	}

	@Test
	public void routesAroundASolidWallRatherThanThroughIt() {
		int[][] flags = openScene();
		// Wall spanning x 5..15 at y 15, leaving a gap at x 15
		for (int x = 5; x < 15; x++) {
			flags[x][15] = SOLID;
		}

		List<WorldPoint> path = path(flags, world(10, 10), world(10, 20));

		assertEquals(world(10, 20), path.get(path.size() - 1));
		assertContiguous(path);
		for (WorldPoint point : path) {
			int x = point.getX() - BASE_X;
			int y = point.getY() - BASE_Y;
			assertFalse("path walked through the wall at " + point, y == 15 && x >= 5 && x < 15);
		}
	}

	@Test
	public void respectsDirectionalWallFlags() {
		int[][] flags = openScene();
		// A fence line, every tile at y 15 refuses to be entered from the south and
		// every tile at y 14 refuses to move north
		for (int x = 5; x < 15; x++) {
			flags[x][14] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
			flags[x][15] |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		}

		List<WorldPoint> path = path(flags, world(10, 10), world(10, 20));

		assertEquals(world(10, 20), path.get(path.size() - 1));
		assertContiguous(path);
		// Every crossing of the fence line has to happen outside its span
		for (int i = 1; i < path.size(); i++) {
			int from_y = path.get(i - 1).getY() - BASE_Y;
			int to_y = path.get(i).getY() - BASE_Y;
			if (from_y == 14 && to_y == 15) {
				int x = path.get(i).getX() - BASE_X;
				assertTrue("crossed the fence at x " + x, x < 5 || x >= 15);
			}
		}
	}

	@Test
	public void stopsBesideAnUnreachableTarget() {
		int[][] flags = openScene();
		// The target itself is solid, a chest or a wall
		flags[10][20] = SOLID;

		List<WorldPoint> path = path(flags, world(10, 10), world(10, 20));

		WorldPoint end = path.get(path.size() - 1);
		assertNotEquals("should not have entered the blocked tile", world(10, 20), end);
		assertEquals("should stop directly beside it", 1, end.distanceTo2D(world(10, 20)));
		assertContiguous(path);
	}

	@Test
	public void walledInPlayerGetsNoUsablePath() {
		int[][] flags = openScene();
		for (int x = 9; x <= 11; x++) {
			for (int y = 9; y <= 11; y++) {
				if (x != 10 || y != 10) {
					flags[x][y] = SOLID;
				}
			}
		}

		List<WorldPoint> path = path(flags, world(10, 10), world(10, 20));

		// Only the tile the player stands on, the overlay draws nothing for this
		assertEquals(1, path.size());
		assertEquals(world(10, 10), path.get(0));
	}

	@Test
	public void sealedRoomYieldsAStubThatNeverNearsTheTarget() {
		int[][] flags = openScene();
		// Walled in the way a house is once the owner has locked the door
		for (int x = 8; x <= 12; x++) {
			flags[x][8] = SOLID;
			flags[x][12] = SOLID;
		}
		for (int y = 8; y <= 12; y++) {
			flags[8][y] = SOLID;
			flags[12][y] = SOLID;
		}

		List<WorldPoint> path = path(flags, world(10, 10), world(10, 30));

		// The search still returns something, it just never gets out of the room,
		// which is what the overlay checks before drawing a line
		WorldPoint end = path.get(path.size() - 1);
		assertTrue("escaped a sealed room", end.distanceTo2D(world(10, 30)) > 2);
		assertContiguous(path);
		for (WorldPoint point : path) {
			int y = point.getY() - BASE_Y;
			assertTrue("left the room at " + point, y > 8 && y < 12);
		}
	}

	@Test
	public void headsTowardsATargetOutsideTheScene() {
		List<WorldPoint> path = path(openScene(), world(10, 10), world(10, SIZE + 40));

		WorldPoint end = path.get(path.size() - 1);
		assertContiguous(path);
		// Walks to the northern edge of the loaded scene in the target's direction
		assertEquals(SIZE - 1, end.getY() - BASE_Y);
	}

	@Test
	public void startingOutsideTheSceneYieldsNothing() {
		assertTrue(path(openScene(), world(-5, 10), world(10, 20)).isEmpty());
	}

	@Test
	public void reusedInstanceDoesNotLeakStateBetweenSearches() {
		Pathfinder pathfinder = new Pathfinder();
		int[][] flags = openScene();

		List<WorldPoint> first = pathfinder.search(flags, BASE_X, BASE_Y, 0, world(10, 10), world(10, 20));
		List<WorldPoint> second = pathfinder.search(flags, BASE_X, BASE_Y, 0, world(30, 30), world(30, 40));

		assertEquals(world(10, 10), first.get(0));
		assertEquals(world(30, 30), second.get(0));
		assertEquals(world(30, 40), second.get(second.size() - 1));
		assertContiguous(second);
	}
}
