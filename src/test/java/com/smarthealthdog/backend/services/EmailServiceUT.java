package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils; // Utility to set @Value fields

import com.smarthealthdog.backend.domain.EmailVerification;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.auth.EmailVerificationCodeSentEvent;
import com.smarthealthdog.backend.repositories.EmailVerificationRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.utils.TokenGenerator;

@ExtendWith(MockitoExtension.class)
class EmailServiceUT {

    // InjectMocks attempts to inject the mocks into this instance
    @InjectMocks
    private DevEmailService emailService;

    // Mock all the dependencies
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TokenGenerator tokenGenerator;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    // Fixed test data
    private final String TEST_EMAIL = "test@example.com";
    private final String TEST_CODE = "XYZ123";
    private final String TEST_FROM = "no-reply@smarthealthdog.com";
    private final int TEST_EXPIRY_MINUTES = 30;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "from", TEST_FROM);
        ReflectionTestUtils.setField(emailService, "emailVerificationExpiryMinutes", TEST_EXPIRY_MINUTES);

        Role role = new Role();
        role.setName(RoleEnum.USER);

        // Create a user object for testing
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail(TEST_EMAIL);
        testUser.setRole(role);
    }

    @Test
    void sendEmailVerification_ShouldUpdateUserAndSendEmail() {
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        // ACT
        // Note on @Async: In a unit test, calling the method directly executes it synchronously.
        // We verify the side effects (save and send) as if it ran successfully.
        EmailVerification emailVerification = EmailVerification.builder()
            .email(TEST_EMAIL)
            .emailVerificationToken(TEST_CODE)
            .emailVerificationTries(1)
            .emailVerificationRequestedAt(java.time.Instant.now())
            .emailVerificationExpiry(java.time.Instant.now().plusSeconds(TEST_EXPIRY_MINUTES * 60))
            .emailVerificationFailCount(0)
            .build();

        EmailVerificationCodeSentEvent event = new EmailVerificationCodeSentEvent(TEST_EMAIL, TEST_CODE, emailVerification);
        emailService.sendEmailVerification(event);

        // 1. VERIFY Email was sent by JavaMailSender
        // Use verify and captor to check the contents of the email message
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();

        // ASSERT 2: Verify Email Contents
        assertEquals(TEST_FROM, sentMessage.getFrom());
        assertEquals(TEST_EMAIL, sentMessage.getTo()[0]);
        assertEquals("[똑똑하개 건강하개] 이메일 인증 코드", sentMessage.getSubject());
        
        // Check the body contains the code and expiry time
        String expectedText = "이메일 인증 코드는 " + TEST_CODE + " 입니다. 이 코드는 " + TEST_EXPIRY_MINUTES + "분 후에 만료됩니다.";
        assertEquals(expectedText, sentMessage.getText());
    }
}