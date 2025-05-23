package org.hibernate.bugs.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

@Entity
public class NestedEntity {

    @Id
    @GeneratedValue
    public Long id;

    @Version
    private Long version;

    public String name;

    @Override
    public String toString() {
        return "NestedEntity{" + "id=" + id + ", version=" + version + ", name='" + name + '\'' + '}';
    }
}
