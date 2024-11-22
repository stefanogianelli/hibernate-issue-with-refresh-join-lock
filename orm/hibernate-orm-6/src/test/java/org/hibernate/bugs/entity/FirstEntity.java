package org.hibernate.bugs.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

@Entity
public class FirstEntity {

    @Id
    @GeneratedValue
    public Long id;

    @Version
    private Long version;

    public String name;

    @ManyToOne(cascade = CascadeType.PERSIST)
    public SecondEntity second;

    @Override
    public String toString() {
        return "First{" + "id=" + id + ", version=" + version + ", name='" + name + '\'' + ", second=" + second + '}';
    }
}
