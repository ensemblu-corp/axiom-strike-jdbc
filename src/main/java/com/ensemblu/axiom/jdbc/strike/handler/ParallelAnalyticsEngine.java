package com.ensemblu.axiom.jdbc.strike.handler;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.foundation.Dop;
import com.ensemblu.axiom.jdbc.api.AxiomWarp;
import com.ensemblu.axiom.jdbc.strike.router.Router;
import com.ensemblu.axiom.schema.SchemaGuard;
import com.ensemblu.axiom.spec.database.contract.AxiomProtocol;
import com.ensemblu.axiom.spec.database.contract.StrikeInstruction;
import com.ensemblu.axiom.spec.parser.JsonParser;

import java.util.List;
import java.util.function.Function;

public interface ParallelAnalyticsEngine {
    static Router.HandlerFunction create(AxiomWarp warp) {
        return substance -> {//
            final var data = SchemaGuard//
                    .checkContent(substance)//
                    .basedOnSchemaInPath("schemas/parallel_analytics_schema")//
                    .withParser(toJson())//
                    .getOrThrow();

            final var input = Axiom.Forge.source(data);
            final var targetStatus = input.follow("status").navigate().toStringVal();
            final var targetCurrency = input.follow("currency").navigate().toStringVal();

            return warp.read(() -> {
                final var tasks = List.of(//
                        StrikeInstruction//
                                .dynamic("SELECT COUNT(*) AS total_accounts FROM accounts WHERE status = :java.status")//
                                .withContract(Axiom//
                                                .Data//
                                                 .<String, AxiomProtocol>emptyMap()//
                                        .put("status", AxiomProtocol.STRING))//
                                .withData(Axiom//
                                        .Data//
                                        .<String, Object>emptyMap()
                                        .put("status", targetStatus)),
                        StrikeInstruction//
                                .dynamic("SELECT SUM(balance) AS total_liability FROM accounts WHERE currency = :java.currency")//
                                .withContract(Axiom//
                                        .Data//
                                        .<String, AxiomProtocol>emptyMap()//
                                        .put("currency", AxiomProtocol.STRING))//
                                .withData(Axiom//
                                        .Data//
                                        .<String, Object>emptyMap()//
                                        .put("currency", targetCurrency))//
                );

                return Axiom//
                        .Data//
                        .<String, Object>emptyMap()//
                        .put("parallel_metrics", warp.parallel(tasks).getOrThrow().map(Dop::toJson));
            });
        };
    }

    private static Function<String, PersistentMap<String, Object>> toJson() {
        return s -> JsonParser.take(s).openBuffer().ensureRootIsObject().parseObject();
    }
}