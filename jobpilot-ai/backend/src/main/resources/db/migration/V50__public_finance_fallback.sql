ALTER TABLE dart_corporations ADD COLUMN jurir_no CHAR(13) NULL AFTER stock_code;
ALTER TABLE dart_corporations ADD KEY ix_dart_corporations_jurir_no (jurir_no);

ALTER TABLE company_financial_years ADD COLUMN data_source VARCHAR(30) NOT NULL DEFAULT 'DART' AFTER currency;
