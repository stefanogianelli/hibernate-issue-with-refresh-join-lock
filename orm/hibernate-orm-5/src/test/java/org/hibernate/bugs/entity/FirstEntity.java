package org.hibernate.bugs.entity;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Version;

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
