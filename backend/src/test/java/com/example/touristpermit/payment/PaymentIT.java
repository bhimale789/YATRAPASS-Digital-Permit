package com.example.touristpermit.payment;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.area.PermitAreaRepository;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditLogRepository;
import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payments.esewa.enabled=true",
        "app.payments.esewa.secret-key=" + PaymentIT.SECRET,
        "app.payments.esewa.product-code=EPAYTEST"
})
class PaymentIT extends AbstractWebIntegrationTest {

    static final String SECRET = "8gBm/:&EnhH.1/q";

    @MockitoSpyBean
    EsewaGateway gateway;
    @Autowired
    PermitAreaRepository areas;
    @Autowired
    PaymentRepository payments;
    @Autowired
    PaymentReconciliationJob reconciliation;
    @Autowired
    AuditLogRepository auditLogs;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void approvalWaitsForFeeAndCounterPaymentUnblocksIt() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long appId = paidAreaApplication(t, "1500.00");

        mvc.perform(get("/api/v1/applications/" + appId).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.feeAmount").value(1500.0))
                .andExpect(jsonPath("$.payment.status").value("UNPAID"))
                .andExpect(jsonPath("$.payment.requiredForApproval").value(true));

        mvc.perform(post("/api/v1/admin/applications/" + appId + "/approve").header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("The permit fee has not been paid")));

