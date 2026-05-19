package com.example.backend.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Permission {
  USER_SELF_READ("user:self:read"),
  USER_SELF_UPDATE("user:self:update"),
  ADMIN_USER_FILTER("admin:user:filter"),
  ADMIN_USER_READ("admin:user:read"),
  NOTIFICATION_SEND("notification:send"),
  NOTIFICATION_STATS_READ("notification:stats:read");

  private final String permission;
}
