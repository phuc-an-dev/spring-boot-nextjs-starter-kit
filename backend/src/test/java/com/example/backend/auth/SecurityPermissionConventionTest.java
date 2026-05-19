package com.example.backend.auth;

import com.example.backend.admin.controller.AdminUsersController;
import com.example.backend.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPermissionConventionTest {
  @Test
  void adminRole_containsAdminUserFilterPermission() {
    assertThat(Role.ADMIN.getPermissionSet())
        .extracting(Permission::getPermission)
        .contains(SecurityPermissions.ADMIN_USER_FILTER);
  }

  @Test
  void adminUsersEndpoint_usesAuthorityPermissionInsteadOfRawRole() throws NoSuchMethodException {
    PreAuthorize annotation = AdminUsersController.class
        .getMethod("admin_getUsers", int.class)
        .getAnnotation(PreAuthorize.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value())
        .isEqualTo("hasAuthority('" + SecurityPermissions.ADMIN_USER_FILTER + "')");
  }
}
