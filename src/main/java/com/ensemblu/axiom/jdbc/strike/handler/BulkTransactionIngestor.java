package com.ensemblu.axiom.jdbc.strike.handler;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.jdbc.api.AxiomWarp;
import com.ensemblu.axiom.jdbc.strike.router.Router;
import com.ensemblu.axiom.schema.SchemaGuard;
import com.ensemblu.axiom.spec.database.contract.AxiomProtocol;
import com.ensemblu.axiom.spec.parser.JsonParser;

import java.util.function.Function;

public interface BulkTransactionIngestor {
    static Router.HandlerFunction create(AxiomWarp warp) {
        return substance -> {//
            final var data = SchemaGuard//
                    .checkContent(substance)//
                    .basedOnSchemaInPath("schemas/bulk_transaction_schema")//
                    .withParser(toJson())//
                    .getOrThrow();//

            final var batchData = data.targetKey("transactions").toStringKeyMapListVal();

            return warp.write(() -> {
                var contract = Axiom//
                        .Data//
                        .<String, AxiomProtocol>emptyMap()//
                        .put("account_id", AxiomProtocol.STRING)//
                        .put("amount", AxiomProtocol.DOUBLE)//
                        .put("direction", AxiomProtocol.STRING)//
                        .put("reference_note", AxiomProtocol.STRING);

                return warp.strike()//
                        .bulk("""
                                INSERT INTO ledger_transactions (account_id, amount, direction, reference_note)
                                VALUES (:java.account_id::uuid, :java.amount::double precision, :java.direction, :java.reference_note)
                                """)
                        .withContract(contract)//
                        .withData(batchData)//
                        .map(count -> Axiom//
                                .Data//
                                .<String, Object>emptyMap()//
                                .put("status", "BULK_SUCCESS")//
                                .put("inserted_rows", count));
            });
        };
    }

    private static Function<String, PersistentMap<String, Object>> toJson() {
        return s -> JsonParser.take(s).openBuffer().ensureRootIsObject().parseObject();
    }
}