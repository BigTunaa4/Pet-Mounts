package com.petmounts;

import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;

/** The outline around a set of points on screen (their convex hull). */
final class ScreenHull
{
	private ScreenHull()
	{
	}

	static Polygon of(List<Point> points)
	{
		List<Point> sorted = new ArrayList<>(points);
		sorted.sort((a, b) -> a.x != b.x ? Integer.compare(a.x, b.x) : Integer.compare(a.y, b.y));
		int n = sorted.size();
		Point[] hull = new Point[2 * n];
		int k = 0;
		for (int i = 0; i < n; i++)
		{
			while (k >= 2 && cross(hull[k - 2], hull[k - 1], sorted.get(i)) <= 0)
			{
				k--;
			}
			hull[k++] = sorted.get(i);
		}
		for (int i = n - 2, lower = k + 1; i >= 0; i--)
		{
			while (k >= lower && cross(hull[k - 2], hull[k - 1], sorted.get(i)) <= 0)
			{
				k--;
			}
			hull[k++] = sorted.get(i);
		}
		Polygon polygon = new Polygon();
		for (int i = 0; i < Math.max(0, k - 1); i++)
		{
			polygon.addPoint(hull[i].x, hull[i].y);
		}
		return polygon;
	}

	private static long cross(Point o, Point a, Point b)
	{
		return (long) (a.x - o.x) * (b.y - o.y) - (long) (a.y - o.y) * (b.x - o.x);
	}
}
