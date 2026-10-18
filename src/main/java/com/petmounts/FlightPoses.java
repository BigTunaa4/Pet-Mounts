package com.petmounts;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Model;

/**
 * Flying poses for winged mounts the game never flies (the gryphons and Vorkath). Each was made from the creature's
 * own animations: its body as it stands, its wings from the attack where it beats them, and its legs from a pose
 * where they're folded or trailing, so it looks like the game's own creature in flight. One full wing beat is
 * stored per mount (flight/&lt;npc id&gt;.bin.gz: vertex count, frame count, then x, y, z per vertex per frame) and
 * played in place of the mount's shape while it's in the air.
 */
@Slf4j
final class FlightPoses
{
	private static final Map<Integer, FlightPoses> CACHE = new HashMap<>();

	final int vertexCount;
	final int frameCount;
	/** x, y, z of every vertex, frame after frame. */
	private final short[] data;

	private FlightPoses(int vertexCount, int frameCount, short[] data)
	{
		this.vertexCount = vertexCount;
		this.frameCount = frameCount;
		this.data = data;
	}

	/** The flying poses for this mount, or null if it has none. */
	static synchronized FlightPoses forNpc(int npcId)
	{
		if (CACHE.containsKey(npcId))
		{
			return CACHE.get(npcId);
		}
		FlightPoses poses = null;
		try (InputStream in = FlightPoses.class.getResourceAsStream("flight/" + npcId + ".bin.gz"))
		{
			if (in != null)
			{
				DataInputStream d = new DataInputStream(new GZIPInputStream(in));
				int vc = d.readInt(), fc = d.readInt();
				short[] data = new short[vc * fc * 3];
				for (int i = 0; i < data.length; i++)
				{
					data[i] = d.readShort();
				}
				poses = new FlightPoses(vc, fc, data);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Couldn't read the flying poses for npc {}", npcId, e);
		}
		CACHE.put(npcId, poses);
		return poses;
	}

	static boolean has(int npcId)
	{
		return FlightPoses.class.getResource("flight/" + npcId + ".bin.gz") != null;
	}

	/**
	 * Shapes the model as the mount in flight at this point of its wing beat (0 to 1, looping), blending between
	 * the stored frames. The model must be a fresh animated copy at the creature's own size.
	 */
	void apply(Model model, float phase)
	{
		if (model.getVerticesCount() != vertexCount)
		{
			return;
		}
		float f = ((phase % 1f) + 1f) % 1f * frameCount;
		int a = (int) f % frameCount, b = (a + 1) % frameCount;
		float t = f - (int) f;
		float[] xs = model.getVerticesX(), ys = model.getVerticesY(), zs = model.getVerticesZ();
		int oa = a * vertexCount * 3, ob = b * vertexCount * 3;
		for (int v = 0; v < vertexCount; v++)
		{
			int i = v * 3;
			xs[v] = data[oa + i] + (data[ob + i] - data[oa + i]) * t;
			ys[v] = data[oa + i + 1] + (data[ob + i + 1] - data[oa + i + 1]) * t;
			zs[v] = data[oa + i + 2] + (data[ob + i + 2] - data[oa + i + 2]) * t;
		}
	}
}
