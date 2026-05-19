package com.example.backend.token;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {
  private final RevokedTokenRepository revokedTokenRepository;

  @Transactional
  public void revoke(String jti, Instant expiresAt) {
    if (revokedTokenRepository.existsById(jti)) {
      return;
    }
    revokedTokenRepository.save(new RevokedToken(jti, Instant.now(), expiresAt));
  }

  public boolean isRevoked(String jti) {
    return revokedTokenRepository.existsByJtiAndExpiresAtAfter(jti, Instant.now());
  }

  @Scheduled(cron = "0 0 * * * *")
  @Transactional
  public void cleanupExpired() {
    revokedTokenRepository.deleteExpired(Instant.now());
  }
}
