package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Role;
import com.tradesim.tradesim.domain.User;
import com.tradesim.tradesim.exception.DuplicateUsernameException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserRepository implements UserRepository {

    private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public User save(User user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO users (username, password_hash, role, created_at) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, user.username());
                ps.setString(2, user.passwordHash());
                ps.setString(3, user.role().name());
                ps.setTimestamp(4, Timestamp.from(user.createdAt()));
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateUsernameException(user.username());
        }
        Number key = keyHolder.getKey();
        Long id = key != null ? key.longValue() : null;
        return new User(id, user.username(), user.passwordHash(), user.role(), user.createdAt());
    }

    @Override
    public Optional<User> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT id, username, password_hash, role, created_at FROM users WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.query(
                "SELECT id, username, password_hash, role, created_at FROM users WHERE LOWER(username) = LOWER(?)",
                ROW_MAPPER,
                username.trim()
        ).stream().findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)",
                Integer.class,
                username.trim()
        );
        return count != null && count > 0;
    }

    @Override
    public List<User> findAll() {
        return jdbcTemplate.query(
                "SELECT id, username, password_hash, role, created_at FROM users ORDER BY id",
                ROW_MAPPER
        );
    }
}

