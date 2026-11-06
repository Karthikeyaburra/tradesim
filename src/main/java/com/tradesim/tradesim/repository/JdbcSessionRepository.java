package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Session;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class JdbcSessionRepository implements SessionRepository {

    private static final RowMapper<Session> ROW_MAPPER = (rs, rowNum) -> new Session(
            rs.getString("token"),
            rs.getLong("user_id"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Session session) {
        jdbcTemplate.update(
                "INSERT INTO sessions (token, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                session.token(),
                session.userId(),
                Timestamp.from(session.createdAt()),
                Timestamp.from(session.expiresAt())
        );
    }

    @Override
    public Optional<Session> findByToken(String token) {
        return jdbcTemplate.query(
                "SELECT token, user_id, created_at, expires_at FROM sessions WHERE token = ?",
                ROW_MAPPER,
                token
        ).stream().findFirst();
    }

    @Override
    public void deleteByToken(String token) {
        jdbcTemplate.update("DELETE FROM sessions WHERE token = ?", token);
    }
}

