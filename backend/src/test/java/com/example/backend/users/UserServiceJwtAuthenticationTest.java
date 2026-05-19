package com.example.backend.users;

import com.example.backend.s3.repository.UploadedFileRepository;
import com.example.backend.s3.service.FileUploadService;
import com.example.backend.users.data.UpdateUserRequest;
import com.example.backend.users.repository.PasswordResetTokenRepository;
import com.example.backend.users.repository.UserRepository;
import com.example.backend.users.repository.VerificationCodeRepository;
import com.example.backend.users.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceJwtAuthenticationTest {
  @Mock UserRepository userRepository;
  @Mock VerificationCodeRepository verificationCodeRepository;
  @Mock PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock UploadedFileRepository uploadedFileRepository;
  @Mock PasswordEncoder passwordEncoder;
  @Mock FileUploadService fileUploadService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void update_resolvesAuthenticatedUserFromJwtSubject() {
    User user = new User();
    ReflectionTestUtils.setField(user, "id", 42L);
    ReflectionTestUtils.setField(user, "email", "user@email.com");
    user.setRole(Role.USER);
    setJwtPrincipal("user@email.com");
    given(userRepository.findByEmail("user@email.com")).willReturn(Optional.of(user));
    given(userRepository.getReferenceById(42L)).willReturn(user);
    given(userRepository.save(user)).willReturn(user);
    UpdateUserRequest request = new UpdateUserRequest();
    request.setFirstName("New");
    request.setLastName("Name");
    UserService userService = userService();

    userService.update(request);

    assertThat(user.getFirstName()).isEqualTo("New");
    assertThat(user.getLastName()).isEqualTo("Name");
    then(userRepository).should().findByEmail("user@email.com");
  }

  private void setJwtPrincipal(String subject) {
    Jwt jwt = new Jwt(
        "access-token",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "HS256"),
        Map.of("sub", subject)
    );
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(jwt, null, List.of())
    );
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
