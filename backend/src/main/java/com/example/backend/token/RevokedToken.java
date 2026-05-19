package com.example.backend.token;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {
  @Id
  private String jti;
  private Instant revokedAt;
  private Instant expiresAt;
}
