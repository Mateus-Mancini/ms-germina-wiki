package com.wikigerminare.users;

import com.wikigerminare.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserRepositoryPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void readsExistingPostgresUserAndRoleLabels() {
        UUID adminId = insertUser("admin");
        UUID memberId = insertUser("member");

        User admin = userRepository.findByEmail("admin-" + adminId + "@example.com").orElseThrow();
        User member = userRepository.findByEmail("member-" + memberId + "@example.com").orElseThrow();

        assertThat(admin.getId()).isEqualTo(adminId);
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(member.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(admin.getPasswordHash()).isEqualTo("test-hash");
        assertThat(userRepository.findByEmail("ADMIN-" + adminId + "@example.com")).isEmpty();
    }

    @Test
    void rejectsUnknownPersistedRoleLabel() {
        assertThatIllegalArgumentException().isThrownBy(() -> UserRole.fromDatabaseValue("owner"));
    }

    private UUID insertUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash, role)
                VALUES (?, 'Auth test', ?, 'test-hash', CAST(? AS user_role))
                """, id, role + "-" + id + "@example.com", role);
        return id;
    }
}
