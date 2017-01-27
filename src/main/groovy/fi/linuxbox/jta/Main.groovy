package fi.linuxbox.jta

import bitronix.tm.TransactionManagerServices
import bitronix.tm.resource.jdbc.PoolingDataSource
import groovy.util.logging.Slf4j

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.Persistence
import javax.transaction.UserTransaction

@Slf4j
class Main {
    static void main(String... args) {
        // set the allowLocalTransactions=true to enable the
        // drop-and-create action from persistence.xml.
        initDerbyDataSource("users1", "jdbc/testDS1", false)
        initDerbyDataSource("users2", "jdbc/testDS2", false)

        EntityManagerFactory emf1 = Persistence.createEntityManagerFactory("testPU1")
        EntityManagerFactory emf2 = Persistence.createEntityManagerFactory("testPU2")

        try {
            createUserAndMoveUsers(emf1, emf2)
        } finally {
            emf1.close()
            emf2.close()
            TransactionManagerServices.transactionManager.shutdown()
        }
    }

    private static void createUserAndMoveUsers(final EntityManagerFactory emf1, final EntityManagerFactory emf2) {
        UserTransaction tx = TransactionManagerServices.getTransactionManager()
        try {
            tx.begin()
            log.info("Persisting one new User in users1")
            createUser(emf1)
            tx.commit()
        } catch (final Exception e) {
            tx.rollback()
            throw e
        }

        try {
            tx.begin()
            log.info("Moving users from users1 to users2")
            moveUsers(emf1, emf2)
            tx.commit()
        } catch (final Exception e) {
            tx.rollback()
            throw e
        }
    }

    private static void createUser(final EntityManagerFactory emf) {
        EntityManager em = emf.createEntityManager()

        try {
            User user = new User(name: "Test User")
            em.persist(user)
        } finally {
            em.close()
        }
    }

    private static void moveUsers(final EntityManagerFactory emf1, final EntityManagerFactory emf2) {
        EntityManager em1 = emf1.createEntityManager()
        EntityManager em2 = emf2.createEntityManager()

        List<User> users1 = (List<User>) em1.createQuery("select u from fi.linuxbox.jta.User u").getResultList()

        try {
            for (User user1 : users1) {
                User user2 = new User(name: user1.getName())
                log.info(" - moving a user")
                em2.persist(user2)
                em1.remove(user1)
            }
        } finally {
            em1.close()
            em2.close()
        }
    }

    /**
     * @param databaseName
     *      The Derby database name (the directory name).
     * @param jndiName
     *      Hibernate will find the data source in JNDI by this name.
     * @return
     */
    private static void initDerbyDataSource(final String databaseName, final String jndiName, final boolean allowLocalTransactions) {
        final PoolingDataSource ds = new PoolingDataSource()
        ds.allowLocalTransactions = allowLocalTransactions
        ds.className = "org.apache.derby.jdbc.EmbeddedXADataSource"
        ds.maxPoolSize = 3
        ds.uniqueName = jndiName
        ds.driverProperties["databaseName"] = databaseName
        ds.init()
    }
}
