package com.example.touristpermit.notification;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.area.PermitAreaRepository;
import com.example.touristpermit.notification.channel.SmsSender;
import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.notifications.email.enabled=true",
        "app.notifications.email.from=YatraPass <no-reply@test.local>",
        "app.notifications.sms.provider=log",
        "app.notifications.max-attempts=3"
})
class NotificationIT extends AbstractWebIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;
    @MockitoBean
    SmsSender smsSender;
    @Autowired
    NotificationDispatcher dispatcher;
    @Autowired
    PermitExpiryReminderJob reminderJob;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    NotificationDeliveryRepository deliveries;
    @Autowired
    PermitAreaRepository areas;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void approvalNotifiesTouristInAppByEmailAndSms() throws Exception {
        TestUser t = testUsers.tourist();
        JsonNode permit = issuePermit(t, testUsers.admin().bearer(), today().plusDays(5), today().plusDays(9));

        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.count").value(1));
        JsonNode inbox = body(mvc.perform(get("/api/v1/notifications").header("Authorization", t.bearer()))
                .andExpect(status().isOk()).andReturn());
        JsonNode n = inbox.get("content").get(0);
        assertThat(n.get("type").asText()).isEqualTo("APPLICATION_APPROVED");
        assertThat(n.get("body").asText()).contains(permit.get("permitNumber").asText());
        assertThat(n.get("body").asText()).doesNotContain(t.profile().getPassportNumber());
        assertThat(n.get("link").asText()).isEqualTo("/tourist/permits");
        assertThat(n.get("read").asBoolean()).isFalse();

        assertThat(dispatcher.dispatch()).isGreaterThanOrEqualTo(2);

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mail.capture());
        SimpleMailMessage sent = mail.getAllValues().stream()
                .filter(m -> List.of(m.getTo()).contains(t.user().getEmail())).findFirst().orElseThrow();
        assertThat(sent.getSubject()).startsWith("YatraPass: Permit approved");
        assertThat(sent.getText()).contains("http://test.local/tourist/permits");
        verify(smsSender, atLeastOnce()).send(eq("+10000000000"), anyString());
        assertThat(deliveries.findByNotificationId(n.get("id").asLong()))
                .allMatch(d -> d.getStatus() == DeliveryStatus.SENT);

        // Mark read
        mvc.perform(post("/api/v1/notifications/" + n.get("id").asLong() + "/read").header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void rejectionAndCounterPaymentAreNotified() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        PermitArea area = testAreas.active(10);
        area.setFee(new BigDecimal("500"));
        areas.save(area);
        long appId = submitApplication(t, area, today().plusDays(1), today().plusDays(2));

        mvc.perform(post("/api/v1/admin/applications/" + appId + "/payments/offline").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"R-1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/applications/" + appId + "/reject").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Dates unavailable\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/notifications").header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("APPLICATION_REJECTED"))
                .andExpect(jsonPath("$.content[0].body").value(org.hamcrest.Matchers.containsString("Dates unavailable")))
                .andExpect(jsonPath("$.content[1].type").value("PAYMENT_RECEIVED"))
                .andExpect(jsonPath("$.content[1].body").value(org.hamcrest.Matchers.containsString("NPR 500")));

        mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.updated").value(2));
    }

    @Test
    void expiryRemindersAreSentOncePerThreshold() throws Exception {
        TestUser t = testUsers.tourist();
        issuePermit(t, testUsers.admin().bearer(), today(), today().plusDays(3));

        reminderJob.run();
        reminderJob.run(); // a second run (or a second instance) must not duplicate

        assertThat(notifications.findByUserIdAndType(t.user().getId(), NotificationType.PERMIT_EXPIRING))
                .singleElement()
                .satisfies(n -> assertThat(n.getTitle()).isEqualTo("Permit expires in 3 days"));
    }

    @Test
    void failedEmailIsRetriedWithBackoffThenMarkedFailed() throws Exception {
        TestUser t = testUsers.tourist();
        doThrow(new MailSendException("smtp down for someone@example.test")).when(mailSender)
                .send(any(SimpleMailMessage.class));
        issuePermit(t, testUsers.admin().bearer(), today().plusDays(1), today().plusDays(2));
        Long notificationId = notifications.findByUserIdAndType(t.user().getId(),
                NotificationType.APPLICATION_APPROVED).getFirst().getId();

        dispatcher.dispatch();
        NotificationDelivery email = emailDelivery(notificationId);
        assertThat(email.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(email.getAttempts()).isEqualTo(1);
        assertThat(email.getLastError()).isEqualTo("MailSendException"); // no address leaked
        assertThat(email.getNextAttemptAt()).isAfter(java.time.Instant.now());

        for (int i = 0; i < 2; i++) {
            jdbc.update("UPDATE notification_deliveries SET next_attempt_at = now() - interval '1 minute' "
                    + "WHERE notification_id = ? AND channel = 'EMAIL'", notificationId);
            dispatcher.dispatch();
        }
        assertThat(emailDelivery(notificationId).getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(emailDelivery(notificationId).getAttempts()).isEqualTo(3);
    }

    @Test
    void touristWithoutPhoneHasSmsSkipped() throws Exception {
        TestUser t = testUsers.tourist();
        long appId = submitApplication(t, testAreas.active(10), today().plusDays(1), today().plusDays(2));
        jdbc.update("UPDATE tourist_profiles SET phone = NULL WHERE user_id = ?", t.user().getId());
        mvc.perform(post("/api/v1/admin/applications/" + appId + "/reject").header("Authorization", testUsers.admin().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No\"}"))
                .andExpect(status().isOk());
        Long id = notifications.findByUserIdAndType(t.user().getId(), NotificationType.APPLICATION_REJECTED)
                .getFirst().getId();

        dispatcher.dispatch();
        assertThat(deliveries.findByNotificationId(id))
                .filteredOn(d -> d.getChannel() == DeliveryChannel.SMS)
                .singleElement()
                .satisfies(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SKIPPED));
    }

    @Test
    void inboxIsPrivateAndTouristOnly() throws Exception {
        TestUser owner = testUsers.tourist();
        long appId = submitApplication(owner, testAreas.active(10), today().plusDays(1), today().plusDays(2));
        mvc.perform(post("/api/v1/admin/applications/" + appId + "/reject").header("Authorization", testUsers.admin().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No\"}"))
                .andExpect(status().isOk());
        Long id = notifications.findByUserIdAndType(owner.user().getId(), NotificationType.APPLICATION_REJECTED)
                .getFirst().getId();

        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications").header("Authorization", testUsers.admin().bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    private NotificationDelivery emailDelivery(Long notificationId) {
        return deliveries.findByNotificationId(notificationId).stream()
                .filter(d -> d.getChannel() == DeliveryChannel.EMAIL).findFirst().orElseThrow();
    }
}
