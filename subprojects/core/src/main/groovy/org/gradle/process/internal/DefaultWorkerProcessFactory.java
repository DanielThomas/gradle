/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.logging.LogLevel;
import org.gradle.cli.CommandLineConverter;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.serialize.Decoder;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectionAcceptor;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.internal.child.ApplicationClassesInSystemClassLoaderWorkerFactory;
import org.gradle.process.internal.child.SystemApplicationClassLoaderWorker;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultWorkerProcessFactory implements Factory<WorkerProcessBuilder> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkerProcessFactory.class);
    public static final Class<?>[] WORKER_REQUIRED_CLASSES = new Class<?>[] {
        SystemApplicationClassLoaderWorker.class,
        Decoder.class,
        ClassLoaderObjectInputStream.class,
        CommandLineConverter.class,
        ConsoleMetaData.class,
        ImmutableSet.class,
        LoggerFactory.class,
        SLF4JBridgeHandler.class
    };
    public static final String[] TEST_WORKER_REQUIRED_CLASSNAMES = new String[] {
        "org/gradle/api/internal/tasks/testing/junit/JUnitTestFramework.class",
        "net/rubygrapefruit/platform/NativeIntegrationUnavailableException.class",
        "com/esotericsoftware/kryo/KryoException.class",
        "org/apache/commons/lang/StringUtils.class",
        "org/junit/runner/notification/RunListener.class"
    };

    private final LogLevel workerLogLevel;
    private final MessagingServer server;
    private final IdGenerator<?> idGenerator;
    private final File gradleUserHomeDir;
    private final ExecHandleFactory execHandleFactory;
    private final ApplicationClassesInSystemClassLoaderWorkerFactory workerFactory;
    private int connectTimeoutSeconds = 120;

    public DefaultWorkerProcessFactory(LogLevel workerLogLevel, MessagingServer server, ClassPathRegistry classPathRegistry, IdGenerator<?> idGenerator,
                                       File gradleUserHomeDir, TemporaryFileProvider temporaryFileProvider, ExecHandleFactory execHandleFactory) {
        this.workerLogLevel = workerLogLevel;
        this.server = server;
        this.idGenerator = idGenerator;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.execHandleFactory = execHandleFactory;
        workerFactory = new ApplicationClassesInSystemClassLoaderWorkerFactory(classPathRegistry, temporaryFileProvider);
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public WorkerProcessBuilder create() {
        return new DefaultWorkerProcessBuilder();
    }

    private class DefaultWorkerProcessBuilder extends WorkerProcessBuilder {
        public DefaultWorkerProcessBuilder() {
            super(execHandleFactory.newJavaExec());
            setLogLevel(workerLogLevel);
            setGradleUserHomeDir(gradleUserHomeDir);
        }

        @Override
        public WorkerProcess build() {
            if (getWorker() == null) {
                throw new IllegalStateException("No worker action specified for this worker process.");
            }

            final DefaultWorkerProcess workerProcess = new DefaultWorkerProcess(connectTimeoutSeconds, TimeUnit.SECONDS);
            ConnectionAcceptor acceptor = server.accept(new Action<ObjectConnection>() {
                public void execute(ObjectConnection connection) {
                    workerProcess.onConnect(connection);
                }
            });
            workerProcess.startAccepting(acceptor);
            Address localAddress = acceptor.getAddress();

            // Build configuration for GradleWorkerMain
            List<Class<?>> classpathClasses = Lists.newArrayList(WORKER_REQUIRED_CLASSES);
            classpathClasses.add(getWorker().getClass());

            List<URL> implementationClassPath = CollectionUtils.collect(classpathClasses, new Transformer<URL, Class<?>>() {
                @Override
                public URL transform(Class<?> aClass) {
                    try {
                        return ClasspathUtil.getClasspathForClass(aClass).toURI().toURL();
                    } catch (MalformedURLException e) {
                        throw new UncheckedException(e);
                    }
                }
            });

            implementationClassPath.addAll(CollectionUtils.collect(TEST_WORKER_REQUIRED_CLASSNAMES, new Transformer<URL, String>() {
                @Override
                public URL transform(String className) {
                    try {
                        return ClasspathUtil.getClasspathForResource(getWorker().getClass().getClassLoader(), className).toURI().toURL();
                    } catch (MalformedURLException e) {
                        throw new UncheckedException(e);
                    }
                }
            }));

            Object id = idGenerator.generateId();
            String displayName = getBaseName() + " " + id;

            LOGGER.debug("Creating {}", displayName);
            LOGGER.debug("Using application classpath {}", getApplicationClasspath());
            LOGGER.debug("Using implementation classpath {}", implementationClassPath);

            JavaExecHandleBuilder javaCommand = getJavaCommand();
            javaCommand.setDisplayName(displayName);

            workerFactory.prepareJavaCommand(id, displayName, this, implementationClassPath, localAddress, javaCommand);

            javaCommand.args("'" + displayName + "'");
            ExecHandle execHandle = javaCommand.build();

            workerProcess.setExecHandle(execHandle);

            return workerProcess;
        }
    }
}
