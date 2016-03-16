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

package org.gradle.tooling;

import org.gradle.tooling.composite.BuildIdentity;

/**
 * Thrown when a Gradle composite build fails or when a model cannot be built.
 *
 * @since 2.13
 */
public class CompositeBuildException extends BuildException {
    private final BuildIdentity buildIdentity;
    public CompositeBuildException(String message, Throwable throwable, BuildIdentity buildIdentity) {
        super(message, throwable);
        this.buildIdentity = buildIdentity;
    }

    public boolean causedBy(BuildIdentity buildIdentity) {
        return this.buildIdentity.equals(buildIdentity);
    }
}
