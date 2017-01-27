package fi.linuxbox.jta

import groovy.transform.Immutable

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Immutable
@Entity(name = "users")
class User {
    @Id
    @GeneratedValue
    Long id

    String name
}
