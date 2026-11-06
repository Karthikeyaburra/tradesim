package com.tradesim.tradesim.repository;

import com.tradesim.tradesim.domain.Session;

import java.util.Optional;

public interface SessionRepository {
    void save(Session session);

    Optional<Session> findByToken(String token);

    void deleteByToken(String token);
}

