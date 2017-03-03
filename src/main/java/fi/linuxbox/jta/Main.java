package fi.linuxbox.jta;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import org.apache.derby.jdbc.EmbeddedXADataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.UserTransaction;
import java.sql.SQLException;
import java.util.List;

class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) throws Exception {
        log.trace("Initializing (JTA) Data Sources");
        initDerbyDataSource("users1", "jdbc/testDS1");
        initDerbyDataSource("users2", "jdbc/testDS2");

        log.trace("Initializing (JPA) Entity Manager Factories");
        EntityManagerFactory emf1 = Persistence.createEntityManagerFactory("testPU1");
        EntityManagerFactory emf2 = Persistence.createEntityManagerFactory("testPU2");

        try {
            createUserAndMoveUsers(emf1, emf2);
        } finally {
            log.trace("Closing (JPA) Entity Manager Factories");
            emf1.close();
            emf2.close();
            log.trace("Closing (JTA) Transaction Manager");
            TransactionManagerServices.getTransactionManager().shutdown();
        }

        log.trace("Shutting down (JTA) Data Sources");
        shutdownDerbyDataSource("users1");
        shutdownDerbyDataSource("users2");
    }

    private static void createUserAndMoveUsers(final EntityManagerFactory emf1, final EntityManagerFactory emf2) throws Exception {
        UserTransaction tx = TransactionManagerServices.getTransactionManager();
        try {
            log.debug("Starting Transaction");
            tx.begin();
            log.info("Persisting one new User in users1");
            createUser(emf1);
            log.debug("Committing Transaction");
            tx.commit();
        } catch (final Exception e) {
            log.error("Failed to add user to users1", e);
            tx.rollback();
            throw e;
        }

        try {
            log.debug("Starting Transaction");
            tx.begin();
            log.info("Moving users from users1 to users2");
            moveUsers(emf1, emf2);
            log.debug("Committing Transaction");
            tx.commit();
        } catch (final Exception e) {
            log.error("Failed to move users; none was moved", e);
            tx.rollback();
            throw e;
        }

        try {
            log.debug("Starting Transaction");
            tx.begin();
            log.info("Listing users in users2");
            listUsers(emf2);
            log.debug("Committing Transaction");
            tx.commit();
        } catch (final Exception e) {
            log.error("Failed to list users", e);
            tx.rollback();
            throw e;
        }
    }

    private static void createUser(final EntityManagerFactory emf) {
        log.trace("Obtaining (JPA) Entity Manager");
        EntityManager em = emf.createEntityManager();

        try {
            User user = new User("Test User");
            em.persist(user);
        } finally {
            log.trace("Closing (JPA) Entity Manager");
            em.close();
        }
    }

    private static void moveUsers(final EntityManagerFactory emf1, final EntityManagerFactory emf2) {
        log.trace("Obtaining (JPA) Entity Managers");
        EntityManager em1 = emf1.createEntityManager();
        EntityManager em2 = emf2.createEntityManager();

        log.info(" - Querying for users in users1");
        List<User> users1 = (List<User>) em1.createQuery("select u from fi.linuxbox.jta.User u").getResultList();

        try {
            log.info(" - Moving those users to users2");
            for (User user1 : users1) {
                User user2 = new User(user1.getName());
                log.info("   - moving a user");
                em2.persist(user2);
                em1.remove(user1);
            }
        } finally {
            log.trace("Closing (JPA) Entity Managers");
            em1.close();
            em2.close();
        }
    }

    private static void listUsers(final EntityManagerFactory emf) {
        log.trace("Obtaining (JPA) Entity Manager");
        EntityManager em = emf.createEntityManager();

        log.info(" - Querying for users in users2");
        List<User> users = (List<User>) em.createQuery("select u from fi.linuxbox.jta.User u").getResultList();

        for (User user : users) {
            log.info("   - " + user.getId() + ": " + user.getName());
        }

        log.trace("Closing (JPA) Entity Manager");
        em.close();
    }

    /**
     * Hibernate needs a DataSource.
     * This method initializes one using BTM pooling data source as the implementation,
     * and Derby as the driver.
     *
     * @param databaseName
     *      The Derby database name (the directory name).
     * @param jndiName
     *      Hibernate will find the data source in JNDI by this name.
     *      This is the same name as used in persistence.xml, jta-data-source element.
     */
    private static void initDerbyDataSource(final String databaseName, final String jndiName) {
        final PoolingDataSource ds = new PoolingDataSource();

        // Configure the pool
        // https://github.com/bitronix/btm/wiki/JDBC-pools-configuration
        ds.setAllowLocalTransactions(true); // default is false and good for production; true allows Hibernate to execute DDLs
        ds.setMinPoolSize(1); // default is 0
        ds.setMaxPoolSize(3); // in this example, this could be 1 (we only have one thread)
        ds.setShareTransactionConnections(true); // defaults is false only for backwards compatibility
        ds.setPreparedStatementCacheSize(10); // default is 0 (disabled)
        ds.setEnableJdbc4ConnectionTest(true); // default is false, but Derby supports JDBC4

        // Use Derby XA driver and configure the driver properties
        // https://db.apache.org/derby/docs/10.13/devguide/cdevresman89722.html
        ds.setClassName("org.apache.derby.jdbc.EmbeddedXADataSource");
        ds.getDriverProperties().put("databaseName", databaseName); // mandatory
        ds.getDriverProperties().put("createDatabase", "create"); // in production you will probably not want this

        // Make the data source available to Hibernate via JNDI
        ds.setUniqueName(jndiName);

        log.trace("Initializing " + databaseName + " pooled data source");
        ds.init();
    }

    /**
     * Shutting down Derby is derby specific mechanism.  BTM will not do it for us.
     *
     * @param databaseName The Derby database name (the directory name).
     */
    private static void shutdownDerbyDataSource(final String databaseName) {
        // https://db.apache.org/derby/docs/10.13/devguide/cdevresman92946.html
        // $DERBY_HOME/libexec/demo/programs/simple/SimpleApp.java
        EmbeddedXADataSource ds = new EmbeddedXADataSource();
        ds.setDatabaseName(databaseName);
        ds.setShutdownDatabase("shutdown");
        try {
            log.trace("Shutting down " + databaseName + " data source");
            ds.getConnection();
        } catch (final SQLException e) {
            // Shutting down a database always throws an SQLException,
            // but the error code and SQL state are very specific for successful
            // shutdown.  These are for single database shutdown; Derby system
            // shutdown would have different error codes and SQL states.
            if (e.getErrorCode() != 45000 || !"08006".equals(e.getSQLState())) {
                log.error("Failed to shutdown database " + databaseName + "; Derby will try to recover on next run", e);
            }
        }
    }
}
