/*

Copyright (C) SYSTAP, LLC DBA Blazegraph 2006-2016.  All rights reserved.

Contact:
     SYSTAP, LLC DBA Blazegraph
     2501 Calvert ST NW #106
     Washington, DC 20008
     licenses@blazegraph.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

*/
package com.bigdata.jmx;

import java.lang.management.ManagementFactory;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.OptionConverter;

/**
 * JMX management of the log4j logger hierarchy, exposing the same object
 * names and level management interface as log4j 1.2's
 * <code>org.apache.log4j.jmx</code> package, which is not part of reload4j:
 * <ul>
 * <li><code>log4j:hierarchy=default</code>: attribute <code>threshold</code>
 * (read/write) and operation <code>addLoggerMBean(String name)</code>;</li>
 * <li><code>log4j:logger=NAME</code>: attributes <code>name</code> (read only)
 * and <code>priority</code> (read/write, a level name).</li>
 * </ul>
 */
public class Log4jHierarchyMBean implements DynamicMBean {

    private static final String THRESHOLD = "threshold";

    private static final String ADD_LOGGER_MBEAN = "addLoggerMBean";

    private final MBeanServer server;

    public Log4jHierarchyMBean() {
        this(ManagementFactory.getPlatformMBeanServer());
    }

    public Log4jHierarchyMBean(final MBeanServer server) {
        this.server = server;
    }

    /**
     * Registers an MBean for an existing logger (as log4j 1.2 did, the name
     * must be that of a logger already present in the repository).
     * 
     * @return The object name, or <code>null</code> if there is no such logger.
     */
    public ObjectName addLoggerMBean(final String name) throws JMException {
        final Logger logger = LogManager.exists(name);
        if (logger == null) {
            return null;
        }
        final ObjectName objectName = new ObjectName("log4j", "logger", name);
        if (!server.isRegistered(objectName)) {
            server.registerMBean(new LoggerMBean(logger), objectName);
        }
        return objectName;
    }

    @Override
    public Object getAttribute(final String attribute) throws AttributeNotFoundException {
        if (THRESHOLD.equals(attribute)) {
            return LogManager.getLoggerRepository().getThreshold().toString();
        }
        throw new AttributeNotFoundException(attribute);
    }

    @Override
    public void setAttribute(final Attribute attribute)
            throws AttributeNotFoundException, InvalidAttributeValueException {
        if (!THRESHOLD.equals(attribute.getName())) {
            throw new AttributeNotFoundException(attribute.getName());
        }
        final Object value = attribute.getValue();
        if (!(value instanceof String)) {
            throw new InvalidAttributeValueException(THRESHOLD + " must be a level name");
        }
        LogManager.getLoggerRepository().setThreshold((String) value);
    }

    @Override
    public AttributeList getAttributes(final String[] attributes) {
        return getAttributes(this, attributes);
    }

    @Override
    public AttributeList setAttributes(final AttributeList attributes) {
        return setAttributes(this, attributes);
    }

    @Override
    public Object invoke(final String actionName, final Object[] params, final String[] signature)
            throws MBeanException, ReflectionException {
        if (ADD_LOGGER_MBEAN.equals(actionName) && params != null && params.length == 1
                && params[0] instanceof String) {
            try {
                return addLoggerMBean((String) params[0]);
            } catch (JMException e) {
                throw new MBeanException(e);
            }
        }
        throw new ReflectionException(new NoSuchMethodException(actionName));
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return new MBeanInfo(getClass().getName(), "log4j logger hierarchy",
                new MBeanAttributeInfo[] { new MBeanAttributeInfo(THRESHOLD, String.class.getName(),
                        "The repository-wide threshold level", true, true, false) },
                null,
                new MBeanOperationInfo[] { new MBeanOperationInfo(ADD_LOGGER_MBEAN,
                        "Registers an MBean for the named logger",
                        new MBeanParameterInfo[] {
                                new MBeanParameterInfo("name", String.class.getName(), "logger name") },
                        ObjectName.class.getName(), MBeanOperationInfo.ACTION) },
                null);
    }

    /** The MBean of a single logger. */
    static class LoggerMBean implements DynamicMBean {

        private static final String NAME = "name";

        private static final String PRIORITY = "priority";

        private final Logger logger;

        LoggerMBean(final Logger logger) {
            this.logger = logger;
        }

        @Override
        public Object getAttribute(final String attribute) throws AttributeNotFoundException {
            if (NAME.equals(attribute)) {
                return logger.getName();
            }
            if (PRIORITY.equals(attribute)) {
                final Level level = logger.getLevel();
                return level == null ? null : level.toString();
            }
            throw new AttributeNotFoundException(attribute);
        }

        @Override
        public void setAttribute(final Attribute attribute)
                throws AttributeNotFoundException, InvalidAttributeValueException {
            if (!PRIORITY.equals(attribute.getName())) {
                throw new AttributeNotFoundException(attribute.getName());
            }
            final Object value = attribute.getValue();
            if (value != null && !(value instanceof String)) {
                throw new InvalidAttributeValueException(PRIORITY + " must be a level name");
            }
            logger.setLevel(value == null ? null : OptionConverter.toLevel((String) value, logger.getLevel()));
        }

        @Override
        public AttributeList getAttributes(final String[] attributes) {
            return Log4jHierarchyMBean.getAttributes(this, attributes);
        }

        @Override
        public AttributeList setAttributes(final AttributeList attributes) {
            return Log4jHierarchyMBean.setAttributes(this, attributes);
        }

        @Override
        public Object invoke(final String actionName, final Object[] params, final String[] signature)
                throws ReflectionException {
            throw new ReflectionException(new NoSuchMethodException(actionName));
        }

        @Override
        public MBeanInfo getMBeanInfo() {
            return new MBeanInfo(getClass().getName(), "log4j logger " + logger.getName(),
                    new MBeanAttributeInfo[] {
                            new MBeanAttributeInfo(NAME, String.class.getName(), "The logger name", true, false,
                                    false),
                            new MBeanAttributeInfo(PRIORITY, String.class.getName(), "The logger level", true,
                                    true, false) },
                    null, null, null);
        }
    }

    private static AttributeList getAttributes(final DynamicMBean mbean, final String[] attributes) {
        final AttributeList list = new AttributeList();
        for (String a : attributes) {
            try {
                list.add(new Attribute(a, mbean.getAttribute(a)));
            } catch (Exception e) {
                // omitted, per the DynamicMBean contract
            }
        }
        return list;
    }

    private static AttributeList setAttributes(final DynamicMBean mbean, final AttributeList attributes) {
        final AttributeList list = new AttributeList();
        for (Attribute a : attributes.asList()) {
            try {
                mbean.setAttribute(a);
                list.add(new Attribute(a.getName(), mbean.getAttribute(a.getName())));
            } catch (Exception e) {
                // omitted, per the DynamicMBean contract
            }
        }
        return list;
    }
}
