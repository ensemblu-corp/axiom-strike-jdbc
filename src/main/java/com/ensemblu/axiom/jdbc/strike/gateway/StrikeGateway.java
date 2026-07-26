package com.ensemblu.axiom.jdbc.strike.gateway;
import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.foundation.Nothing;
import com.ensemblu.axiom.jdbc.api.AxiomWarp;

import com.ensemblu.axiom.jdbc.strike.handler.AccountQueryExecutor;
import com.ensemblu.axiom.jdbc.strike.handler.BulkTransactionIngestor;
import com.ensemblu.axiom.jdbc.strike.handler.ParallelAnalyticsEngine;
import com.ensemblu.axiom.jdbc.strike.infra.Seeder;
import com.ensemblu.axiom.jdbc.strike.infra.StrikeInfrastructure;
import com.ensemblu.axiom.jdbc.strike.router.Router;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public interface StrikeGateway {

    static Nothing launchOnPort (int port){
        Axiom.Io.log("🌊 [AXIOM STRIKE] Initializing Full System Verification...").run();
        Axiom.Io.log("-----------------------------------------------------------").run();

        final var warp = StrikeInfrastructure.initialize();
        Seeder.initializeLedgerData(warp);
        final var server = bindHttpServer(port);
        server.createContext("/", createRouter(warp));
        server.start();

        Axiom.Io.log("🚀 [AXIOM STRIKE] Gateway active on port " + port).run();

        return Nothing.INSTANCE;
    }

    /**
     * Binds and configures the underlying HTTP server socket listener.
     */
    private static HttpServer bindHttpServer(int targetPort) {
        try {
            return HttpServer.create(new InetSocketAddress(targetPort), 0);
        } catch (IOException e) {
            throw new RuntimeException("Failed to bind Axiom Strike HTTP gateway on port " + targetPort, e);
        }
    }

    /**
     * Constructs and wires the modular routing table for database strike scenarios.
     */
    private static Router createRouter(AxiomWarp warp) {
        return Router.groupBy("strike")//
                .POST("accounts/query")//
                .withHandler(AccountQueryExecutor.create(warp))//
                .POST("accounts/bulk-txn")//
                .withHandler(BulkTransactionIngestor.create(warp))//
                .POST("system/parallel-metrics")//
                .withHandler(ParallelAnalyticsEngine.create(warp));
    }
}