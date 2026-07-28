package com.ensemblu.axiom.jdbc.strike;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.jdbc.strike.gateway.StrikeGateway;

/**
 * 🌊 [AXIOM STRIKE] Bootstrap Engine - Sovereign Execution Entry Point
 * <p>
 * This engine initializes the Axiom Strike ecosystem, provisions the relational-siege bridge,
 * and boots up the framework-free HTTP gateway for verification endpoints.
 * <p>
 * ### Verification Commands (Run in sequence):
 * <pre>{@code
 * curl -X POST -H "Content-Type: application/json" -d "{\"status\": \"ACTIVE\", \"limit\": 1}" http://localhost:8089/strike/accounts/query
 *
 * curl -X POST -H "Content-Type: application/json" -d "{\"transactions\": [{\"account_id\": \"11111111-1111-1111-1111-111111111111\", \"amount\": 125.50, \"direction\": \"CREDIT\", \"reference_note\": \"Test Deposit\"}]}" http://localhost:8089/strike/accounts/bulk-txn
 *
 * curl -X POST -H "Content-Type: application/json" -d "{\"status\": \"ACTIVE\", \"currency\": \"ILS\"}" http://localhost:8089/strike/system/parallel-metrics
 * }</pre>
 */
public final class BootstrapEngine {

    private static final int DEFAULT_PORT = 8089;

    static void main() {
        StrikeGateway.launchOnPort(DEFAULT_PORT);
    }
}
