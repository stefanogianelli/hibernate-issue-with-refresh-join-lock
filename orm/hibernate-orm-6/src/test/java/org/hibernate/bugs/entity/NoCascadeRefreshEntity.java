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
@FetchProfile(name = "onRefresh", fetchOverrides = @FetchProfile.FetchOverride(entity = NoCascadeRefreshEntity.class, association = "second", fetch = FetchType.LAZY, mode = FetchMode.SELECT))
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
