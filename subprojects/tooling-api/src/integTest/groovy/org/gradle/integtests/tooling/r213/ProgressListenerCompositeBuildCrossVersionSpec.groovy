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

package org.gradle.integtests.tooling.r213
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject
/**
 * Tooling client provides progress listener for composite model request
 */
class ProgressListenerCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    static final List<String> IGNORED_EVENTS = ['Validate distribution', '', 'Compiling script into cache']

    def "compare events from a composite build and a regular build with single build"() {
        given:
        def builds = createBuilds(1)

        when:
        def progressListenerForComposite = new CapturingProgressListener()
        def progressListenerForRegularBuild = new CapturingProgressListener()
        requestModels(builds, progressListenerForComposite, progressListenerForRegularBuild)

        then:
        progressListenerForComposite.eventDescriptions.size() > 0
        progressListenerForRegularBuild.eventDescriptions.each { eventDescription ->
            if (!(eventDescription in IGNORED_EVENTS)) {
                assert progressListenerForComposite.eventDescriptions.contains(eventDescription)
                progressListenerForComposite.eventDescriptions.remove(eventDescription)
            }
        }
    }

    def "compare events from a composite build and a regular build with 3 builds"() {
        given:
        def builds = createBuilds(3)

        when:
        def progressListenerForComposite = new CapturingProgressListener()
        def progressListenerForRegularBuild = new CapturingProgressListener()
        requestModels(builds, progressListenerForComposite, progressListenerForRegularBuild)

        then:
        progressListenerForComposite.eventDescriptions.size() > 0
        progressListenerForRegularBuild.eventDescriptions.each { eventDescription ->
            if (!(eventDescription in IGNORED_EVENTS)) {
                assert progressListenerForComposite.eventDescriptions.contains(eventDescription)
                progressListenerForComposite.eventDescriptions.remove(eventDescription)
            }
        }
    }

    def "compare events from task execution from a composite build and a regular build with 3 builds"() {
        given:
        def builds = createBuilds(3)

        when:
        def progressListenerForComposite = new CapturingProgressListener()
        def progressListenerForRegularBuild = new CapturingProgressListener()
        executeFirstBuild(builds, progressListenerForComposite, progressListenerForRegularBuild)

        then:
        progressListenerForComposite.eventDescriptions.size() > 0
        progressListenerForRegularBuild.eventDescriptions.each { eventDescription ->
            if (!(eventDescription in IGNORED_EVENTS)) {
                assert progressListenerForComposite.eventDescriptions.contains(eventDescription)
                progressListenerForComposite.eventDescriptions.remove(eventDescription)
            }
        }
    }

    private List<File> createBuilds(int numberOfBuilds) {
        def builds = (1..numberOfBuilds).collect {
            populate("build-$it") {
                buildFile << "apply plugin: 'java'"
            }
        }
        return builds
    }

    private void requestModels(List<File> builds, progressListenerForComposite, progressListenerForRegularBuild) {
        withCompositeConnection(builds) { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.addProgressListener(progressListenerForComposite)
            modelBuilder.get()
        }

        builds.each { buildDir ->
            GradleConnector connector = toolingApi.connector()
            connector.forProjectDirectory(buildDir.absoluteFile)
            toolingApi.withConnection(connector) { ProjectConnection connection ->
                def modelBuilder = connection.model(EclipseProject)
                modelBuilder.addProgressListener(progressListenerForRegularBuild)
                modelBuilder.get()
            }
        }
    }

    private void executeFirstBuild(List<File> builds, progressListenerForComposite, progressListenerForRegularBuild) {
        def buildId = createGradleBuildParticipant(builds[0]).toBuildIdentity()
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild(buildId)
            buildLauncher.forTasks("jar")
            buildLauncher.addProgressListener(progressListenerForComposite)
            buildLauncher.run()
        }

        GradleConnector connector = toolingApi.connector()
        connector.forProjectDirectory(builds[0].absoluteFile)
        toolingApi.withConnection(connector) { ProjectConnection connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks("jar")
            buildLauncher.addProgressListener(progressListenerForRegularBuild)
            buildLauncher.run()
        }
    }

    static class CapturingProgressListener implements ProgressListener {
        def eventDescriptions = []

        @Override
        void statusChanged(ProgressEvent event) {
            eventDescriptions.add(event.description)
        }
    }
}
