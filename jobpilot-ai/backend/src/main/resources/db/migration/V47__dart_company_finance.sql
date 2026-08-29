CREATE TABLE dart_corporations (
    corp_code CHAR(8) NOT NULL,
    corp_name VARCHAR(255) NOT NULL,
    corp_eng_name VARCHAR(255) NULL,
    stock_code CHAR(6) NULL,
    modify_date CHAR(8) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (corp_code),
    KEY ix_dart_corporations_name (corp_name)
);

CREATE TABLE company_dart_matches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_provider VARCHAR(30) NOT NULL,
    source_company_id VARCHAR(150) NOT NULL DEFAULT '',
    normalized_company_name VARCHAR(255) NOT NULL,
    corp_code CHAR(8) NULL,
    match_status VARCHAR(20) NOT NULL,
    match_method VARCHAR(40) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
    verified_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_dart_match_source (source_provider, source_company_id, normalized_company_name),
    KEY ix_company_dart_match_corp (corp_code),
    CONSTRAINT fk_company_dart_match_corp FOREIGN KEY (corp_code) REFERENCES dart_corporations (corp_code)
);

CREATE TABLE company_financial_years (
    id BIGINT NOT NULL AUTO_INCREMENT,
    corp_code CHAR(8) NOT NULL,
    business_year SMALLINT NOT NULL,
    report_code CHAR(5) NOT NULL,
    rcept_no CHAR(14) NULL,
    fs_div VARCHAR(8) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    revenue DECIMAL(22,0) NULL,
    operating_income DECIMAL(22,0) NULL,
    net_income DECIMAL(22,0) NULL,
    total_assets DECIMAL(22,0) NULL,
    total_liabilities DECIMAL(22,0) NULL,
    total_equity DECIMAL(22,0) NULL,
    operating_cash_flow DECIMAL(22,0) NULL,
    cash_and_cash_equivalents DECIMAL(22,0) NULL,
    fetched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_financial_year (corp_code, business_year, report_code, fs_div),
    KEY ix_company_financial_year_corp_year (corp_code, business_year),
    CONSTRAINT fk_company_financial_year_corp FOREIGN KEY (corp_code) REFERENCES dart_corporations (corp_code)
);

CREATE TABLE company_financial_metrics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    financial_year_id BIGINT NOT NULL,
    revenue_growth_1y DECIMAL(12,6) NULL,
    revenue_growth_3y DECIMAL(12,6) NULL,
    operating_margin DECIMAL(12,6) NULL,
    debt_ratio DECIMAL(12,6) NULL,
    operating_cashflow_ratio DECIMAL(12,6) NULL,
    profitability_positive BOOLEAN NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_financial_metric_year (financial_year_id),
    CONSTRAINT fk_company_financial_metric_year FOREIGN KEY (financial_year_id) REFERENCES company_financial_years (id)
);

CREATE TABLE company_hiring_monthly_metrics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_provider VARCHAR(30) NOT NULL,
    source_company_id VARCHAR(150) NOT NULL DEFAULT '',
    normalized_company_name VARCHAR(255) NOT NULL,
    metric_month DATE NOT NULL,
    active_posting_count INT NOT NULL DEFAULT 0,
    developer_posting_count INT NOT NULL DEFAULT 0,
    role_summary JSON NULL,
    skill_summary JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_hiring_metric_month (source_provider, source_company_id, normalized_company_name, metric_month)
);

CREATE TABLE company_growth_predictions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    corp_code CHAR(8) NOT NULL,
    base_year SMALLINT NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    growth_probability DECIMAL(8,6) NOT NULL,
    profitability_improvement_probability DECIMAL(8,6) NOT NULL,
    stability_risk_probability DECIMAL(8,6) NOT NULL,
    outlook VARCHAR(20) NOT NULL,
    confidence VARCHAR(20) NOT NULL,
    evidence JSON NOT NULL,
    feature_snapshot JSON NOT NULL,
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_growth_prediction_version (corp_code, base_year, model_version),
    KEY ix_company_growth_prediction_latest (corp_code, generated_at),
    CONSTRAINT fk_company_growth_prediction_corp FOREIGN KEY (corp_code) REFERENCES dart_corporations (corp_code)
);
