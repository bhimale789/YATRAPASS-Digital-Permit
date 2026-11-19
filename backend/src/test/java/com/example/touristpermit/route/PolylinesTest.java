package com.example.touristpermit.route;

import com.example.touristpermit.road.Coordinate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolylinesTest {

    @Test
    void dropsCollinearPointsButKeepsEnds() {
        List<Coordinate> straight = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            straight.add(new Coordinate(27.0 + i * 0.001, 85.0));
        }
        assertThat(Polylines.simplify(straight, 15)).containsExactly(straight.get(0), straight.get(100));
    }

    @Test
    void keepsSignificantBends() {
        // An L-shaped path: the corner (~1 km off the straight line) must survive.
        List<Coordinate> path = List.of(
                new Coordinate(27.0, 85.0), new Coordinate(27.005, 85.0), new Coordinate(27.01, 85.0),
                new Coordinate(27.01, 85.005), new Coordinate(27.01, 85.01));
        assertThat(Polylines.simplify(path, 15))
                .containsExactly(new Coordinate(27.0, 85.0), new Coordinate(27.01, 85.0), new Coordinate(27.01, 85.01));
    }

    @Test
    void handlesLongRoutesWithoutRecursionLimits() {
        List<Coordinate> zigzag = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            zigzag.add(new Coordinate(27.0 + i * 0.00001, 85.0 + (i % 2 == 0 ? 0 : 0.001)));
        }
        List<Coordinate> result = Polylines.simplify(zigzag, 15);
        assertThat(result.get(0)).isEqualTo(zigzag.get(0));
        assertThat(result.get(result.size() - 1)).isEqualTo(zigzag.get(zigzag.size() - 1));
    }

    @Test
    void tinyInputsAreReturnedAsIs() {
        List<Coordinate> two = List.of(new Coordinate(1.0, 1.0), new Coordinate(2.0, 2.0));
        assertThat(Polylines.simplify(two, 15)).isEqualTo(two);
    }
}
