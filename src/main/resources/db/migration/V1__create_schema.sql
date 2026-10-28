CREATE TABLE customers (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    first_name  VARCHAR(60)  NOT NULL,
    last_name   VARCHAR(60)  NOT NULL,
    email       VARCHAR(120) NOT NULL,
    phone       VARCHAR(30)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uq_customers_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE accounts (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(20)    NOT NULL,
    customer_id    BIGINT         NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    balance        DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency       VARCHAR(3)     NOT NULL DEFAULT 'USD',
    version        BIGINT         NULL,
    created_at     DATETIME(6)    NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE = InnoDB;

CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);

CREATE TABLE transactions (
    id                          BIGINT         NOT NULL AUTO_INCREMENT,
    account_id                  BIGINT         NOT NULL,
    type                        VARCHAR(20)    NOT NULL,
    amount                      DECIMAL(19, 2) NOT NULL,
    balance_after               DECIMAL(19, 2) NOT NULL,
    description                 VARCHAR(140)   NULL,
    reference                   VARCHAR(36)    NOT NULL,
    counterparty_account_number VARCHAR(20)    NULL,
    created_at                  DATETIME(6)    NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE = InnoDB;

CREATE INDEX idx_transactions_account_id_created_at ON transactions (account_id, created_at);
CREATE INDEX idx_transactions_reference ON transactions (reference);
