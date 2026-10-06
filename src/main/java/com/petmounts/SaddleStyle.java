package com.petmounts;

/** Saddle and blanket designs to choose from. Colours are the game's own HSL colours. */
public enum SaddleStyle
{
	CLASSIC("Classic", -1, -1, SaddleMesh.LEATHER, SaddleMesh.LEATHER_DARK, SaddleMesh.STEEL),
	LEATHER("Plain leather", SaddleMesh.hsl(6, 3, 24), SaddleMesh.hsl(7, 4, 50), SaddleMesh.hsl(6, 4, 34),
		SaddleMesh.hsl(6, 3, 18), SaddleMesh.hsl(6, 5, 52)),
	ROYAL("Royal", SaddleMesh.GOLD, SaddleMesh.hsl(50, 6, 34), SaddleMesh.hsl(1, 6, 26),
		SaddleMesh.hsl(1, 5, 14), SaddleMesh.GOLD),
	SKULL("Skull and bones", SaddleMesh.hsl(8, 2, 104), SaddleMesh.hsl(0, 0, 14), SaddleMesh.hsl(0, 0, 20),
		SaddleMesh.hsl(0, 0, 8), SaddleMesh.hsl(8, 2, 92)),
	HOLIDAY("Holiday", SaddleMesh.hsl(0, 0, 112), SaddleMesh.hsl(0, 7, 38), SaddleMesh.hsl(21, 6, 26),
		SaddleMesh.hsl(21, 5, 14), SaddleMesh.GOLD);

	private final String name;
	/** Blanket colour (the strip under the seat), or -1 to match the pet. */
	final short blanket;
	/** Blanket edge and horn colour (most of what shows down the sides), or -1 for gold or silver to suit the pet. */
	final short trim;
	final short leather;
	final short leatherDark;
	final short metal;

	SaddleStyle(String name, int blanket, int trim, short leather, short leatherDark, short metal)
	{
		this.name = name;
		this.blanket = (short) blanket;
		this.trim = (short) trim;
		this.leather = leather;
		this.leatherDark = leatherDark;
		this.metal = metal;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
