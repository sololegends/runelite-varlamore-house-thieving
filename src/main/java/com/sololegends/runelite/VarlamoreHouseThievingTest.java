package com.sololegends.runelite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class VarlamoreHouseThievingTest {
	public static void main(String[] args) throws Exception {
		ExternalPluginManager.loadBuiltin(VarlamoreHouseThievingPlugin.class);
		RuneLite.main(args);
	}
}