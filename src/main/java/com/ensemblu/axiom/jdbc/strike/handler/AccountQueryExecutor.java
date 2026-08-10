package com.ensemblu.axiom.jdbc.strike.handler;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.foundation.Dop;
import com.ensemblu.axiom.jdbc.api.AxiomWarp;
import com.ensemblu.axiom.jdbc.strike.router.Router;
import com.ensemblu.axiom.schema.SchemaGuard;
import com.ensemblu.axiom.spec.database.contract.AxiomProtocol;
import com.ensemblu.axiom.spec.parser.JsonEmitter;
import com.ensemblu.axiom.spec.parser.JsonParser;

import java.util.function.Function;

public interface AccountQueryExecutor {
    static Router.HandlerFunction create(AxiomWarp warp) {
        return substance -> {
            final var data = SchemaGuard//
                    .checkContent(substance)//
                    .basedOnSchemaInPath("schemas/account_query_schema")//
                    .withParser(toJson())//
                    .getOrThrow();

            return warp.read(() ->
                    warp.strike()//
                            .dynamic("""
                                    SELECT account_id, balance, currency, status
                                    FROM accounts
                                    WHERE status = :java.status
                                    ORDER BY created_at DESC
                                    LIMIT :java.limit;""")
                            .withContract(deriveContractFromData(data))//
                            .withData(data)//
                            .map(l ->
                                    Axiom//
                                            .Data//
                                            .<String, Object>emptyMap()//
                                            .put("accounts", l.map(JsonEmitter::emit)))//
                            .getOrThrow()
            );
        };
    }

    private static Function<byte[], PersistentMap<String, Object>> toJson() {
        return s -> JsonParser.take(s).openBuffer().ensureRootIsObject().parseObject();
    }

    private static PersistentMap<String, AxiomProtocol> deriveContractFromData(PersistentMap<String, Object> data) {
        return Dop.project(data)//
                .mapValues(val -> switch (val) {
                    case Integer _ -> AxiomProtocol.INTEGER;
                    case Long _ -> AxiomProtocol.LONG;
                    case Double _ -> AxiomProtocol.DOUBLE;
                    case Boolean _ -> AxiomProtocol.BOOLEAN;
                    case java.util.Date _ -> AxiomProtocol.TIMESTAMP;
                    case String _ -> AxiomProtocol.STRING;
                    case null, default -> AxiomProtocol.OPAQUE;
                })//
                .deploy();
    }
}