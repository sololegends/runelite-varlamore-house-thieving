package com.sololegends.runelite.debug;

import java.util.*;

/**
 * Stopwatch for the window between the owner turning up at their door and the
 * owner actually walking inside, which is the moment you are caught.
 *
 * <p>
 * Two anchors are timed side by side from the same arrival so they can be
 * compared, the chatbox warning and the owner reaching their own door. The
 * chatbox warning is the one the game fires deterministically, door proximity
 * depends on when the NPC comes into view.
 * </p>
 */
public class GraceMeter {

	public static final String ANCHOR_DOOR = "owner at door";
	public static final String ANCHOR_CHAT = "chat warning";

	/**
	 * An owner who wandered past their door without going in leaves a stopwatch
	 * running, drop it rather than reporting a nonsense figure later.
	 */
	private static final long ARM_TIMEOUT = 60_000;

	private final Map<String, Long> running = new HashMap<>();
	private final Map<String, List<Long>> samples = new LinkedHashMap<>();

	private static String key(int house_id, String anchor) {
		return house_id + ":" + anchor;
	}

	/** Overridable so the timing behaviour can be driven from a test */
	long now() {
		return System.currentTimeMillis();
	}

	/** First arrival wins, later ticks must not restart the clock */
	public void arm(int house_id, String anchor) {
		running.putIfAbsent(key(house_id, anchor), now());
	}

	public boolean isArmed(int house_id, String anchor) {
		return running.containsKey(key(house_id, anchor));
	}

	public void expire() {
		final long now = now();
		running.values().removeIf(started -> now - started > ARM_TIMEOUT);
	}

	/**
	 * The owner is inside, so every stopwatch for that house stops. Returns one
	 * report line per anchor that was running, newest measurement first.
	 */
	public List<String> settle(int house_id, String house_name) {
		final List<String> lines = new ArrayList<>();
		final long now = now();
		for (String anchor : new String[] { ANCHOR_CHAT, ANCHOR_DOOR }) {
			Long started = running.remove(key(house_id, anchor));
			if (started == null) {
				continue;
			}
			long elapsed = now - started;
			samples.computeIfAbsent(anchor, unused -> new ArrayList<>()).add(elapsed);
			lines.add(house_name + " entered " + seconds(elapsed) + " after " + anchor
					+ "  [" + summary(anchor) + "]");
		}
		return lines;
	}

	public void reset() {
		running.clear();
		samples.clear();
	}

	public boolean hasSamples() {
		return !samples.isEmpty();
	}

	/** min / mean / max across everything measured so far, per anchor */
	public String report() {
		if (samples.isEmpty()) {
			return "No owner arrivals measured yet";
		}
		StringBuilder out = new StringBuilder();
		for (String anchor : samples.keySet()) {
			if (out.length() > 0) {
				out.append("   ");
			}
			out.append(anchor).append(": ").append(summary(anchor));
		}
		return out.toString();
	}

	private String summary(String anchor) {
		List<Long> values = samples.get(anchor);
		if (values == null || values.isEmpty()) {
			return "n=0";
		}
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		long total = 0;
		for (long value : values) {
			min = Math.min(min, value);
			max = Math.max(max, value);
			total += value;
		}
		return "n=" + values.size()
				+ " min " + seconds(min)
				+ " avg " + seconds(total / values.size())
				+ " max " + seconds(max);
	}

	/**
	 * The config is whole seconds, so round down. Being a little early is safe,
	 * being a little late is a lost inventory.
	 */
	public int safeGraceSeconds() {
		List<Long> values = samples.get(ANCHOR_CHAT);
		if (values == null || values.isEmpty()) {
			values = samples.get(ANCHOR_DOOR);
		}
		if (values == null || values.isEmpty()) {
			return -1;
		}
		long min = Long.MAX_VALUE;
		for (long value : values) {
			min = Math.min(min, value);
		}
		return (int) (min / 1000);
	}

	private static String seconds(long millis) {
		return String.format("%.1fs", millis / 1000D);
	}
}
