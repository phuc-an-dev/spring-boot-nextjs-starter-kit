package com.example.backend.users;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;

class UserOAuth2ConstructorTest {
  @Test
  void constructor_copiesOAuth2ProfileIntoUser() {
    OAuth2User oauth2User = new DefaultOAuth2User(
        List.of(new SimpleGrantedAuthority("ROLE_USER")),
        Map.of(
            "sub", "google-123",
            "email", "oauth@example.com",
            "name", "OAuth Person"
        ),
        "sub"
    );

    User user = new User(oauth2User);

    assertThat(user.getEmail()).isEqualTo("oauth@example.com");
    assertThat(user.getFirstName()).isEqualTo("OAuth");
    assertThat(user.getLastName()).isEqualTo("Person");
    assertThat(user.isVerified()).isTrue();
    assertThat(user.getRole()).isEqualTo(Role.USER);
  }
}
