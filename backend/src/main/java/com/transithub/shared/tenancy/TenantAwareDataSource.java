package com.transithub.shared.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

/**
 * Writes the current {@link TenantScope} onto every database connection the
 * application takes, so PostgreSQL row-level security can enforce it
 * (ADR-0003, layer 2).
 *
 * <p>Both settings are written on <em>every</em> checkout, including empty
 * values for {@link TenantScope#none()}. A pooled connection therefore never
 * carries a previous request's scope.
 *
 * <p>Flyway is unaffected: it connects as the schema owner through its own
 * data source.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String APPLY_SCOPE =
            "SELECT set_config('app.scope', ?, false), set_config('app.company_id', ?, false)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyScope(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyScope(super.getConnection(username, password));
    }

    private static Connection applyScope(Connection connection) throws SQLException {
        TenantScope scope = TenantContext.current();
        try (PreparedStatement statement = connection.prepareStatement(APPLY_SCOPE)) {
            statement.setString(1, scope.databaseScope());
            statement.setString(2, scope.databaseCompanyId());
            statement.execute();
            return connection;
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    /**
     * Wraps the application's data source bean. Slice tests such as
     * {@code @DataJpaTest} must {@code @Import} it explicitly.
     */
    @Component
    public static class Installer implements BeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
                return new TenantAwareDataSource(dataSource);
            }
            return bean;
        }
    }
}
