package org.hibernate.bugs.entity;

import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.FetchProfile;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Version;

@Entity
@FetchProfile(name = "onRefresh", fetchOverrides = @FetchProfile.FetchOverride(entity = CascadeRefreshEntity.class, association = "second", fetch = FetchType.LAZY, mode = FetchMode.SELECT))
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

    @Override
    public String toString() {
        return "CascadeRefreshEntity{" + "id=" + id + ", version=" + version + ", name='" + name + '\'' + ", second=" + second + '}';
    }
}
