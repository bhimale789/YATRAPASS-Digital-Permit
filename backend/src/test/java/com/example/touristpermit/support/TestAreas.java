package com.example.touristpermit.support;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.area.PermitAreaRepository;
import org.springframework.boot.test.context.TestComponent;

import java.util.UUID;

@TestComponent
public class TestAreas {

    private final PermitAreaRepository areas;

    public TestAreas(PermitAreaRepository areas) {
        this.areas = areas;
    }

    public PermitArea active(int maxDays) {
        PermitArea a = new PermitArea("Test Area", "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                maxDays);
        return areas.save(a);
    }

    public PermitArea inactive() {
        PermitArea a = active(10);
        a.setActive(false);
        return areas.save(a);
    }
}
