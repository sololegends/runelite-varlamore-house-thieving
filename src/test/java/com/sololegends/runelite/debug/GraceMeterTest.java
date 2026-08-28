package com.sololegends.runelite.debug;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class GraceMeterTest {

	private static final int VICTOR = 13313;
	private static final int CAIUS = 13314;

	/** Drives the meter off a clock the test controls rather than wall time */
	private static class TestMeter extends GraceMeter {
		private long now = 1_000_000;

		@Override
		long now() {
			return now;
		}

		void advance(long millis) {
			now += millis;
		}
	}

	private TestMeter meter;

	@Before
	public void setUp() {
		meter = new TestMeter();
	}

	@Test
	public void anOwnerWalkingInWithNothingArmedRecordsNothing() {
		assertTrue(meter.settle(VICTOR, "Victor").isEmpty());
		assertFalse(meter.hasSamples());
	}

	@Test
	public void timesFromArrivalToTheOwnerSteppingInside() {
		meter.arm(VICTOR, GraceMeter.ANCHOR_DOOR);
		meter.advance(9_400);

		List<String> lines = meter.settle(VICTOR, "Victor");

		assertEquals(1, lines.size());
		assertTrue(lines.get(0), lines.get(0).contains("Victor"));
		assertTrue(lines.get(0), lines.get(0).contains("9.4s"));
		assertTrue(lines.get(0), lines.get(0).contains(GraceMeter.ANCHOR_DOOR));
	}

	@Test
	public void timesBothAnchorsFromTheOneArrival() {
		meter.arm(VICTOR, GraceMeter.ANCHOR_CHAT);
		meter.advance(2_000);
		meter.arm(VICTOR, GraceMeter.ANCHOR_DOOR);
		meter.advance(8_000);

		List<String> lines = meter.settle(VICTOR, "Victor");

		assertEquals(2, lines.size());
		assertTrue(lines.get(0), lines.get(0).contains("10.0s"));
		assertTrue(lines.get(0), lines.get(0).contains(GraceMeter.ANCHOR_CHAT));
		assertTrue(lines.get(1), lines.get(1).contains("8.0s"));
		assertTrue(lines.get(1), lines.get(1).contains(GraceMeter.ANCHOR_DOOR));
	}

	@Test
	public void aSecondSightingDoesNotRestartTheClock() {
		meter.arm(VICTOR, GraceMeter.ANCHOR_DOOR);
		meter.advance(5_000);
		// Owner is still stood at the door on the following ticks
		meter.arm(VICTOR, GraceMeter.ANCHOR_DOOR);
		meter.advance(5_000);

		assertTrue(meter.settle(VICTOR, "Victor").get(0).contains("10.0s"));
	}

	@Test
	public void housesAreTimedIndependently() {
		meter.arm(VICTOR, GraceMeter.ANCHOR_DOOR);
		meter.advance(4_000);
		meter.arm(CAIUS, GraceMeter.ANCHOR_DOOR);
		meter.advance(6_000);

		assertTrue(meter.settle(VICTOR, "Victor").get(0).contains("10.0s"));
		assertTrue(meter.settle(CAIUS, "Caius").get(0).contains("6.0s"));
	}

	@Test
	public void anOwnerWhoWandersPastWithoutGoingInIsDiscarded() {
		meter.arm(VICTOR, GraceMeter.ANCHOR_DOOR);
		assertTrue(meter.isArmed(VICTOR, GraceMeter.ANCHOR_DOOR));

		meter.advance(61_000);
		meter.expire();

		assertFalse(meter.isArmed(VICTOR, GraceMeter.ANCHOR_DOOR));
		assertTrue(meter.settle(VICTOR, "Victor").isEmpty());
	}

	@Test
	public void aStopwatchInsideTheTimeoutSurvives() {
		meter.arm(VICTOR, GraceMeter.ANCHOR_DOOR);

		meter.advance(30_000);
		meter.expire();

		assertTrue(meter.isArmed(VICTOR, GraceMeter.ANCHOR_DOOR));
	}

	@Test
	public void reportsMinimumMeanAndMaximumAcrossArrivals() {
		for (long grace : new long[] { 8_000, 12_000, 10_000 }) {
			meter.arm(VICTOR, GraceMeter.ANCHOR_DOOR);
			meter.advance(grace);
			meter.settle(VICTOR, "Victor");
		}

		String report = meter.report();
		assertTrue(report, report.contains("n=3"));
		assertTrue(report, report.contains("min 8.0s"));
		assertTrue(report, report.contains("avg 10.0s"));
		assertTrue(report, report.contains("max 12.0s"));
	}

	@Test
	public void suggestsTheQuickestArrivalSoTheCountdownIsNeverLate() {
		for (long grace : new long[] { 11_900, 9_600, 13_000 }) {
			meter.arm(VICTOR, GraceMeter.ANCHOR_CHAT);
			meter.advance(grace);
			meter.settle(VICTOR, "Victor");
		}

		// Rounded down from the fastest, being early is free and being late is not
		assertEquals(9, meter.safeGraceSeconds());
	}

	@Test
	public void fallsBackToDoorTimingWhenTheChatWarningWasNeverSeen() {
		meter.arm(VICTOR, GraceMeter.ANCHOR_DOOR);
		meter.advance(7_500);
		meter.settle(VICTOR, "Victor");

		assertEquals(7, meter.safeGraceSeconds());
	}

	@Test
	public void suggestsNothingWithoutData() {
		assertEquals(-1, meter.safeGraceSeconds());
		assertEquals("No owner arrivals measured yet", meter.report());
	}
}
