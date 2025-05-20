package org.hibernate.bugs;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.Persistence;

import org.hibernate.bugs.entity.CascadeRefreshEntity;
import org.hibernate.bugs.entity.NestedEntity;
import org.hibernate.bugs.entity.NoCascadeRefreshEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
                new HashMap<String, String>() {{
                    put("javax.persistence.jdbc.url", DATABASE.getJdbcUrl());
                    put("javax.persistence.jdbc.user", DATABASE.getUsername());
                    put("javax.persistence.jdbc.password", DATABASE.getPassword());
                }}
        );
    }

    @AfterEach
    void destroy() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @Test
    void withCascadeRefresh() {

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
            Assertions.assertNotNull(entity.getSecond());

            executeAsync(() -> {
                inTransaction(nestedEm -> {
                    final CascadeRefreshEntity nestedEntity = CascadeRefreshEntity.findByName(nestedEm, firstName);
                    nestedEntity.name = firstNameUpdated;
                    Assertions.assertNotNull(nestedEntity.getSecond());
                    nestedEntity.getSecond().name = secondNameUpdated;
                    Assertions.assertTrue(nestedEm.contains(nestedEntity.getSecond()));
                });
            });

            /*
            Hibernate:
                select
                    nestedenti0_.id as id1_1_0_,
                    nestedenti0_.name as name2_1_0_,
                    nestedenti0_.version as version3_1_0_
                from
                    NestedEntity nestedenti0_
                where
                    nestedenti0_.id=?
            Hibernate:
                select
                    cascaderef0_.id as id1_0_0_,
                    cascaderef0_.name as name2_0_0_,
                    cascaderef0_.second_id as second_i4_0_0_,
                    cascaderef0_.version as version3_0_0_
                from
                    CascadeRefreshEntity cascaderef0_
                where
                    cascaderef0_.id=? for update
             */
            em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);

            Assertions.assertEquals(firstNameUpdated, entity.getName());
            Assertions.assertNotNull(entity.getSecond());
            Assertions.assertEquals(secondNameUpdated, entity.getSecond().getName());

            executeAsync(() -> {
                inTransaction(nestedEm -> {
                    final CascadeRefreshEntity nestedEntity = CascadeRefreshEntity.findByName(nestedEm, firstNameUpdated);
                    nestedEm.refresh(nestedEntity.getSecond(), LockModeType.PESSIMISTIC_WRITE);
                    Assertions.assertEquals(LockModeType.PESSIMISTIC_WRITE, nestedEm.getLockMode(nestedEntity.getSecond()));
                    nestedEntity.getSecond().name = secondNameAnotherUpdate;
                });
            });
        });
    }

    @Test
    void withCascadeRefreshNoLock() {

        final String firstName = "first entity";
        final String secondName = "second entity";
        final String firstNameUpdated = firstName + " (UPDATED)";
        final String secondNameUpdated = secondName + " (UPDATED)";

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
            Assertions.assertNotNull(entity.getSecond());

            executeAsync(() -> {
                inTransaction(nestedEm -> {
                    final CascadeRefreshEntity nestedEntity = CascadeRefreshEntity.findByName(nestedEm, firstName);
                    nestedEntity.name = firstNameUpdated;
                    Assertions.assertNotNull(nestedEntity.getSecond());
                    nestedEntity.getSecond().name = secondNameUpdated;
                    Assertions.assertTrue(nestedEm.contains(nestedEntity.getSecond()));
                });
            });

            /*
            Hibernate:
                select
                    nestedenti0_.id as id1_1_0_,
                    nestedenti0_.name as name2_1_0_,
                    nestedenti0_.version as version3_1_0_
                from
                    NestedEntity nestedenti0_
                where
                    nestedenti0_.id=?
            Hibernate:
                select
                    cascaderef0_.id as id1_0_1_,
                    cascaderef0_.name as name2_0_1_,
                    cascaderef0_.second_id as second_i4_0_1_,
                    cascaderef0_.version as version3_0_1_,
                    nestedenti1_.id as id1_1_0_,
                    nestedenti1_.name as name2_1_0_,
                    nestedenti1_.version as version3_1_0_
                from
                    CascadeRefreshEntity cascaderef0_
                left outer join
                    NestedEntity nestedenti1_
                        on cascaderef0_.second_id=nestedenti1_.id
                where
                    cascaderef0_.id=?
             */
            em.refresh(entity);

            Assertions.assertEquals(firstNameUpdated, entity.getName());
            Assertions.assertNotNull(entity.getSecond());
            Assertions.assertEquals(secondNameUpdated, entity.getSecond().getName());
        });
    }

    @Test
    void withoutCascadeRefresh() {

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

            /*
            Hibernate:
                select
                    nocascader0_.id as id1_2_0_,
                    nocascader0_.name as name2_2_0_,
                    nocascader0_.second_id as second_i4_2_0_,
                    nocascader0_.version as version3_2_0_
                from
                    NoCascadeRefreshEntity nocascader0_
                where
                    nocascader0_.id=? for update
             */
            em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);

            Assertions.assertEquals(firstNameUpdated, entity.name);
            Assertions.assertNotNull(entity.second);
            Assertions.assertEquals(secondName, entity.second.name);

            executeAsync(() -> {
                inTransaction(nestedEm -> {
                    final NoCascadeRefreshEntity nestedEntity = NoCascadeRefreshEntity.findByName(nestedEm, firstNameUpdated);
                    nestedEm.refresh(nestedEntity.second, LockModeType.PESSIMISTIC_WRITE);
                    Assertions.assertEquals(LockModeType.PESSIMISTIC_WRITE, nestedEm.getLockMode(nestedEntity.second));
                    nestedEntity.second.name = secondNameAnotherUpdate;
                });
            });
        });
    }

    @Test
    void withoutCascadeRefreshNoLock() {

        final String firstName = "first entity";
        final String secondName = "second entity";
        final String firstNameUpdated = firstName + " (UPDATED)";
        final String secondNameUpdated = secondName + " (UPDATED)";

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

            /*
            Hibernate:
                select
                    nocascader0_.id as id1_2_0_,
                    nocascader0_.name as name2_2_0_,
                    nocascader0_.second_id as second_i4_2_0_,
                    nocascader0_.version as version3_2_0_
                from
                    NoCascadeRefreshEntity nocascader0_
                where
                    nocascader0_.id=?
             */
            em.refresh(entity);

            Assertions.assertEquals(firstNameUpdated, entity.name);
            Assertions.assertNotNull(entity.second);
            Assertions.assertEquals(secondName, entity.second.name);
        });
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
}
