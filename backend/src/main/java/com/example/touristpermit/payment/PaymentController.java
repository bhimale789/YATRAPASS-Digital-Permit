package com.example.touristpermit.payment;

import com.example.touristpermit.payment.dto.PaymentDtos.EsewaCheckout;
import com.example.touristpermit.payment.dto.PaymentDtos.EsewaConfirmRequest;
import com.example.touristpermit.payment.dto.PaymentDtos.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('TOURIST')")
@Tag(name = "Tourist: Payments", description = "Pay permit fees online with eSewa")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/applications/{id}/payments/esewa")
    @Operation(summary = "Start an eSewa payment",
            description = "Returns signed form fields; the browser must POST them to formUrl.")
    public ResponseEntity<EsewaCheckout> startEsewa(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(paymentService.startEsewa(id));
    }

    @GetMapping("/applications/{id}/payments")
    @Operation(summary = "Payments made for one of my applications")
    public List<PaymentResponse> list(@PathVariable Long id) {
        return paymentService.listOwn(id);
    }

    @PostMapping("/payments/esewa/confirm")
    @Operation(summary = "Confirm an eSewa payment",
            description = "Send the 'data' parameter from eSewa's success redirect. The result is verified "
                    + "with eSewa before the payment is accepted.")
    public PaymentResponse confirm(@Valid @RequestBody EsewaConfirmRequest request) {
        return paymentService.confirmEsewa(request.data());
    }

    @PostMapping("/payments/{transactionUuid}/check")
    @Operation(summary = "Re-check a payment's status with eSewa")
    public PaymentResponse check(@PathVariable String transactionUuid) {
        return paymentService.checkOwn(transactionUuid);
    }
}
