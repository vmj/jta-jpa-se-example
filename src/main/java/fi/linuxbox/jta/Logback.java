package fi.linuxbox.jta;

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.status.OnConsoleStatusListener;

import static ch.qos.logback.classic.Level.*;

public class Logback extends BasicConfigurator {
    @Override
    public void configure(final LoggerContext lc) {
        super.configure(lc);
        lc.getStatusManager().add(new OnConsoleStatusListener());

        // "Finding logger config" logging
        //StatusPrinter.print(lc);

        lc.getLogger("ROOT").setLevel(DEBUG);

        // App logging
        //  - use error for transaction rollbacks
        //  - use info for explanatory logging
        //  - use debug for transaction management logging
        //  - use trace for resource management logging
        lc.getLogger("fi.linuxbox").setLevel(INFO);

        // BTM logging
        //  - warn will warn about missing server ID, which is fine for testing
        //  - info shows the version, jvm unique ID, and shutdown notice
        //  - debug is mighty verbose
        lc.getLogger("bitronix.tm").setLevel(ERROR);

        // Hibernate logging
        lc.getLogger("org.hibernate").setLevel(WARN);
        //  - SQL logging as it is sent to JDBC
        //lc.getLogger("org.hibernate.SQL").setLevel(TRACE);
        //  - parameter binding logging
        //lc.getLogger("org.hibernate.type.descriptor.sql").setLevel(TRACE);
        //  - DDL logging (WARN warns about not being able to drop non-existing things)
        lc.getLogger("org.hibernate.tool.schema").setLevel(ERROR);
        //  - cache activity
        //lc.getLogger("org.hibernate.cache").setLevel(TRACE);
        //  - this I haven't seen, so I dunno...
        //lc.getLogger("org.hibernate.hql.internal.ast.AST").setLevel(TRACE);

        // JBoss logging
        //  - just logs which logging backend it has chosen
        lc.getLogger("org.jboss.logging").setLevel(INFO);
    }
}
