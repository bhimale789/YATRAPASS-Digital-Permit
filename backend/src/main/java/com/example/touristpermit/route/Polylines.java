package com.example.touristpermit.route;

import com.example.touristpermit.road.Coordinate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Polyline helpers. */
final class Polylines {

    private static final double EARTH_RADIUS_M = 6_371_000;

    private Polylines() {
    }

    /**
     * Douglas–Peucker simplification (iterative, so long routes cannot overflow the stack).
     * Points closer than {@code toleranceMeters} to the simplified line are dropped; the
     * first and last points are always kept.
     */
    static List<Coordinate> simplify(List<Coordinate> points, double toleranceMeters) {
        int n = points.size();
        if (n <= 2) {
            return List.copyOf(points);
        }
        // Project to a local equirectangular plane (metres) — accurate enough at route scale.
        double refLat = Math.toRadians(points.get(0).lat());
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = Math.toRadians(points.get(i).lng()) * Math.cos(refLat) * EARTH_RADIUS_M;
            y[i] = Math.toRadians(points.get(i).lat()) * EARTH_RADIUS_M;
        }
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1});
        while (!stack.isEmpty()) {
            int[] range = stack.pop();
            int first = range[0];
            int last = range[1];
            double maxDist = -1;
            int index = -1;
            for (int i = first + 1; i < last; i++) {
                double d = segmentDistance(x[i], y[i], x[first], y[first], x[last], y[last]);
                if (d > maxDist) {
                    maxDist = d;
                    index = i;
                }
            }
            if (index >= 0 && maxDist > toleranceMeters) {
                keep[index] = true;
                stack.push(new int[]{first, index});
                stack.push(new int[]{index, last});
            }
        }
        List<Coordinate> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    private static double segmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        double t = lengthSq == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSq));
        double cx = ax + t * dx - px;
        double cy = ay + t * dy - py;
        return Math.sqrt(cx * cx + cy * cy);
    }
}
