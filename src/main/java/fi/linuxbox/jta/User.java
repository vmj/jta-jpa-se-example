package fi.linuxbox.jta;

import javax.persistence.*;

@Entity(name = "users")
public class User {
    @Id
    @GeneratedValue
    private Long id;
    private String name;

    public User() {

    }

    public User(final String name) {
        this.name = name;
    }

    public final Long getId() {
        return id;
    }

    public final String getName() {
        return name;
    }
}
