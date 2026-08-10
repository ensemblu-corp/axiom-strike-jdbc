package com.ensemblu.axiom.jdbc.strike.infra;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.foundation.Nothing;
import com.ensemblu.axiom.jdbc.api.AxiomWarp;

public interface Seeder {
    /**
     * Using our ingestion engine to load initial banking state and accounts
     * without raw SQL files or mutable ORM baggage.
     * */
    static Nothing initializeLedgerData(AxiomWarp warp) {
        Axiom.Io.log("\n🧹 [CLEANSE] Purging Perimeter for Fundamental Testing...").run();

        return warp.write(() -> {//
                    warp//
                            .strike()//
                            .shot("""                            
                                    DROP TRIGGER IF EXISTS trg_audit_accounts ON accounts;
                                    DROP FUNCTION IF EXISTS log_account_mutation();
                                    DROP TABLE IF EXISTS audit_logs CASCADE;
                                    DROP TABLE IF EXISTS ledger_transactions CASCADE;
                                    DROP TABLE IF EXISTS account_holders CASCADE;
                                    DROP TABLE IF EXISTS app_users CASCADE;
                                    DROP TABLE IF EXISTS account_limits CASCADE;
                                    DROP TABLE IF EXISTS accounts CASCADE;
                                    DROP VIEW IF EXISTS active_account_summary CASCADE;
                                    """).getOrThrow();

                    warp//
                            .strike()//
                            .shot("""                                             
                                    CREATE TABLE IF NOT EXISTS account_limits (
                                        tier VARCHAR(50) PRIMARY KEY,
                                        max_daily_withdrawal NUMERIC(18, 4) NOT NULL DEFAULT 10000.0000,
                                        max_single_tx NUMERIC(18, 4) NOT NULL DEFAULT 5000.0000,
                                        requires_approval BOOLEAN NOT NULL DEFAULT FALSE
                                    );
                                    
                                    INSERT INTO account_limits (tier, max_daily_withdrawal, max_single_tx, requires_approval)
                                    VALUES
                                        ('STANDARD', 10000.0000, 5000.0000, FALSE),
                                        ('OLD_TIER', 5000.0000, 2000.0000, TRUE)
                                    ON CONFLICT (tier) DO NOTHING;
                                    """).getOrThrow();

                    warp//
                            .strike()//
                            .shot("""                                             
                                   CREATE TABLE IF NOT EXISTS accounts (
                                        account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        owner VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
                                        balance NUMERIC(18, 4) NOT NULL CHECK (balance >= 0),
                                        currency VARCHAR(3) NOT NULL,
                                        tier VARCHAR(50) NOT NULL DEFAULT 'STANDARD' REFERENCES account_limits(tier),
                                        status VARCHAR(50) NOT NULL,
                                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                                    );
                                   
                                    INSERT INTO accounts (account_id, owner, balance, currency, tier, status)
                                                    VALUES
                                                        ('11111111-1111-1111-1111-111111111111', 'SYSTEM_DEMO', 50000.0000, 'ILS', 'STANDARD', 'ACTIVE')
                                                    ON CONFLICT (account_id) DO NOTHING;
                                   
                                    CREATE INDEX IF NOT EXISTS idx_accounts_currency ON accounts(currency);
                                    CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);
                                    CREATE INDEX IF NOT EXISTS idx_accounts_tier ON accounts(tier);
                                   """).getOrThrow();

                    warp//
                            .strike()//
                            .shot("""                                             
                                    CREATE TABLE IF NOT EXISTS app_users (
                                        user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        email VARCHAR(255) NOT NULL UNIQUE,
                                        full_name VARCHAR(255) NOT NULL,
                                        password_hash VARCHAR(255) NOT NULL,
                                        user_code VARCHAR(50) NOT NULL UNIQUE
                                    );
                                   
                                   """).getOrThrow();

                    warp//
                            .strike()//
                            .shot("""                                             
                                   CREATE TABLE IF NOT EXISTS account_holders (
                                        account_id UUID NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
                                        user_id UUID NOT NULL REFERENCES app_users(user_id) ON DELETE CASCADE,
                                        role VARCHAR(50) NOT NULL DEFAULT 'PRIMARY_OWNER',
                                        PRIMARY KEY (account_id, user_id)
                                    );
                                   
                                   CREATE INDEX IF NOT EXISTS idx_holders_user ON account_holders(user_id);
                                   
                                   """).getOrThrow();

                    warp//
                            .strike()//
                            .shot("""                                             
                                    CREATE TABLE IF NOT EXISTS ledger_transactions (
                                        tx_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        account_id UUID NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
                                        amount NUMERIC(18, 4) NOT NULL,
                                        direction VARCHAR(10) NOT NULL CHECK (direction IN ('CREDIT', 'DEBIT')),
                                        reference_note VARCHAR(255) NOT NULL DEFAULT 'NONE',
                                        posted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                                    );
                                    
                                    CREATE INDEX IF NOT EXISTS idx_transactions_account ON ledger_transactions(account_id);
                                    
                                    """).getOrThrow();

                    warp//
                            .strike()//
                            .shot("""                                             
                                    CREATE TABLE IF NOT EXISTS audit_logs (
                                         audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                         actor VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
                                         action_type VARCHAR(100) NOT NULL,
                                         payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                                     );
                                    
                                    CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs(action_type);
                                    
                                    """).getOrThrow();

                    warp//
                            .strike()//
                            .shot("""                                                                     
                                     -- View: Active Account Summary joining limits
                                     CREATE VIEW active_account_summary AS
                                     SELECT
                                         account.account_id,
                                         account.balance,
                                         account.currency,
                                         account.status,
                                         account_limit.max_daily_withdrawal,
                                         account_limit.requires_approval
                                     FROM accounts account
                                     JOIN account_limits AS account_limit  ON account.tier = account_limit.tier
                                     WHERE account.status = 'ACTIVE';
                                    """).getOrThrow();


                    warp//
                            .strike()//
                            .shot("""                                                                                  
                                    -- Trigger Function & Trigger for automated auditing
                                         CREATE OR REPLACE FUNCTION log_account_mutation()
                                         RETURNS TRIGGER AS $$
                                         BEGIN
                                             INSERT INTO audit_logs (actor, action_type, payload)
                                             VALUES (
                                                 'DB_TRIGGER',
                                                 TG_OP || '_ACCOUNT',
                                                 jsonb_build_object('account_id', COALESCE(NEW.account_id, OLD.account_id), 'status', COALESCE(NEW.status, OLD.status))
                                             );
                                             RETURN NEW;
                                         END;
                                         $$ LANGUAGE plpgsql;
                                    
                                         CREATE TRIGGER trg_audit_accounts
                                         AFTER INSERT OR UPDATE ON accounts
                                         FOR EACH ROW
                                         EXECUTE FUNCTION log_account_mutation();
                                    """).getOrThrow();

                    warp.ingest()//
                            .fromFile("csv/initial_accounts.csv")//
                            .usingFileHeaders()//
                            .onTableName("accounts")//
                            .getOrThrow();

                    return Axiom.Check.success("Fundamental Ground Re-Forged");
                }).mapEmpty()//
                .getOrThrow();
    }
}