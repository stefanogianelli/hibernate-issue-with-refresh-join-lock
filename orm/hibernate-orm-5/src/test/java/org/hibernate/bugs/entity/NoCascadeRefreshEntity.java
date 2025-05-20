package org.hibernate.bugs.entity;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.Version;

@Entity
@NamedQuery(name = "NoCascadeRefreshEntity.findByName", query = "select e from NoCascadeRefreshEntity e where e.name = :name")
public class NoCascadeRefreshEntity {

    public static NoCascadeRefreshEntity findByName(final EntityManager em, final String name) {
        return em.createNamedQuery("NoCascadeRefreshEntity.findByName", NoCascadeRefreshEntity.class)
                .setParameter("name", name)
                .getSingleResult();
    }

    @Id
    @GeneratedValue
    public Long id;

    @Version
    private Long version;

    public String name;

    @ManyToOne(cascade = CascadeType.PERSIST)
    public NestedEntity second;

    @Override
    public String toString() {
        return "NoCascadeRefreshEntity{" + "id=" + id + ", version=" + version + ", name='" + name + '\'' + ", second=" + second + '}';
    }
}
