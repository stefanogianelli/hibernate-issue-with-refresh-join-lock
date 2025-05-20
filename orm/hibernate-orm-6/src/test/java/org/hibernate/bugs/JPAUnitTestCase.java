package org.hibernate.bugs;

import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.hibernate.bugs.entity.CascadeRefreshEntity;
import org.hibernate.bugs.entity.NestedEntity;
import org.hibernate.bugs.entity.NoCascadeRefreshEntity;
import org.hibernate.internal.SessionImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM, using the Java Persistence API.
 */
@Testcontainers
class JPAUnitTestCase {

    @Container
    private static final MySQLContainer<?> DATABASE = new MySQLContainer<>("mysql:8.0")
            .withCommand(
                    "--innodb-lock-wait-timeout=0",
                    "--character_set_server=utf8",
                    "--collation_server=utf8_general_ci",
                    "--explicit_defaults_for_timestamp=true",
                    "--default_time_zone=+00:00",
                    "--transaction_isolation=READ-COMMITTED"
            );

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void init() {
        entityManagerFactory = Persistence.createEntityManagerFactory(
                "templatePU",
                Map.of(
                        "jakarta.persistence.jdbc.url", DATABASE.getJdbcUrl(),
                        "jakarta.persistence.jdbc.user", DATABASE.getUsername(),
                        "jakarta.persistence.jdbc.password", DATABASE.getPassword()
                )
        );
    }

    @AfterEach
    void destroy() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource
    void withCascadeRefresh(final BiConsumer<EntityManager, CascadeRefreshEntity> consumer, final boolean lockTest, final Class<Exception> expectedException) {

        final String firstName = "first entity";
        final String secondName = "second entity";
        final String firstNameUpdated = firstName + " (UPDATED)";
        final String secondNameUpdated = secondName + " (UPDATED)";
        final String secondNameAnotherUpdate = secondName + " (UPDATEDx2)";

        inTransaction(em -> {
            final NestedEntity nested = new NestedEntity();
            nested.name = secondName;

            final CascadeRefreshEntity entity = new CascadeRefreshEntity();
            entity.name = firstName;
            entity.second = nested;
            em.persist(entity);
        });

        inTransaction(em -> {

            final CascadeRefreshEntity entity = CascadeRefreshEntity.findByName(em, firstName);
            Assertions.assertNotNull(entity.second);

            executeAsync(() -> {
                inTransaction(nestedEm -> {
                    final CascadeRefreshEntity nestedEntity = CascadeRefreshEntity.findByName(nestedEm, firstName);
                    nestedEntity.name = firstNameUpdated;
                    Assertions.assertNotNull(nestedEntity.second);
                    nestedEntity.second.name = secondNameUpdated;
                    Assertions.assertTrue(nestedEm.contains(nestedEntity.second));
                });
            });

            consumer.accept(em, entity);

            Assertions.assertEquals(firstNameUpdated, entity.name);
            Assertions.assertNotNull(entity.second);
            Assertions.assertEquals(secondNameUpdated, entity.second.name);

            executeAsync(() -> {
                inTransaction(nestedEm -> {
                    final CascadeRefreshEntity nestedEntity = CascadeRefreshEntity.findByName(nestedEm, firstNameUpdated);
                    if (expectedException != null) {
                        Assertions.assertThrows(
                                expectedException,
                                () -> nestedEm.refresh(nestedEntity.second, LockModeType.PESSIMISTIC_WRITE)
                        );
                    } else {
                        nestedEm.refresh(nestedEntity.second, LockModeType.PESSIMISTIC_WRITE);
                        Assertions.assertEquals(LockModeType.PESSIMISTIC_WRITE, nestedEm.getLockMode(nestedEntity.second));
                        nestedEntity.second.name = secondNameAnotherUpdate;
                    }
                });
            });
        });
    }

