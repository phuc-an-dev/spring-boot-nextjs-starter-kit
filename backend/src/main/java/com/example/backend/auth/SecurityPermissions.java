package com.example.backend.auth;

public interface SecurityPermissions {
  String USER_SELF_READ = "user:self:read";
  String USER_SELF_UPDATE = "user:self:update";
  String ADMIN_USER_FILTER = "admin:user:filter";
  String ADMIN_USER_READ = "admin:user:read";
  String NOTIFICATION_SEND = "notification:send";
  String NOTIFICATION_STATS_READ = "notification:stats:read";
}
