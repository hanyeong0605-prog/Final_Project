package com.jobpilot.api.domain.companyfinance.service;

import com.jobpilot.api.domain.companyfinance.client.OpenDartClient;
import java.sql.PreparedStatement;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DartCorporationSyncService {
    private static final String UPSERT = """
            INSERT INTO dart_corporations (corp_code, corp_name, corp_eng_name, stock_code, modify_date)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE corp_name = VALUES(corp_name), corp_eng_name = VALUES(corp_eng_name),
                stock_code = VALUES(stock_code), modify_date = VALUES(modify_date)
            """;

    private final OpenDartClient client;
    private final JdbcTemplate jdbc;

    public DartCorporationSyncService(OpenDartClient client, JdbcTemplate jdbc) {
        this.client = client;
        this.jdbc = jdbc;
    }

    @Transactional
    public int sync() {
        var corporations = client.downloadCorporations();
        if (corporations.isEmpty()) return 0;
        jdbc.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement statement, int index) throws java.sql.SQLException {
                var corporation = corporations.get(index);
                statement.setString(1, corporation.corpCode());
                statement.setString(2, corporation.corpName());
                statement.setString(3, blankToNull(corporation.corpEngName()));
                statement.setString(4, blankToNull(corporation.stockCode()));
                statement.setString(5, blankToNull(corporation.modifyDate()));
            }
            @Override public int getBatchSize() { return corporations.size(); }
        });
        return corporations.size();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
