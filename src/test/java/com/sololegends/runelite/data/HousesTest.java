package com.sololegends.runelite.data;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sololegends.runelite.VarlamoreHouseThievingPlugin;
import com.sololegends.runelite.data.Houses.House;

import net.runelite.api.coords.WorldPoint;

/**
 * The house list is static and mutable, so every test puts all three back to
 * owner home and never robbed before it starts.
 */
public class HousesTest {

	private static final WorldPoint IN_THE_MARKET = new WorldPoint(1650, 3110, 0);
	private static final WorldPoint INSIDE_LAVINIA = new WorldPoint(1670, 3085, 0);
	private static final WorldPoint INSIDE_VICTOR = new WorldPoint(1636, 3098, 0);

	private House lavinia() {
		return Houses.getHouse(VarlamoreHouseThievingPlugin.LAVINIA_ID);
	}

	private House victor() {
		return Houses.getHouse(VarlamoreHouseThievingPlugin.VICTOR_ID);
	}

	private House caius() {
		return Houses.getHouse(VarlamoreHouseThievingPlugin.CAIUS_ID);
	}

	@Before
	public void resetHouses() {
		for (House house : new House[] { lavinia(), victor(), caius() }) {
			house.clearVisited();
			house.door.setUnlocked();
		}
	}

	@Test
	public void candidatesDropTheHouseYouAreStandingIn() {
		List<House> candidates = Houses.getCandidates(INSIDE_LAVINIA);

		assertEquals(2, candidates.size());
		assertFalse(candidates.contains(lavinia()));
	}

	@Test
	public void candidatesDropAHouseYouHaveAlreadyEmptied() {
		victor().setVisited();

		List<House> candidates = Houses.getCandidates(IN_THE_MARKET);

		assertEquals(2, candidates.size());
		assertFalse(candidates.contains(victor()));
	}

	@Test
	public void anUnlockedDoorNeverPutsTheHouseBackInPlay() {
		victor().setVisited();

		// The door you opened yourself reads unlocked from inside and from outside
		// alike, and neither reading says the owner has been home to restock it
		Houses.registerUnlocked(victor().door.getWorldLocation());

		assertTrue(victor().isVisited());
		assertFalse(Houses.getCandidates(IN_THE_MARKET).contains(victor()));
		assertFalse(Houses.getCandidates(INSIDE_VICTOR).contains(victor()));
	}

	@Test
	public void aFreshLockAfterTheOwnerCycleRestocksTheHouse() {
		// Visited long enough ago for the owner to have been home and left again
		victor().setVisitedAt(System.currentTimeMillis() - 90_000);
		Houses.registerUnlocked(victor().door.getWorldLocation());

		Houses.registerLocked(victor().door.getWorldLocation());

		assertFalse(victor().isVisited());
		assertTrue(Houses.getCandidates(IN_THE_MARKET).contains(victor()));
	}

	@Test
	public void aDoorFlickerDoesNotRestockTheHouse() {
		// Walked out a second ago, the owner cannot possibly have restocked it
		victor().setVisited();
		Houses.registerUnlocked(victor().door.getWorldLocation());

		Houses.registerLocked(victor().door.getWorldLocation());

		assertTrue(victor().isVisited());
		assertFalse(Houses.getCandidates(IN_THE_MARKET).contains(victor()));
	}

	@Test
	public void seeingTheOwnerIndoorsPutsTheHouseBackInPlay() {
		victor().setVisited();

		// The owner stood in their own house is the one unambiguous sign, unlike the
		// door, which reads the same whoever opened it
		Houses.registerOwnerHome(VarlamoreHouseThievingPlugin.VICTOR_ID);

		assertFalse(victor().isVisited());
		assertTrue(Houses.getCandidates(IN_THE_MARKET).contains(victor()));
	}

	@Test
	public void doesNotRouteBackToTheHouseYouJustEmptied() {
		// Door stays locked after you leave, the owner has not returned yet
		Houses.registerLocked(lavinia().door.getWorldLocation());
		lavinia().setVisited();

		assertNull(Houses.getNearestAvailable(IN_THE_MARKET));
	}

	@Test
	public void findsTheConfirmedOpenHouse() {
		Houses.registerLocked(caius().door.getWorldLocation());

		assertEquals(caius(), Houses.getNearestAvailable(IN_THE_MARKET));
	}

	@Test
	public void picksTheNearerOfTwoOpenHouses() {
		Houses.registerLocked(victor().door.getWorldLocation());
		Houses.registerLocked(caius().door.getWorldLocation());

		// Stood just south of Victor's door, well away from Caius
		assertEquals(victor(), Houses.getNearestAvailable(new WorldPoint(1636, 3090, 0)));
	}

	@Test
	public void stagingPointSitsBetweenTheRemainingHouses() {
		// Having just left Lavinia, Victor and Caius are what is left in play
		WorldPoint staging = Houses.getStagingPoint(Houses.getCandidates(INSIDE_LAVINIA));

		// Victor's door 1636,3103 and Caius' door 1631,3121
		assertEquals(new WorldPoint(1633, 3112, 0), staging);
	}

	@Test
	public void stagingPointIsEquallyFarFromEachRemainingDoor() {
		List<House> candidates = Houses.getCandidates(INSIDE_LAVINIA);
		WorldPoint staging = Houses.getStagingPoint(candidates);

		int first = staging.distanceTo2D(candidates.get(0).door.getWorldLocation());
		int second = staging.distanceTo2D(candidates.get(1).door.getWorldLocation());
		assertTrue("staging point favours one house: " + first + " vs " + second,
				Math.abs(first - second) <= 1);
	}

	@Test
	public void noCandidatesMeansNoStagingPoint() {
		lavinia().setVisited();
		victor().setVisited();
		caius().setVisited();

		assertTrue(Houses.getCandidates(IN_THE_MARKET).isEmpty());
		assertNull(Houses.getStagingPoint(Houses.getCandidates(IN_THE_MARKET)));
	}
}
