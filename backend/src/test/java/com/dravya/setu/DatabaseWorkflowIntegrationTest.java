package com.dravya.setu;

import com.dravya.setu.auth.*;
import com.dravya.setu.chatbot.*;
import com.dravya.setu.common.ClientErrorException;
import com.dravya.setu.identification.*;
import com.dravya.setu.mail.EmailSender;
import com.dravya.setu.mail.EmailDeliveryException;
import com.dravya.setu.plants.PlantRepository;
import com.dravya.setu.users.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFlywayConfig.class)
class DatabaseWorkflowIntegrationTest {
    @Autowired AuthService auth;
    @Autowired OtpService otp;
    @Autowired OtpHasher hasher;
    @Autowired PendingRegistrationRepository pending;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatService chat;
    @Autowired AuthAuditService audit;
    @Autowired PlatformTransactionManager transactions;
    @Autowired IdentificationRepository identifications;
    @Autowired IdentificationService identificationService;
    @Autowired PlantRepository plants;
    @Autowired com.dravya.setu.security.JwtService jwt;
    @MockitoBean EmailSender email;

    private String address() { return UUID.randomUUID() + "@example.test"; }
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1"); return request;
    }
    private User user() {
        User user = new User(); user.setEmail(address()); user.setRole("USER");
        user.setPasswordHash("test-only"); user.setEmailVerified(true);
        user.setEmailVerifiedAt(OffsetDateTime.now()); return users.saveAndFlush(user);
    }
    private void register(String address) {
        RegisterRequest request = new RegisterRequest(); request.setEmail(address); request.setPassword("password123");
        auth.register(request, request());
    }
    private void verifyCode(String address, String code) {
        VerifyEmailRequest request = new VerifyEmailRequest(); request.setEmail(address); request.setOtp(code);
        auth.verifyEmail(request, request());
    }
    private void resend(String address) {
        ResendVerificationRequest request = new ResendVerificationRequest(); request.setEmail(address);
        auth.resendVerification(request);
    }
    private String code(String address) {
        var html = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).send(eq(address), anyString(), html.capture());
        var matcher = java.util.regex.Pattern.compile("\\b\\d{6}\\b").matcher(html.getValue());
        assertTrue(matcher.find()); return matcher.group();
    }
    private void concurrent(int count, java.util.function.IntConsumer action) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count); var start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try { if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout"); }
                    catch (InterruptedException e) { throw new RuntimeException(e); }
                    action.accept(index);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    @Test void replacesOtpAndSerializesConcurrentIssuance() throws Exception {
        User user = user(); var first = otp.issue(user, OtpPurpose.PASSWORD_RESET).orElseThrow();
        jdbc.update("update email_otps set last_sent_at=now()-interval '2 minutes' where user_id=?", user.getId());
        var issued = new java.util.concurrent.atomic.AtomicInteger();
        concurrent(4, index -> { if (otp.issue(user, OtpPurpose.PASSWORD_RESET).isPresent()) issued.incrementAndGet(); });
        assertEquals(1, issued.get());
        assertEquals(1, jdbc.queryForObject("select count(*) from email_otps where user_id=? and consumed_at is null", Integer.class, user.getId()));
        assertNotEquals(OtpService.VerificationStatus.VERIFIED, otp.verify(user.getId(), OtpPurpose.PASSWORD_RESET, first.code()).status());
    }

    @Test void resetTicketIsSingleUseUnderConcurrentRedemption() throws Exception {
        User user = user(); var issued = otp.issue(user, OtpPurpose.PASSWORD_RESET).orElseThrow();
        VerifyResetOtpRequest verify = new VerifyResetOtpRequest();
        verify.setEmail(user.getEmail()); verify.setOtp(issued.code());
        String ticket = auth.verifyResetOtp(verify, request()).resetToken();
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        concurrent(4, index -> {
            ResetPasswordRequest reset = new ResetPasswordRequest(); reset.setResetToken(ticket);
            reset.setPassword("changed123" + index); reset.setConfirmPassword(reset.getPassword());
            try { auth.resetPassword(reset, request()); successes.incrementAndGet(); }
            catch (ClientErrorException expected) { assertEquals("INVALID_REQUEST", expected.getErrorCode()); }
        });
        assertEquals(1, successes.get());
        assertEquals(1, users.findById(user.getId()).orElseThrow().getTokenVersion());
    }

    @Test void failedTicketWriteRollsBackOtpConsumptionAndWrongAttemptsCommit() {
        User user = user(); var issued = otp.issue(user, OtpPurpose.PASSWORD_RESET).orElseThrow();
        VerifyResetOtpRequest verify = new VerifyResetOtpRequest(); verify.setEmail(user.getEmail());
        verify.setOtp(issued.code().equals("000000") ? "111111" : "000000");
        assertThrows(ClientErrorException.class, () -> auth.verifyResetOtp(verify, request()));
        assertEquals(1, jdbc.queryForObject("select attempts from email_otps where user_id=? and consumed_at is null", Integer.class, user.getId()));
        jdbc.execute("CREATE FUNCTION reject_test_ticket() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test failure''; END'");
        jdbc.execute("CREATE TRIGGER reject_test_ticket BEFORE INSERT ON password_reset_tokens FOR EACH ROW EXECUTE FUNCTION reject_test_ticket()");
        verify.setOtp(issued.code());
        try {
            assertThrows(RuntimeException.class, () -> auth.verifyResetOtp(verify, request()));
            assertEquals(1, jdbc.queryForObject("select count(*) from email_otps where user_id=? and consumed_at is null", Integer.class, user.getId()));
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_ticket ON password_reset_tokens");
            jdbc.execute("DROP FUNCTION reject_test_ticket()");
        }
        assertNotNull(auth.verifyResetOtp(verify, request()).resetToken());
    }

    @Test void duplicateConcurrentLogoutIsIdempotent() throws Exception {
        User user = user(); String token = jwt.generateToken(user);
        concurrent(4, index -> auth.logout(token, request()));
        assertEquals(1, jdbc.queryForObject("select count(*) from revoked_tokens where jti=?", Integer.class, jwt.parse(token).orElseThrow().jti()));
    }

    @Test void serializesRegistrationAndVerification() throws Exception {
        String address = address();
        concurrent(4, index -> register(address));
        verify(email, times(1)).send(eq(address), anyString(), anyString());
        String code = code(address);
        concurrent(4, index -> verifyCode(address, code));
        assertTrue(users.findByEmail(address).orElseThrow().isEmailVerified());
        assertNotNull(pending.findByEmail(address).orElseThrow().getConsumedAt());
        assertEquals(1, jdbc.queryForObject("select count(*) from users where email=?", Integer.class, address));
        assertEquals(1, jdbc.queryForObject("select count(*) from auth_audit_log where email=? and event='EMAIL_VERIFIED'", Integer.class, address));
    }

    @Test void concurrentWrongAttemptsCommitAndResendReopensBurnedRegistration() throws Exception {
        String address = address(); register(address);
        String valid = code(address); String wrong = valid.equals("000000") ? "111111" : "000000";
        concurrent(5, index -> assertThrows(ClientErrorException.class, () -> verifyCode(address, wrong)));
        assertEquals(5, pending.findByEmail(address).orElseThrow().getAttempts());
        assertNotNull(pending.findByEmail(address).orElseThrow().getConsumedAt());
        jdbc.update("update pending_registrations set last_sent_at=now()-interval '2 minutes', expires_at=now()-interval '1 minute' where email=?", address);
        resend(address);
        var reopened = pending.findByEmail(address).orElseThrow();
        assertNull(reopened.getConsumedAt()); assertEquals(0, reopened.getAttempts());
        assertTrue(reopened.getExpiresAt().isAfter(OffsetDateTime.now().plusMinutes(4)));
        verifyCode(address, code(address));
        assertTrue(users.findByEmail(address).orElseThrow().isEmailVerified());
    }

    @Test void pendingSendCapCountsActualSendsPerAddress() {
        String address = address(); register(address);
        for (int i = 0; i < 6; i++) {
            jdbc.update("update pending_registrations set last_sent_at=now()-interval '2 minutes' where email=?", address);
            resend(address);
        }
        verify(email, times(5)).send(eq(address), anyString(), anyString());
        String other = address(); register(other);
        jdbc.update("update pending_registrations set last_sent_at=now()-interval '2 minutes' where email=?", other);
        resend(other);
        verify(email, times(2)).send(eq(other), anyString(), anyString());
    }

    @Test void mailFailureRollsBackRegistration() {
        String address = address();
        doThrow(new EmailDeliveryException("test outage")).when(email).send(eq(address), anyString(), anyString());
        assertThrows(EmailDeliveryException.class, () -> register(address));
        assertTrue(pending.findByEmail(address).isEmpty()); assertTrue(users.findByEmail(address).isEmpty());
    }

    @Test void chatFailureRollsBackTheWholeTurn() {
        User user = user();
        jdbc.execute("CREATE FUNCTION reject_test_answer() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN IF NEW.role = ''ASSISTANT'' THEN RAISE EXCEPTION ''test failure''; END IF; RETURN NEW; END'");
        jdbc.execute("CREATE TRIGGER reject_test_answer BEFORE INSERT ON chat_messages FOR EACH ROW EXECUTE FUNCTION reject_test_answer()");
        try {
            ChatRequest request = new ChatRequest(); request.setMessage("What is Aloe Vera?");
            assertThrows(RuntimeException.class, () -> chat.ask(request, user, "chat-test"));
            assertEquals(0, jdbc.queryForObject("select count(*) from chat_sessions where user_id=?", Integer.class, user.getId()));
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_answer ON chat_messages");
            jdbc.execute("DROP FUNCTION reject_test_answer()");
        }
    }

    @Test void concurrentChatTurnsPersistCompletePairsWithStableHistoryAndOwnership() throws Exception {
        User user = user();
        ChatRequest first = new ChatRequest(); first.setMessage("What is Aloe Vera?");
        UUID session = chat.ask(first, user, "first-turn").getSessionId();
        concurrent(4, index -> {
            ChatRequest next = new ChatRequest(); next.setSessionId(session); next.setMessage("Follow up " + index);
            assertEquals(session, chat.ask(next, user, "turn-" + index).getSessionId());
        });
        jdbc.update("update chat_messages set created_at=now() where session_id=?",session);
        var history = chat.getSessionMessages(session, user, 0, 50).getContent();
        assertEquals(10, history.size());
        for (int index = 0; index < history.size(); index += 2) {
            assertEquals("user", history.get(index).role());
            assertEquals("assistant", history.get(index+1).role());
            assertTrue(history.get(index).id() < history.get(index+1).id());
        }
        User other = user();
        assertThrows(ClientErrorException.class, () -> chat.getSessionMessages(session, other, 0, 50));
    }

    @Test void failureAuditSurvivesRollback() {
        String address = address();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audit.registerAttempt(address, "127.0.0.1", "test", false, "TEST");
            status.setRollbackOnly();
        });
        assertEquals(1, jdbc.queryForObject("select count(*) from auth_audit_log where email=?", Integer.class, address));
    }

    @Test void confidenceAndNativeRegionalSearchRoundTrip() {
        User user = user();
        Identification item = new Identification(); item.setUserId(user.getId()); item.setStatus("KNOWN");
        item.setPlantId(plants.findByPlantCode("PLANT_001").orElseThrow().getId());
        item.setConfidence(new BigDecimal("0.9471267")); item.setModelVersion("test-v1");
        item = identifications.saveAndFlush(item);
        assertEquals(0.9471267, identificationService.getHistoryItem(item.getId(), user).confidence());
        assertTrue(plants.searchByTerm("kumari").stream().anyMatch(p -> p.getPlantCode().equals("PLANT_001")));
    }

    @Test void databaseRejectsContradictoryResultsAndRelationships() {
        User user = user(); Long plantId = plants.findByPlantCode("PLANT_001").orElseThrow().getId();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into identifications(user_id,status,confidence,model_version) values (?,'KNOWN',0.9,'v1')", user.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into identifications(user_id,plant_id,status,confidence,model_version) values (?,?,'UNKNOWN',0.9,'v1')", user.getId(),plantId));
        Long first = jdbc.queryForObject("insert into identifications(user_id,status,confidence,model_version) values (?,'UNKNOWN',0.2,'v1') returning id",Long.class,user.getId());
        Long second = jdbc.queryForObject("insert into identifications(user_id,status,confidence,model_version) values (?,'UNKNOWN',0.2,'v1') returning id",Long.class,user.getId());
        Long image = jdbc.queryForObject("insert into identification_images(identification_id,sequence_number) values (?,1) returning id",Long.class,first);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into identification_images(identification_id,sequence_number) values (?,1)",first));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into identification_heatmaps(identification_id,identification_image_id,storage_key,sequence_number) values (?,?,'test',1)",second,image));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into users(email,password_hash,role) values (?,'test','USER')",user.getEmail().toUpperCase(Locale.ROOT)));
    }

    @Test void upgradesPopulatedV25AndRefusesToSilentlyRepairInvalidHistory() throws Exception {
        try (var database = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16")) {
            database.start();
            var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword());
            var before = org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                    .locations("classpath:db/migration").target("25").load();
            before.migrate();
            var sql = new JdbcTemplate(dataSource);
            Long owner = sql.queryForObject("insert into users(email,password_hash,role) values ('upgrade@example.test','test','USER') returning id",Long.class);
            Long invalid = sql.queryForObject("insert into identifications(user_id,status) values (?,'KNOWN') returning id",Long.class,owner);
            var latest = org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                    .locations("classpath:db/migration").load();
            assertThrows(org.flywaydb.core.api.FlywayException.class, latest::migrate);
            assertEquals(1, sql.queryForObject("select count(*) from identifications where id=?",Integer.class,invalid));
            // Explicitly remove only this deliberately invalid test row, then exercise a valid upgrade.
            sql.update("delete from identifications where id=?",invalid);
            Long result = sql.queryForObject("insert into identifications(user_id,status,confidence,model_version,response_payload) values (?,'UNKNOWN',0.9471,'v1',?) returning id",Long.class,owner,"{\"confidence\":0.9471267}");
            String preflight = java.nio.file.Files.readString(java.nio.file.Path.of("../docs/database/preflight-v26.sql"));
            for (var row : sql.queryForList(preflight)) assertEquals(0L, ((Number) row.get("count")).longValue(), row.toString());
            latest.migrate();
            assertEquals(new BigDecimal("0.9471267"),sql.queryForObject("select confidence from identifications where id=?",BigDecimal.class,result));
        }
    }
}
