package org.hibernate.bugs;

import org.hibernate.bugs.entity.FirstEntity;
import org.hibernate.bugs.entity.SecondEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Persistence;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM, using the Java Persistence API.
 */
class JPAUnitTestCase {

    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void init() {
        entityManagerFactory = Persistence.createEntityManagerFactory("templatePU");
    }

    @AfterEach
    void destroy() {
        entityManagerFactory.close();
    }

    @Test
    void hhh18879Test() {

        final EntityManager em = entityManagerFactory.createEntityManager();
        em.getTransaction().begin();

        final SecondEntity second = new SecondEntity();
        second.name = "second entity";

        final FirstEntity first = new FirstEntity();
        first.name = "first entity";
        first.second = second;
        em.persist(first);

        em.flush();
        em.clear();

        /*
        Hibernate:
            select
                fe1_0.id,
                fe1_0.name,
                s1_0.id,
                s1_0.name,
                s1_0.version,
                fe1_0.version
            from
                FirstEntity fe1_0
            left join
                SecondEntity s1_0
                    on s1_0.id=fe1_0.second_id
            where
                fe1_0.id=?
        Hibernate:
            select
                fe1_0.id,
                fe1_0.name,
                s1_0.id,
                s1_0.name,
                s1_0.version,
                fe1_0.version
            from
                FirstEntity fe1_0
            left join
                SecondEntity s1_0
                    on s1_0.id=fe1_0.second_id
            where
                fe1_0.id=? for update
         */
        em.refresh(em.find(FirstEntity.class, first.id), LockModeType.PESSIMISTIC_WRITE);

        em.getTransaction().rollback();
        em.close();
    }
}
