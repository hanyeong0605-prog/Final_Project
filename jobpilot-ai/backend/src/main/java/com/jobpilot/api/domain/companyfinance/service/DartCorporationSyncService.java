package com.jobpilot.api.domain.companyfinance.service;

import com.jobpilot.api.domain.companyfinance.client.OpenDartClient;
import java.sql.PreparedStatement;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DartCorporationSyncService {
    private static final Logger log = LoggerFactory.getLogger(DartCorporationSyncService.class);
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

    /** Refresh when possible, but keep a previously stored official directory on a transient DART outage. */
    public int syncWithCacheFallback() {
        try {
            return sync();
        } catch (RuntimeException upstreamFailure) {
            Integer cached = jdbc.queryForObject("SELECT COUNT(*) FROM dart_corporations", Integer.class);
            if (cached != null && cached > 0) {
                log.warn("OpenDART corporation directory refresh failed; using cached rows={}", cached);
                return 0;
            }
            throw upstreamFailure;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
