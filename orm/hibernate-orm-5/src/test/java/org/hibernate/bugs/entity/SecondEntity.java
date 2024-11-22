package org.hibernate.bugs.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Version;

@Entity
public class SecondEntity {

    @Id
    @GeneratedValue
    public Long id;

    @Version
    private Long version;

    public String name;

    @Override
    public String toString() {
        return "Second{" + "id=" + id + ", version=" + version + ", name='" + name + '\'' + '}';
    }
}
