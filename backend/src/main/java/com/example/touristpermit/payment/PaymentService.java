package com.example.touristpermit.payment;

import com.example.touristpermit.application.PermitApplication;
import com.example.touristpermit.application.PermitApplicationRepository;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.exception.ApiException;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.notification.event.PaymentCompletedEvent;
import com.example.touristpermit.payment.dto.PaymentDtos.EsewaCheckout;
import com.example.touristpermit.payment.dto.PaymentDtos.FeeStatus;
import com.example.touristpermit.payment.dto.PaymentDtos.PaymentResponse;
import com.example.touristpermit.payment.dto.PaymentDtos.PaymentSummary;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.tourist.TouristService;
import com.example.touristpermit.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permit fee payments. Online payments use eSewa ePay v2: the browser posts a signed
 * form to eSewa; on return the callback signature is verified and the result is
 * re-confirmed through eSewa's server-to-server status API before the payment counts.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    static final String REQUEST_SIGNED_FIELDS = "total_amount,transaction_uuid,product_code";

    private final PaymentRepository payments;
    private final PermitApplicationRepository applications;
    private final TouristService touristService;
    private final UserRepository users;
    private final EsewaGateway esewa;
    private final PaymentProperties properties;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;
    private final Clock clock;
    private final String baseUrl;

    public PaymentService(PaymentRepository payments, PermitApplicationRepository applications,
                          TouristService touristService, UserRepository users, EsewaGateway esewa,
                          PaymentProperties properties, AuditService audit, ApplicationEventPublisher events,
                          ObjectMapper json, Clock clock, AppProperties appProperties) {
        this.payments = payments;
        this.applications = applications;
        this.touristService = touristService;
        this.users = users;
        this.esewa = esewa;
        this.properties = properties;
        this.audit = audit;
        this.events = events;
        this.json = json;
        this.clock = clock;
        this.baseUrl = appProperties.baseUrl().replaceAll("/+$", "");
    }

    // ---------------------------------------------------------------- tourist: eSewa

    @Transactional
    public EsewaCheckout startEsewa(Long applicationId) {
        if (!properties.esewaEnabled()) {
            throw new BusinessRuleException("Online payment is not available. Please pay at the permit office.");
        }
        PermitApplication app = ownApplication(applicationId);
        requirePayable(app);
        String uuid = "YP-" + app.getId() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Payment payment = payments.save(new Payment(app, PaymentProvider.ESEWA, app.getFeeAmount(), uuid, null));

        String amount = format(app.getFeeAmount());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("amount", amount);
        fields.put("tax_amount", "0");
        fields.put("total_amount", amount);
        fields.put("transaction_uuid", uuid);
        fields.put("product_code", properties.esewa().productCode());
        fields.put("product_service_charge", "0");
        fields.put("product_delivery_charge", "0");
        fields.put("success_url", baseUrl + "/tourist/payments/esewa/success");
        fields.put("failure_url", baseUrl + "/tourist/payments/esewa/failure?uuid=" + uuid);
        fields.put("signed_field_names", REQUEST_SIGNED_FIELDS);
        fields.put("signature", esewa.sign(fields, REQUEST_SIGNED_FIELDS));

        audit.record(AuditAction.PAYMENT_STARTED, "Payment", payment.getId(),
                Map.of("applicationNumber", app.getApplicationNumber(), "amount", amount, "provider", "ESEWA"));
        return new EsewaCheckout(properties.esewa().formUrl(), fields, uuid);
    }

    /**
     * Handles eSewa's success redirect: verifies the HMAC signature on the returned data,
     * checks it matches our pending payment, then confirms with eSewa's status API.
     */
    @Transactional
    public PaymentResponse confirmEsewa(String data) {
        JsonNode body = decode(data);
        String signedFields = body.path("signed_field_names").asText("");
        if (signedFields.isBlank() || !body.hasNonNull("signature")) {
            throw invalidResponse();
        }
        Map<String, String> values = new HashMap<>();
        for (String name : signedFields.split(",")) {
            JsonNode node = body.get(name.trim());
            if (node == null || node.isNull()) {
                throw invalidResponse();
            }
            values.put(name.trim(), node.asText());
        }
        String expected = esewa.sign(values, signedFields);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                body.get("signature").asText().getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected eSewa callback with an invalid signature");
            throw invalidResponse();
        }
        if (!properties.esewa().productCode().equals(body.path("product_code").asText())) {
            throw invalidResponse();
        }
        Payment payment = ownPaymentForUpdate(body.path("transaction_uuid").asText());
        if (parseAmount(body.path("total_amount").asText()).compareTo(payment.getAmount()) != 0) {
            log.warn("eSewa callback amount mismatch for payment {}", payment.getId());
            throw invalidResponse();
        }
        return PaymentResponse.of(settle(payment), false);
    }

    /** Re-checks a payment with eSewa (e.g. after the failure redirect or a "refresh" click). */
    @Transactional
    public PaymentResponse checkOwn(String transactionUuid) {
        return PaymentResponse.of(settle(ownPaymentForUpdate(transactionUuid)), false);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listOwn(Long applicationId) {
        PermitApplication app = ownApplication(applicationId);
        return payments.findByApplicationIdOrderByCreatedAtDesc(app.getId()).stream()
                .map(p -> PaymentResponse.of(p, false)).toList();
    }

    // ---------------------------------------------------------------- administrator

    /** Records a fee paid in person at a permit counter. */
    @Transactional
    public PaymentResponse recordOffline(Long applicationId, String reference) {
        PermitApplication app = applications.findForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        requirePayable(app);
        Payment payment = payments.save(new Payment(app, PaymentProvider.OFFLINE, app.getFeeAmount(),
                "OFF-" + app.getId() + "-" + UUID.randomUUID().toString().substring(0, 8),
                users.getReferenceById(CurrentUser.require().id())));
        complete(payment, reference.trim());
        return PaymentResponse.of(payment, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(PaymentStatus status, PaymentProvider provider, Pageable pageable) {
        Specification<Payment> spec = (root, q, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (provider != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("provider"), provider));
        }
        return PageResponse.of(payments.findAll(spec, pageable), p -> PaymentResponse.of(p, true));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForApplication(Long applicationId) {
        return payments.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(p -> PaymentResponse.of(p, true)).toList();
    }

    // ---------------------------------------------------------------- rules & summaries

    /** True when approval must wait for the fee (fee > 0, online payment enabled, not yet paid). */
    @Transactional(readOnly = true)
    public boolean isFeeOutstanding(PermitApplication app) {
        return app.requiresPayment() && properties.esewaEnabled()
                && !payments.existsByApplicationIdAndStatus(app.getId(), PaymentStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public PaymentSummary summaryFor(PermitApplication app) {
        return summarize(app, payments.findByApplicationIdOrderByCreatedAtDesc(app.getId()));
    }

    /** Batch version for list pages (one query). */
    @Transactional(readOnly = true)
    public Map<Long, PaymentSummary> summariesFor(Collection<PermitApplication> apps) {
        if (apps.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Payment>> byApp = payments.findByApplicationIds(apps.stream().map(PermitApplication::getId).toList())
                .stream().collect(Collectors.groupingBy(p -> p.getApplication().getId()));
        return apps.stream().collect(Collectors.toMap(PermitApplication::getId,
                a -> summarize(a, byApp.getOrDefault(a.getId(), List.of())), (x, y) -> x));
    }

    private PaymentSummary summarize(PermitApplication app, List<Payment> list) {
        boolean required = app.requiresPayment() && properties.esewaEnabled();
        if (!app.requiresPayment()) {
            return new PaymentSummary(FeeStatus.NOT_REQUIRED, BigDecimal.ZERO, "NPR", null, null, null, false);
        }
        Map<PaymentStatus, Payment> latest = list.stream()
                .collect(Collectors.toMap(Payment::getStatus, Function.identity(), (first, second) -> first));
        Payment paid = latest.get(PaymentStatus.COMPLETED);
        if (paid != null) {
            return new PaymentSummary(FeeStatus.PAID, paid.getAmount(), paid.getCurrency(), paid.getProvider(),
                    paid.getProviderRef(), paid.getCompletedAt(), required);
        }
        FeeStatus status = latest.containsKey(PaymentStatus.PENDING) ? FeeStatus.PENDING : FeeStatus.UNPAID;
        return new PaymentSummary(status, app.getFeeAmount(), "NPR", null, null, null, required);
    }

    // ---------------------------------------------------------------- internals

    /** Asks eSewa for the outcome and applies it. Package-visible for the reconciliation job. */
    Payment settle(Payment payment) {
        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getProvider() != PaymentProvider.ESEWA) {
            return payment;
        }
        EsewaGateway.Status status = esewa.checkStatus(format(payment.getAmount()), payment.getTransactionUuid());
        if (status.isComplete()) {
            complete(payment, status.refId());
        } else if (status.isAbandoned() && payment.getStatus() == PaymentStatus.PENDING) {
            payment.close(PaymentStatus.CANCELLED, "Payment was not completed");
        }
        return payment;
    }

    private void complete(Payment payment, String reference) {
        if (payments.existsByApplicationIdAndStatus(payment.getApplication().getId(), PaymentStatus.COMPLETED)) {
            // The tourist paid twice (e.g. two browser tabs). Keep a record so staff can refund it.
            payment.close(PaymentStatus.FAILED, "Duplicate payment - refund required (ref " + reference + ")");
            audit.record(AuditAction.PAYMENT_FAILED, "Payment", payment.getId(), Map.of("reason", "duplicate"));
            log.warn("Duplicate payment {} for application {}", payment.getId(), payment.getApplication().getId());
            return;
        }
        payment.complete(reference, clock.instant());
        payments.saveAndFlush(payment);
        audit.record(AuditAction.PAYMENT_COMPLETED, "Payment", payment.getId(), Map.of(
                "applicationNumber", payment.getApplication().getApplicationNumber(),
                "amount", format(payment.getAmount()), "provider", payment.getProvider().name()));
        events.publishEvent(new PaymentCompletedEvent(payment.getId()));
    }

    private void requirePayable(PermitApplication app) {
        if (!app.getStatus().isOpen()) {
            throw new ConflictException("Fees can only be paid while the application is pending or under review");
        }
        if (!app.requiresPayment()) {
            throw new BusinessRuleException("This application has no fee");
        }
        if (payments.existsByApplicationIdAndStatus(app.getId(), PaymentStatus.COMPLETED)) {
            throw new ConflictException("The fee for this application has already been paid");
        }
    }

    private PermitApplication ownApplication(Long applicationId) {
        Long touristId = touristService.currentProfile().getId();
        return applications.findByIdAndTouristId(applicationId, touristId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }

    private Payment ownPaymentForUpdate(String uuid) {
        Long touristId = touristService.currentProfile().getId();
        return payments.findByTransactionUuidForUpdate(uuid == null ? "" : uuid)
                .filter(p -> p.getApplication().getTourist().getId().equals(touristId))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private JsonNode decode(String data) {
        try {
            return json.readTree(Base64.getDecoder().decode(data.trim()));
        } catch (Exception e) {
            throw invalidResponse();
        }
    }

    /** eSewa formats amounts like "1,000.0"; compare numerically. */
    static BigDecimal parseAmount(String text) {
        try {
            return new BigDecimal(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw invalidResponse();
        }
    }

    /** Amount as sent to eSewa (no trailing zeros): 1500.00 -> "1500", 10.50 -> "10.5". */
    static String format(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static ApiException invalidResponse() {
        return new ApiException(HttpStatus.BAD_REQUEST, "The payment response from eSewa is not valid");
    }
}
