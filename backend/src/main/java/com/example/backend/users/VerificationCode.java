package com.example.backend.users;

import com.example.backend.entity.AbstractEntity;
import com.example.backend.util.Client;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.RandomStringUtils;

@Entity
@Getter
@NoArgsConstructor
@Client
public class VerificationCode extends AbstractEntity {

  private String code;
  private Instant expiresAt;
  private Instant consumedAt;
  @Setter
  private boolean emailSent = false;
  @OneToOne
  private User user;

  public VerificationCode(User user) {
    this.user = user;
    this.code = RandomStringUtils.random(6, false, true);
    this.expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
  }

  public boolean isExpired() {
    return expiresAt != null && expiresAt.isBefore(Instant.now());
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public void consume() {
    this.consumedAt = Instant.now();
  }
}
