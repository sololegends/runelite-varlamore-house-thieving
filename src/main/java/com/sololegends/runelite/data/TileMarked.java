package com.sololegends.runelite.data;

public class TileMarked {
	int x, y, z;

	public TileMarked(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	@Override
	public int hashCode() {
		// Force equals use
		return 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TileMarked) {
			TileMarked tm = (TileMarked) obj;
			return tm.x == x && tm.y == y && tm.z == z;
		}
		return false;
	}
}