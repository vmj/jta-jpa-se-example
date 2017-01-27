package fi.linuxbox.jta

import ch.qos.logback.classic.BasicConfigurator
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.status.OnConsoleStatusListener
import ch.qos.logback.core.util.StatusPrinter

import static ch.qos.logback.classic.Level.*

class Logback extends BasicConfigurator {
    @Override
    void configure(final LoggerContext lc) {
        super.configure(lc)
        lc.statusManager.add(new OnConsoleStatusListener())

        // "Finding logger config" logging
        //StatusPrinter.print(lc)

        lc.getLogger("ROOT").level = DEBUG

        // App logging
        //  - use error for transaction rollbacks
        //  - use info for explanatory logging
        //  - use debug for transaction management logging
        //  - use trace for resource management logging
        lc.getLogger("fi.linuxbox").level = INFO

        // BTM logging
        //  - warn will warn about missing server ID, which is fine for testing
        //  - info shows the version, jvm unique ID, and shutdown notice
        //  - debug is mighty verbose
        lc.getLogger("bitronix.tm").level = ERROR

        // Hibernate logging
        lc.getLogger("org.hibernate").level = WARN
        //  - SQL logging as it is sent to JDBC
        //lc.getLogger("org.hibernate.SQL").level = TRACE
        //  - parameter binding logging
        //lc.getLogger("org.hibernate.type.descriptor.sql").level = TRACE
        //  - DDL logging
        //lc.getLogger("org.hibernate.tool.hbm2ddl").level = TRACE
        //  - cache activity
        //lc.getLogger("org.hibernate.cache").level = TRACE
        //  - this I haven't seen, so I dunno...
        //lc.getLogger("org.hibernate.hql.internal.ast.AST").level = TRACE

        // JBoss logging
        //  - just logs which logging backend it has chosen
        lc.getLogger("org.jboss.logging").level = INFO
    }
}
