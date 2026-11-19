package com.example.touristpermit.common.util;

import com.example.touristpermit.common.exception.ApiException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * Restricts client-supplied sort properties to an explicit allow-list. Without this,
 * clients could sort by arbitrary (including joined, sensitive) attributes and infer
 * their values from result ordering.
 */
public final class SortWhitelist {

    private SortWhitelist() {
    }

    public static Pageable restrict(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported sort property: " + order.getProperty()
                        + ". Allowed: " + String.join(", ", allowed.stream().sorted().toList()));
            }
        }
        return pageable;
    }
}
