package com.ensemblu.axiom.jdbc.strike.infra;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.jdbc.strike.adapter.AxiomDataSourceAdapter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.ensemblu.axiom.jdbc.api.AxiomWarp;

public final class StrikeInfrastructure {

    /**
     * The "Strong Argument" setup.
     * Boots the physical pool and prepares the warp.
     */
    public static AxiomWarp initialize() {
        final var config = Axiom.Config.file("postgres-strike.properties");

        return AxiomWarp.connect(config)//
                .withPoolProvider(//
                        map -> {
                    final var hikariConfig = new HikariConfig();
                    // Casting to String/Integer keeps the data generic and immutable
                    hikariConfig.setJdbcUrl(map.targetKey("engine.url").toStringVal());
                    hikariConfig.setUsername(map.targetKey("engine.user").toStringVal());
                    hikariConfig.setPassword(map.targetKey("engine.password").toStringVal());
                    hikariConfig.setMinimumIdle(map.targetKey("engine.pool.min").toIntVal());
                    hikariConfig.setMaximumPoolSize(map.targetKey("engine.pool.max").toIntVal());

                    // Sovereignty Rule: We control the transaction, not the driver
                    hikariConfig.setAutoCommit(false);

                    return AxiomDataSourceAdapter.of(new HikariDataSource(hikariConfig));})//
                .validateRules()//
                .map(AxiomWarp::new)//
                .prependFailureMessage("🚨 INFRASTRUCTURE BOOT FAILURE: Pool rules violated.")
                .getOrThrow();
    }

}