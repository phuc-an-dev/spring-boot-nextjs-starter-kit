package com.example.backend.token;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {
  @Mock RevokedTokenRepository revokedTokenRepository;
  @InjectMocks TokenRevocationService tokenRevocationService;

  @Test
  void revoke_savesTokenWhenJtiDoesNotExist() {
    Instant expiresAt = Instant.now().plusSeconds(60);
    given(revokedTokenRepository.existsById("jti-1")).willReturn(false);

    tokenRevocationService.revoke("jti-1", expiresAt);

    ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
    then(revokedTokenRepository).should().save(captor.capture());
    assertThat(captor.getValue().getJti()).isEqualTo("jti-1");
    assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiresAt);
  }
}
