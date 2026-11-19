package com.example.touristpermit.payment;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import com.example.touristpermit.payment.dto.PaymentDtos.OfflinePaymentRequest;
import com.example.touristpermit.payment.dto.PaymentDtos.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Payments", description = "Permit fee payments")
public class AdminPaymentController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "completedAt", "amount", "status");

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payments")
    @Operation(summary = "Search payments")
    public PageResponse<PaymentResponse> search(@RequestParam(required = false) PaymentStatus status,
                                                @RequestParam(required = false) PaymentProvider provider,
                                                @PageableDefault(size = 20, sort = "createdAt",
                                                        direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.search(status, provider, SortWhitelist.restrict(pageable, SORTABLE));
    }

    @GetMapping("/applications/{id}/payments")
    @Operation(summary = "Payments for an application")
    public List<PaymentResponse> forApplication(@PathVariable Long id) {
        return paymentService.listForApplication(id);
    }

    @PostMapping("/applications/{id}/payments/offline")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a fee paid in person", description = "Use the counter receipt number as reference.")
    public PaymentResponse recordOffline(@PathVariable Long id, @Valid @RequestBody OfflinePaymentRequest request) {
        return paymentService.recordOffline(id, request.reference());
    }
}
