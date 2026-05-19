package com.example.backend.users;

import com.example.backend.auth.Permission;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.example.backend.auth.Permission.USER_SELF_READ;
import static com.example.backend.auth.Permission.USER_SELF_UPDATE;

@Getter
@RequiredArgsConstructor
public enum Role {
  USER(Set.of(USER_SELF_READ, USER_SELF_UPDATE)),
  ADMIN(Set.of(Permission.values()));

  private final Set<Permission> permissionSet;
}
