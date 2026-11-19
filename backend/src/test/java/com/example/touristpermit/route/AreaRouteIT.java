package com.example.touristpermit.route;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.area.PermitAreaRepository;
import com.example.touristpermit.road.Coordinate;
import com.example.touristpermit.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AreaRouteIT extends AbstractWebIntegrationTest {

    @MockitoBean
    RouteProvider provider;

    @Autowired
    PermitAreaRepository areas;

    static final RouteResult ROUTE = new RouteResult(412300, 40500,
            List.of(new Coordinate(27.7172, 85.324), new Coordinate(28.2, 84.0), new Coordinate(28.78, 83.72)));

    private PermitArea areaWithLocation(double lat, double lng) {
        PermitArea a = testAreas.active(10);
        a.setLocation(BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
        return areas.save(a);
    }

    @Test
    void routeFromDefaultOrigin() throws Exception {
        PermitArea area = areaWithLocation(28.7804, 83.7230);
        when(provider.route(any(), any())).thenReturn(Optional.of(ROUTE));

        mvc.perform(get("/api/v1/areas/" + area.getId() + "/route").header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from.label").value("Pokhara"))
                .andExpect(jsonPath("$.to.label").value(area.getName()))
                .andExpect(jsonPath("$.distanceMeters").value(412300.0))
                .andExpect(jsonPath("$.coordinates.length()").value(3))
                .andExpect(jsonPath("$.attribution").exists());
        verify(provider).route(eq(new Coordinate(28.2096, 83.9856)), eq(new Coordinate(28.7804, 83.7230)));
    }

    @Test
    void routeFromCustomStartAndCaching() throws Exception {
        PermitArea area = areaWithLocation(28.1, 84.1);
        when(provider.route(any(), any())).thenReturn(Optional.of(ROUTE));
        String tourist = testUsers.tourist().bearer();

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/v1/areas/" + area.getId() + "/route").param("fromLat", "27.7172").param("fromLng", "85.324")
                            .header("Authorization", tourist))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from.label").value("Your location"));
        }
        verify(provider, times(1)).route(eq(new Coordinate(27.7172, 85.324)), any());
    }

    @Test
    void destinationWithoutLocationIs422() throws Exception {
        long id = testAreas.active(10).getId();
        mvc.perform(get("/api/v1/areas/" + id + "/route").header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void noRouteIs422AndOutageIs503() throws Exception {
        PermitArea a = areaWithLocation(10.0, 10.0);
        PermitArea b = areaWithLocation(11.0, 11.0);
        when(provider.route(any(), eq(new Coordinate(10.0, 10.0)))).thenReturn(Optional.empty());
        when(provider.route(any(), eq(new Coordinate(11.0, 11.0)))).thenThrow(new RouteUnavailableException());
        String tourist = testUsers.tourist().bearer();

        mvc.perform(get("/api/v1/areas/" + a.getId() + "/route").header("Authorization", tourist))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("No driving route was found to this destination"));
        mvc.perform(get("/api/v1/areas/" + b.getId() + "/route").header("Authorization", tourist))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void validationAndAuth() throws Exception {
        PermitArea area = areaWithLocation(28.1, 84.1);
        mvc.perform(get("/api/v1/areas/" + area.getId() + "/route")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/areas/" + area.getId() + "/route").param("fromLat", "28.2")
                        .header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/areas/" + area.getId() + "/route").param("fromLat", "128").param("fromLng", "84")
                        .header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminSetsDestinationLocation() throws Exception {
        PermitArea area = testAreas.active(10);
        String admin = testUsers.admin().bearer();
        Map<String, Object> body = new java.util.HashMap<>(Map.of("name", area.getName(), "code", area.getCode(),
                "permitRequired", true, "maximumDays", 10, "fee", "0.00", "active", true,
                "latitude", 28.7804, "longitude", 83.7230));
        mvc.perform(put("/api/v1/admin/areas/" + area.getId()).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(28.7804));

        body.remove("longitude");
        mvc.perform(put("/api/v1/admin/areas/" + area.getId()).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isBadRequest());
    }
}
