package com.blazegraph.acceptance.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.bigdata.Banner;

/** Runtime log level management over JMX, registered at start-up by the Banner. */
@Tag("embedded")
@DisplayName("Log4j JMX management")
class Log4jJmxTest {

    @Test
    @DisplayName("EMB-06 log4j MBeans (log4j:hierarchy=default, log4j:logger=NAME) manage log levels")
    void log4jMBeans() throws Exception {
        Banner.banner();
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName hierarchy = new ObjectName("log4j:hierarchy=default");
        assertTrue(mbs.isRegistered(hierarchy));
        assertNotNull(mbs.getAttribute(hierarchy, "threshold"));

        // the Banner configures com.bigdata at WARN and registers its MBean
        ObjectName logger = new ObjectName("log4j", "logger", "com.bigdata");
        assertTrue(mbs.isRegistered(logger));
        assertEquals("com.bigdata", mbs.getAttribute(logger, "name"));
        String before = (String) mbs.getAttribute(logger, "priority");
        try {
            mbs.setAttribute(logger, new Attribute("priority", "ERROR"));
            assertEquals("ERROR", mbs.getAttribute(logger, "priority"));
            assertEquals("ERROR", org.apache.log4j.Logger.getLogger("com.bigdata").getLevel().toString());
        } finally {
            mbs.setAttribute(logger, new Attribute("priority", before));
        }

        // further loggers can be added through the hierarchy MBean
        org.apache.log4j.Logger.getLogger("com.blazegraph.acceptance.jmx");
        Object added = mbs.invoke(hierarchy, "addLoggerMBean", new Object[] { "com.blazegraph.acceptance.jmx" },
                new String[] { String.class.getName() });
        assertEquals(new ObjectName("log4j", "logger", "com.blazegraph.acceptance.jmx"), added);
    }
}
