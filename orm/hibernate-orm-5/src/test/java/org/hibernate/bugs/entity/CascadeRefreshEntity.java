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
@NamedQuery(name = "CascadeRefreshEntity.findByName", query = "select e from CascadeRefreshEntity e where e.name = :name")
public class CascadeRefreshEntity {

    public static CascadeRefreshEntity findByName(final EntityManager em, final String name) {
        return em.createNamedQuery("CascadeRefreshEntity.findByName", CascadeRefreshEntity.class)
                .setParameter("name", name)
                .getSingleResult();
    }

    @Id
    @GeneratedValue
    public Long id;

    @Version
    private Long version;

    public String name;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.REFRESH})
    public NestedEntity second;

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public NestedEntity getSecond() {
        return second;
    }

    @Override
    public String toString() {
        return "CascadeRefreshEntity{" + "id=" + id + ", version=" + version + ", name='" + name + '\'' + ", second=" + second + '}';
    }
}
