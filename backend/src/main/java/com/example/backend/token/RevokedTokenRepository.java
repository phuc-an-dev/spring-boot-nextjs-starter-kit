package com.example.backend.token;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
  boolean existsByJtiAndExpiresAtAfter(String jti, Instant now);

  @Modifying
  @Query("delete from RevokedToken token where token.expiresAt <= :now")
  void deleteExpired(Instant now);
}
