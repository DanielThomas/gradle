/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.StartParameter;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRuleProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactBuilder;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.composite.CompositeBuildDependencySubstitutions;
import org.gradle.api.internal.composite.CompositeProjectArtifactBuilder;
import org.gradle.api.internal.composite.DefaultBuildableCompositeBuildContext;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.internal.service.ServiceRegistry;

public class CompositeScopeServices {
    private final BuildRequestContext requestContext;
    private final boolean propagateFailures;

    public CompositeScopeServices(BuildRequestContext requestContext, boolean propagateFailures) {
        this.requestContext = requestContext;
        this.propagateFailures = propagateFailures;
    }

    public CompositeBuildContext createCompositeBuildContext() {
        return new DefaultBuildableCompositeBuildContext();
    }

    public CompositeContextBuilder createCompositeContextBuilder(StartParameter startParameter, ServiceRegistry compositeServices) {
        return new DefaultCompositeContextBuilder(startParameter, requestContext, compositeServices, propagateFailures);
    }

    public ProjectArtifactBuilder createCompositeProjectArtifactBuilder(CompositeBuildContext compositeBuildContext, GradleLauncherFactory gradleLauncherFactory, StartParameter startParameter, ServiceRegistry compositeServices) {
        return new CompositeProjectArtifactBuilder(compositeBuildContext, gradleLauncherFactory, startParameter, compositeServices);
    }

    public DependencySubstitutionRuleProvider createCompositeBuildDependencySubstitutions(CompositeBuildContext compositeBuildContext) {
        return new CompositeBuildDependencySubstitutions(compositeBuildContext);
    }

}
