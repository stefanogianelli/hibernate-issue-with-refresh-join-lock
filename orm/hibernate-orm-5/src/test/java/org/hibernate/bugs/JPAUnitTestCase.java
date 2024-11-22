package org.hibernate.bugs;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.LockModeType;
import javax.persistence.Persistence;

import org.hibernate.bugs.entity.FirstEntity;
import org.hibernate.bugs.entity.SecondEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM, using the Java Persistence API.
 */
public class JPAUnitTestCase {

    private EntityManagerFactory entityManagerFactory;

    @Before
    public void init() {
        entityManagerFactory = Persistence.createEntityManagerFactory("templatePU");
    }

    @After
    public void destroy() {
        entityManagerFactory.close();
    }

    // Entities are auto-discovered, so just add them anywhere on class-path
    // Add your tests, using standard JUnit.
    @Test
    public void hhh18879Test() {

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
                firstentit0_.id as id1_0_0_,
                firstentit0_.name as name2_0_0_,
                firstentit0_.second_id as second_i4_0_0_,
                firstentit0_.version as version3_0_0_,
                secondenti1_.id as id1_1_1_,
                secondenti1_.name as name2_1_1_,
                secondenti1_.version as version3_1_1_
            from
                FirstEntity firstentit0_
            left outer join
                SecondEntity secondenti1_
                    on firstentit0_.second_id=secondenti1_.id
            where
                firstentit0_.id=?
        Hibernate:
            select
                firstentit0_.id as id1_0_0_,
                firstentit0_.name as name2_0_0_,
                firstentit0_.second_id as second_i4_0_0_,
                firstentit0_.version as version3_0_0_
            from
                FirstEntity firstentit0_
            where
                firstentit0_.id=? for update
         */
        em.refresh(em.find(FirstEntity.class, first.id), LockModeType.PESSIMISTIC_WRITE);

        em.getTransaction().rollback();
        em.close();
    }
}
