package com.example.touristpermit.config;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.area.PermitAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * DEVELOPMENT ONLY. Seeds clearly-labelled SAMPLE areas when the {@code dev} profile is
 * active. These are not real Nepal permit areas, rules or fees.
 */
@Component
@Profile("dev")
public class DevSampleDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSampleDataInitializer.class);
    private static final String NOTE = "SAMPLE DATA for development only. Not a real permit area; "
            + "rules and fees are placeholders.";

    private final PermitAreaRepository areas;

    public DevSampleDataInitializer(PermitAreaRepository areas) {
        this.areas = areas;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Seed only an empty database, so areas an admin deleted or replaced do not reappear on restart.
        if (areas.count() > 0) {
            return;
        }
        seed("Sample Restricted Area", "SAMPLE-RA", "Sample District A", "Sample Province 1", 10, "0.00");
        seed("Sample Highland Zone", "SAMPLE-HZ", "Sample District B", "Sample Province 2", 14, "0.00");
        seed("Sample Conservation Area", "SAMPLE-CA", "Sample District C", "Sample Province 3", 30, "0.00");
    }

    private void seed(String name, String code, String district, String province, int maxDays, String fee) {
        if (areas.existsByCode(code)) {
            return;
        }
        PermitArea area = new PermitArea(name, code, maxDays);
        area.setDescription(NOTE);
        area.setDistrict(district);
        area.setProvince(province);
        area.setFee(new BigDecimal(fee));
        areas.save(area);
        log.info("Seeded SAMPLE area {}", code);
    }
}