        mvc.perform(post("/api/v1/admin/applications/" + appId + "/payments/offline").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"Receipt 42\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.provider").value("OFFLINE"))
                .andExpect(jsonPath("$.reference").value("Receipt 42"));

        // A second payment for the same application is refused
        mvc.perform(post("/api/v1/admin/applications/" + appId + "/payments/offline").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"Receipt 43\"}"))
                .andExpect(status().isConflict());

        approve(admin, appId);
        mvc.perform(get("/api/v1/admin/applications/" + appId).header("Authorization", admin))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.provider").value("OFFLINE"));
    }

    @Test
    void esewaCheckoutIsSignedAndConfirmedPaymentCompletes() throws Exception {
        TestUser t = testUsers.tourist();
        long appId = paidAreaApplication(t, "1500.00");

        JsonNode checkout = startEsewa(t, appId);
        JsonNode fields = checkout.get("fields");
        String uuid = checkout.get("transactionUuid").asText();
        assertThat(checkout.get("formUrl").asText()).contains("esewa.com.np");
        assertThat(fields.get("total_amount").asText()).isEqualTo("1500");
        assertThat(fields.get("product_code").asText()).isEqualTo("EPAYTEST");
        assertThat(fields.get("success_url").asText()).isEqualTo("http://test.local/tourist/payments/esewa/success");
        assertThat(fields.get("signature").asText())
                .isEqualTo(hmac("total_amount=1500,transaction_uuid=" + uuid + ",product_code=EPAYTEST"));

        mvc.perform(get("/api/v1/applications/" + appId).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.payment.status").value("PENDING"));

        doReturn(new EsewaGateway.Status("COMPLETE", "000AWEO")).when(gateway).checkStatus("1500", uuid);
        mvc.perform(post("/api/v1/payments/esewa/confirm").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("data", callback(uuid, "1,500.0", "COMPLETE")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reference").value("000AWEO"));

        mvc.perform(get("/api/v1/applications/" + appId).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.provider").value("ESEWA"));
        Long paymentId = payments.findByTransactionUuid(uuid).orElseThrow().getId();
        assertThat(auditLogs.findByActionAndEntityId(AuditAction.PAYMENT_COMPLETED, paymentId.toString())).hasSize(1);

        // Already paid: no new checkout; confirming again is idempotent
        mvc.perform(post("/api/v1/applications/" + appId + "/payments/esewa").header("Authorization", t.bearer()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/payments/esewa/confirm").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("data", callback(uuid, "1500.0", "COMPLETE")))))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        approve(testUsers.admin().bearer(), appId);
    }

    @Test
    void forgedOrMismatchedCallbacksAreRejected() throws Exception {
        TestUser t = testUsers.tourist();
        long appId = paidAreaApplication(t, "1500.00");
        String uuid = startEsewa(t, appId).get("transactionUuid").asText();

        // Signature computed with the wrong key
        Map<String, String> forged = callbackFields(uuid, "1500.0", "COMPLETE");
        forged.put("signature", "AAAA" + forged.get("signature").substring(4));
        confirm(t, encode(forged)).andExpect(status().isBadRequest());

        // Correctly signed, but for a smaller amount
        confirm(t, callback(uuid, "10.0", "COMPLETE")).andExpect(status().isBadRequest());

        // Not base64 JSON
        confirm(t, "not-base64!").andExpect(status().isBadRequest());

        // Another tourist cannot confirm this payment
        confirm(testUsers.tourist(), callback(uuid, "1500.0", "COMPLETE")).andExpect(status().isNotFound());

        assertThat(payments.findByTransactionUuid(uuid).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void callbackIsNotTrustedWithoutGatewayConfirmation() throws Exception {
        TestUser t = testUsers.tourist();
        long appId = paidAreaApplication(t, "800.00");
        String uuid = startEsewa(t, appId).get("transactionUuid").asText();

        doReturn(new EsewaGateway.Status("PENDING", null)).when(gateway).checkStatus("800", uuid);
        confirm(t, callback(uuid, "800.0", "COMPLETE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        doReturn(new EsewaGateway.Status("CANCELED", null)).when(gateway).checkStatus("800", uuid);
        mvc.perform(post("/api/v1/payments/" + uuid + "/check").header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/v1/applications/" + appId).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.payment.status").value("UNPAID"));
    }

    @Test
    void reconciliationCompletesPaymentsTheTouristNeverReturnedFrom() throws Exception {
        TestUser t = testUsers.tourist();
        long appId = paidAreaApplication(t, "2000.00");
        String uuid = startEsewa(t, appId).get("transactionUuid").asText();
        jdbc.update("UPDATE payments SET created_at = now() - interval '1 hour' WHERE transaction_uuid = ?", uuid);

        doReturn(new EsewaGateway.Status("COMPLETE", "REF-RECON")).when(gateway).checkStatus(eq("2000"), anyString());
        reconciliation.run();

        Payment payment = payments.findByTransactionUuid(uuid).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getProviderRef()).isEqualTo("REF-RECON");

        mvc.perform(get("/api/v1/admin/payments").param("status", "COMPLETED")
                        .header("Authorization", testUsers.admin().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.transactionUuid == '" + uuid + "')].touristName").exists());
    }

    @Test
    void freeAreasNeedNoPayment() throws Exception {
        TestUser t = testUsers.tourist();
        long appId = submitApplication(t, testAreas.active(10), today().plusDays(1), today().plusDays(2));
        uploadPassport(t, appId);

        mvc.perform(get("/api/v1/applications/" + appId).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.payment.status").value("NOT_REQUIRED"));
        mvc.perform(post("/api/v1/applications/" + appId + "/payments/esewa").header("Authorization", t.bearer()))
                .andExpect(status().isUnprocessableEntity());
        approve(testUsers.admin().bearer(), appId);
    }

    @Test
    void paymentEndpointsAreRoleProtected() throws Exception {
        mvc.perform(get("/api/v1/admin/payments").header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payments/esewa/confirm").header("Authorization", testUsers.admin().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payments/esewa/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private long paidAreaApplication(TestUser t, String fee) throws Exception {
        PermitArea area = testAreas.active(10);
        area.setFee(new BigDecimal(fee));
        areas.save(area);
        long appId = submitApplication(t, area, today().plusDays(1), today().plusDays(3));
        uploadPassport(t, appId);
        return appId;
    }

    private JsonNode startEsewa(TestUser t, long appId) throws Exception {
        return body(mvc.perform(post("/api/v1/applications/" + appId + "/payments/esewa").header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions confirm(TestUser t, String data) throws Exception {
        return mvc.perform(post("/api/v1/payments/esewa/confirm").header("Authorization", t.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("data", data))));
    }

    /** What eSewa appends to the success URL, signed with the merchant secret. */
    private Map<String, String> callbackFields(String uuid, String totalAmount, String status) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("transaction_code", "000AWEO");
        f.put("status", status);
        f.put("total_amount", totalAmount);
        f.put("transaction_uuid", uuid);
        f.put("product_code", "EPAYTEST");
        f.put("signed_field_names", "transaction_code,status,total_amount,transaction_uuid,product_code,signed_field_names");
        f.put("signature", hmac("transaction_code=000AWEO,status=" + status + ",total_amount=" + totalAmount
                + ",transaction_uuid=" + uuid + ",product_code=EPAYTEST,signed_field_names=" + f.get("signed_field_names")));
        return f;
    }

    private String callback(String uuid, String totalAmount, String status) throws Exception {
        return encode(callbackFields(uuid, totalAmount, status));
    }

    private String encode(Map<String, String> fields) throws Exception {
        return Base64.getEncoder().encodeToString(json.writeValueAsBytes(fields));
    }

    static String hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