    static Stream<Arguments> withCascadeRefresh() {
        final BiConsumer<EntityManager, CascadeRefreshEntity> refresh = (em, entity) -> em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        final BiConsumer<EntityManager, CascadeRefreshEntity> refreshNoLock = (em, entity) -> em.refresh(entity);
        final BiConsumer<EntityManager, CascadeRefreshEntity> withProfile = (em, entity) -> withProfile(em, () -> em.refresh(entity, LockModeType.PESSIMISTIC_WRITE));
        final BiConsumer<EntityManager, CascadeRefreshEntity> withGraph = (em, entity) -> withGraph(em, NoCascadeRefreshEntity.class, () -> em.refresh(entity, LockModeType.PESSIMISTIC_WRITE));
        return Stream.of(
                /*
                Hibernate:
                    select
                        ne1_0.id,
                        ne1_0.name,
                        ne1_0.version
                    from
                        NestedEntity ne1_0
                    where
                        ne1_0.id=?
                Hibernate:
                    select
                        cre1_0.id,
                        cre1_0.name,
                        s1_0.id,
                        s1_0.name,
                        s1_0.version,
                        cre1_0.version
                    from
                        CascadeRefreshEntity cre1_0
                    left join
                        NestedEntity s1_0
                            on s1_0.id=cre1_0.second_id
                    where
                        cre1_0.id=? for update
                 */
                arguments(named("refresh", refresh), true, PessimisticLockException.class),
                /*
                Hibernate:
                    select
                        ne1_0.id,
                        ne1_0.name,
                        ne1_0.version
                    from
                        NestedEntity ne1_0
                    where
                        ne1_0.id=?
                Hibernate:
                    select
                        cre1_0.id,
                        cre1_0.name,
                        s1_0.id,
                        s1_0.name,
                        s1_0.version,
                        cre1_0.version
                    from
                        CascadeRefreshEntity cre1_0
                    left join
                        NestedEntity s1_0
                            on s1_0.id=cre1_0.second_id
                    where
                        cre1_0.id=?
                 */
                arguments(named("refresh (no lock)", refreshNoLock), false, null),
                /*
                Hibernate:
                    select
                        ne1_0.id,
                        ne1_0.name,
                        ne1_0.version
                    from
                        NestedEntity ne1_0
                    where
                        ne1_0.id=?
                Hibernate:
                    select
                        cre1_0.id,
                        cre1_0.name,
                        cre1_0.second_id,
                        cre1_0.version
                    from
                        CascadeRefreshEntity cre1_0
                    where
                        cre1_0.id=? for update
                 */
                arguments(named("with profile", withProfile), true, null),
                /*
                Hibernate:
                    select
                        ne1_0.id,
                        ne1_0.name,
                        ne1_0.version
                    from
                        NestedEntity ne1_0
                    where
                        ne1_0.id=?
                Hibernate:
                    select
                        cre1_0.id,
                        cre1_0.name,
                        cre1_0.second_id,
                        cre1_0.version
                    from
                        CascadeRefreshEntity cre1_0
                    where
                        cre1_0.id=? for update
                 */
                arguments(named("with graph", withGraph), true, null)
        );
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource
    void withoutCascadeRefresh(final BiConsumer<EntityManager, NoCascadeRefreshEntity> consumer, final Class<Exception> expectedException) {

        final String firstName = "first entity";
        final String secondName = "second entity";
        final String firstNameUpdated = firstName + " (UPDATED)";
        final String secondNameUpdated = secondName + " (UPDATED)";
        final String secondNameAnotherUpdate = secondName + " (UPDATEDx2)";

        inTransaction(em -> {
            final NestedEntity nested = new NestedEntity();
            nested.name = secondName;

            final NoCascadeRefreshEntity entity = new NoCascadeRefreshEntity();
            entity.name = firstName;
            entity.second = nested;
            em.persist(entity);
        });

        inTransaction(em -> {

            final NoCascadeRefreshEntity entity = NoCascadeRefreshEntity.findByName(em, firstName);
            Assertions.assertNotNull(entity.second);

            executeAsync(() -> {
                inTransaction(nestedEm -> {
                    final NoCascadeRefreshEntity nestedEntity = NoCascadeRefreshEntity.findByName(nestedEm, firstName);
                    nestedEntity.name = firstNameUpdated;
                    Assertions.assertNotNull(nestedEntity.second);
                    nestedEntity.second.name = secondNameUpdated;
                    Assertions.assertTrue(nestedEm.contains(nestedEntity.second));
                });
            });

            consumer.accept(em, entity);

            Assertions.assertEquals(firstNameUpdated, entity.name);
            Assertions.assertNotNull(entity.second);
            Assertions.assertEquals(secondName, entity.second.name);

            executeAsync(() -> {
                inTransaction(nestedEm -> {
                    final NoCascadeRefreshEntity nestedEntity = NoCascadeRefreshEntity.findByName(nestedEm, firstNameUpdated);
                    if (expectedException != null) {
                        Assertions.assertThrows(
                                expectedException,
                                () -> nestedEm.refresh(nestedEntity.second, LockModeType.PESSIMISTIC_WRITE)
                        );
                    } else {
                        nestedEm.refresh(nestedEntity.second, LockModeType.PESSIMISTIC_WRITE);
                        Assertions.assertEquals(LockModeType.PESSIMISTIC_WRITE, nestedEm.getLockMode(nestedEntity.second));
                        nestedEntity.second.name = secondNameAnotherUpdate;
                    }
                });
            });
        });
    }

    static Stream<Arguments> withoutCascadeRefresh() {
        final BiConsumer<EntityManager, NoCascadeRefreshEntity> refresh = (em, entity) -> em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        final BiConsumer<EntityManager, NoCascadeRefreshEntity> refreshNoLock = (em, entity) -> em.refresh(entity);
        final BiConsumer<EntityManager, NoCascadeRefreshEntity> withProfile = (em, entity) -> withProfile(em, () -> em.refresh(entity, LockModeType.PESSIMISTIC_WRITE));
        final BiConsumer<EntityManager, NoCascadeRefreshEntity> withGraph = (em, entity) -> withGraph(em, NoCascadeRefreshEntity.class, () -> em.refresh(entity, LockModeType.PESSIMISTIC_WRITE));
        return Stream.of(
                /*
                Hibernate:
                    select
                        ncre1_0.id,
                        ncre1_0.name,
                        s1_0.id,
                        s1_0.name,
                        s1_0.version,
                        ncre1_0.version
                    from
                        NoCascadeRefreshEntity ncre1_0
                    left join
                        NestedEntity s1_0
                            on s1_0.id=ncre1_0.second_id
                    where
                        ncre1_0.id=? for update
                 */
                arguments(named("refresh", refresh), PessimisticLockException.class),
                /*
                Hibernate:
                    select
                        ncre1_0.id,
                        ncre1_0.name,
                        s1_0.id,
                        s1_0.name,
                        s1_0.version,
                        ncre1_0.version
                    from
                        NoCascadeRefreshEntity ncre1_0
                    left join
                        NestedEntity s1_0
                            on s1_0.id=ncre1_0.second_id
                    where
                        ncre1_0.id=?
                 */
                arguments(named("refresh (no lock)", refreshNoLock), null),
                /*
                Hibernate:
                    select
                        ncre1_0.id,
                        ncre1_0.name,
                        ncre1_0.second_id,
                        ncre1_0.version
                    from
                        NoCascadeRefreshEntity ncre1_0
                    where
                        ncre1_0.id=? for update
                 */
                arguments(named("with profile", withProfile), null),
                /*
                Hibernate:
                    select
                        ncre1_0.id,
                        ncre1_0.name,
                        ncre1_0.second_id,
                        ncre1_0.version
                    from
                        NoCascadeRefreshEntity ncre1_0
                    where
                        ncre1_0.id=? for update
                 */
                arguments(named("with graph", withGraph), null)
        );
    }

    private void inTransaction(final Consumer<EntityManager> tx) {
        final EntityManager em = entityManagerFactory.createEntityManager();
        final EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        try {
            tx.accept(em);
            if (transaction.getRollbackOnly()) {
                transaction.rollback();
            } else {
                transaction.commit();
            }
        } catch (final Exception e) {
            transaction.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    private void executeAsync(final Runnable runnable) {
        try {
            executor.submit(runnable).get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private static void withProfile(final EntityManager em, final Runnable runnable) {
        boolean profileAvailable = true;
        try {
            ((SessionImpl) em).enableFetchProfile("onRefresh");
        } catch (final PersistenceException unused) {
            // profile not available
            profileAvailable = false;
        }
        try {
            runnable.run();
        } finally {
            if (profileAvailable) {
                ((SessionImpl) em).disableFetchProfile("onRefresh");
            }
        }
    }

    private static void withGraph(final EntityManager em, final Class<?> entityClass, final Runnable runnable) {
        // TODO check if the graph is clear
        ((SessionImpl) em).getLoadQueryInfluencers()
                .getEffectiveEntityGraph()
                .applyConfiguredGraph(
                        Map.of(
                                "jakarta.persistence.fetchgraph", ((SessionImpl) em).createEntityGraph(entityClass)
                        )
                );
        try {
            runnable.run();
        } finally {
            ((SessionImpl) em).getLoadQueryInfluencers().getEffectiveEntityGraph().clear();
        }
    }
}
