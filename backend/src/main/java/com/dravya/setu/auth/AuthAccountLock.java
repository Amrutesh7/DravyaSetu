package com.dravya.setu.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes account workflows even before a users/pending row exists. */
@Component
public class AuthAccountLock {
    private final JdbcTemplate jdbc;

    public AuthAccountLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String email) {
        jdbc.execute(connection -> {
            var statement = connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))");
            statement.setString(1, "account:" + email);
            return statement;
        }, (org.springframework.jdbc.core.PreparedStatementCallback<Void>) statement -> {
            statement.execute();
            return null;
        });
    }
}
