package fi.linuxbox.jta;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.UserTransaction;
import java.util.List;

class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) throws Exception {
        // set the allowLocalTransactions=true to enable the
        // drop-and-create action from persistence.xml.
        log.trace("Initializing (JTA) Data Sources");
        initDerbyDataSource("users1", "jdbc/testDS1", false);
        initDerbyDataSource("users2", "jdbc/testDS2", false);

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
     * @param allowLocalTransactions
     *      Whether to allow local transactions (those are needed for the drop-and-create actions).
     */
    private static void initDerbyDataSource(final String databaseName, final String jndiName, final boolean allowLocalTransactions) {
        final PoolingDataSource ds = new PoolingDataSource();

        // Configure the pool
        // https://github.com/bitronix/btm/wiki/JDBC-pools-configuration
        ds.setAllowLocalTransactions(allowLocalTransactions);
        ds.setMinPoolSize(1); // default is 0
        ds.setMaxPoolSize(3); // in this example, this could be 1 (we only have one thread)
        ds.setShareTransactionConnections(true); // defaults is false only for backwards comp
        ds.setPreparedStatementCacheSize(10); // default is 0 (disabled)
        ds.setEnableJdbc4ConnectionTest(true); // default is false, but Derby supports JDBC4

        // Use Derby XA driver and configure the driver properties
        ds.setClassName("org.apache.derby.jdbc.EmbeddedXADataSource");
        ds.getDriverProperties().put("databaseName", databaseName);

        // Make the data source available to Hibernate via JNDI
        ds.setUniqueName(jndiName);

        log.trace("Initializing " + databaseName + " pooled data source");
        ds.init();
    }
}
