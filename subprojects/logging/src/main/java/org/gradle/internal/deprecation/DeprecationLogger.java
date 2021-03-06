/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.deprecation;

import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.internal.Factory;
import org.gradle.internal.featurelifecycle.DeprecatedUsageBuildOperationProgressBroadcaster;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.UsageLocationReporter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static org.gradle.internal.deprecation.Messages.thisBehaviourHasBeenDeprecatedAndIsScheduledToBeRemoved;
import static org.gradle.internal.deprecation.Messages.thisIsScheduledToBeRemoved;
import static org.gradle.internal.deprecation.Messages.thisWillBecomeAnError;
import static org.gradle.internal.deprecation.Messages.xHasBeenDeprecated;

@ThreadSafe
public class DeprecationLogger {

    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };

    private static LoggingDeprecatedFeatureHandler deprecatedFeatureHandler = new LoggingDeprecatedFeatureHandler();

    public synchronized static void init(UsageLocationReporter reporter, WarningMode warningMode, DeprecatedUsageBuildOperationProgressBroadcaster buildOperationProgressBroadcaster) {
        deprecatedFeatureHandler.init(reporter, warningMode, buildOperationProgressBroadcaster);
    }

    public synchronized static void reset() {
        deprecatedFeatureHandler.reset();
    }

    public synchronized static void reportSuppressedDeprecations() {
        deprecatedFeatureHandler.reportSuppressedDeprecations();
    }

    @Nullable
    public static Throwable getDeprecationFailure() {
        return deprecatedFeatureHandler.getDeprecationFailure();
    }

    // Output: ${feature} has been deprecated. This is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder deprecate(final String feature) {
        return new DeprecationMessageBuilder() {
            @Override
            DeprecationMessage build() {
                withSummary(xHasBeenDeprecated(feature));
                withRemovalDetails(thisIsScheduledToBeRemoved());
                return super.build();
            }
        };
    }

    // Output: ${feature} has been deprecated. This is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder deprecateIndirectUsage(String feature) {
        return deprecate(feature).withIndirectUsage();
    }

    // Output: ${feature} has been deprecated. This is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder deprecateBuildInvocationFeature(String feature) {
        return deprecate(feature).withBuildInvocation();
    }

    // Output: ${behaviour}. This behaviour has been deprecated and is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder deprecateBehaviour(final String behaviour) {
        return new DeprecationMessageBuilder() {
            @Override
            DeprecationMessage build() {
                withSummary(behaviour);
                withRemovalDetails(thisBehaviourHasBeenDeprecatedAndIsScheduledToBeRemoved());
                return super.build();
            }
        };
    }

    public static DeprecationMessageBuilder warnOfChangedBehaviour(final String behaviour) {
        return new DeprecationMessageBuilder() {
            @Override
            DeprecationMessage build() {
                withSummary(behaviour);
                withRemovalDetails("");
                withIndirectUsage();
                return super.build();
            }
        };
    }

    // Output: ${action} has been deprecated. This will fail with an error in Gradle X.
    public static DeprecationMessageBuilder deprecateAction(final String action) {
        return new DeprecationMessageBuilder() {
            @Override
            DeprecationMessage build() {
                withSummary(xHasBeenDeprecated(action));
                withRemovalDetails(thisWillBecomeAnError());
                return super.build();
            }
        };
    }

    // Output: The ${property} property has been deprecated. This is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder.DeprecateProperty deprecateProperty(String property) {
        return new DeprecationMessageBuilder.DeprecateProperty(property);
    }

    // Output: The ${parameter} named parameter has been deprecated. This is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder.DeprecateNamedParameter deprecateNamedParameter(String parameter) {
        return new DeprecationMessageBuilder.DeprecateNamedParameter(parameter);
    }

    // Output: The ${method} method has been deprecated. This is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder.DeprecateMethod deprecateMethod(String method) {
        return new DeprecationMessageBuilder.DeprecateMethod(method);
    }

    // Output: Using method ${invocation} has been deprecated. This will fail with an error in Gradle X.
    public static DeprecationMessageBuilder.DeprecateInvocation deprecateInvocation(String invocation) {
        return new DeprecationMessageBuilder.DeprecateInvocation(invocation);
    }

    // Output: The ${task} task has been deprecated. This is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder.DeprecateTask deprecateTask(String task) {
        return new DeprecationMessageBuilder.DeprecateTask(task);
    }

    // Output: The ${plugin} plugin has been deprecated. This is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder.DeprecatePlugin deprecatePlugin(String plugin) {
        return new DeprecationMessageBuilder.DeprecatePlugin(plugin);
    }

    // Output: Internal API ${api} has been deprecated. This is scheduled to be removed in Gradle X.
    public static DeprecationMessageBuilder.DeprecateInternalApi deprecateInternalApi(String api) {
        return new DeprecationMessageBuilder.DeprecateInternalApi(api);
    }

    public static DeprecationMessageBuilder.ConfigurationDeprecationTypeSelector deprecateConfiguration(String configuration) {
        return new DeprecationMessageBuilder.ConfigurationDeprecationTypeSelector(configuration);
    }

    static void nagUserWith(DeprecationMessageBuilder deprecationMessageBuilder, Class<?> calledFrom) {
        if (isEnabled()) {
            DeprecationMessage deprecationMessage = deprecationMessageBuilder.build();
            nagUserWith(deprecationMessage.toDeprecatedFeatureUsage(calledFrom));
        }
    }

    @Nullable
    public static <T> T whileDisabled(Factory<T> factory) {
        ENABLED.set(false);
        try {
            return factory.create();
        } finally {
            ENABLED.set(true);
        }
    }

    public static void whileDisabled(Runnable action) {
        ENABLED.set(false);
        try {
            action.run();
        } finally {
            ENABLED.set(true);
        }
    }

    private static boolean isEnabled() {
        return ENABLED.get();
    }

    private synchronized static void nagUserWith(DeprecatedFeatureUsage usage) {
        deprecatedFeatureHandler.featureUsed(usage);
    }
}
