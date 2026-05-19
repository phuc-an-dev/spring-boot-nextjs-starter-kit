package com.example.backend.users;

import com.example.backend.s3.repository.UploadedFileRepository;
import com.example.backend.s3.service.FileUploadService;
import com.example.backend.users.repository.PasswordResetTokenRepository;
import com.example.backend.users.repository.UserRepository;
import com.example.backend.users.repository.VerificationCodeRepository;
import com.example.backend.users.service.UserService;
import com.example.backend.util.exception.ApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceEmailVerificationTest {
  @Mock UserRepository userRepository;
  @Mock VerificationCodeRepository verificationCodeRepository;
  @Mock PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock UploadedFileRepository uploadedFileRepository;
  @Mock PasswordEncoder passwordEncoder;
  @Mock FileUploadService fileUploadService;

  @Test
  void verifyEmail_validTokenVerifiesUserAndConsumesToken() {
    User user = new User();
    VerificationCode verificationCode = verificationCode(user, "valid-token");
    ReflectionTestUtils.setField(verificationCode, "expiresAt", Instant.now().plusSeconds(60));
    given(verificationCodeRepository.findByCode("valid-token")).willReturn(Optional.of(verificationCode));
    UserService userService = userService();

    userService.verifyEmail("valid-token");

    assertThat(user.isVerified()).isTrue();
    then(userRepository).should().save(user);
    ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);
    then(verificationCodeRepository).should().save(captor.capture());
    assertThat(captor.getValue().isConsumed()).isTrue();
    then(verificationCodeRepository).should(never()).delete(verificationCode);
  }

  @Test
  void verifyEmail_expiredTokenReturnsBadRequest() {
    VerificationCode verificationCode = verificationCode(new User(), "expired-token");
    ReflectionTestUtils.setField(verificationCode, "expiresAt", Instant.now().minusSeconds(1));
    given(verificationCodeRepository.findByCode("expired-token")).willReturn(Optional.of(verificationCode));
    UserService userService = userService();

    assertThatThrownBy(() -> userService.verifyEmail("expired-token"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Verification token is expired");
  }

  @Test
  void verifyEmail_reusedTokenReturnsBadRequest() {
    VerificationCode verificationCode = verificationCode(new User(), "used-token");
    ReflectionTestUtils.setField(verificationCode, "expiresAt", Instant.now().plusSeconds(60));
    ReflectionTestUtils.setField(verificationCode, "consumedAt", Instant.now().minusSeconds(1));
    given(verificationCodeRepository.findByCode("used-token")).willReturn(Optional.of(verificationCode));
    UserService userService = userService();

    assertThatThrownBy(() -> userService.verifyEmail("used-token"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Verification token has already been used");
  }

  private VerificationCode verificationCode(User user, String code) {
    VerificationCode verificationCode = new VerificationCode(user);
    ReflectionTestUtils.setField(verificationCode, "code", code);
    return verificationCode;
  }

  private UserService userService() {
    return new UserService(
        userRepository,
        verificationCodeRepository,
        passwordResetTokenRepository,
        uploadedFileRepository,
        passwordEncoder,
        fileUploadService
    );
  }
}
