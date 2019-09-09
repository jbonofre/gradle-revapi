/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.revapi

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class RevapiSpec extends IntegrationSpec {

    def 'fails when comparing produced jar versus some random other jar'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                compile 'one.util:streamex:0.7.0'
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = '0.11.1'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        def file = new File(projectDir, "src/main/java/foo/Foo.java")
        file.getParentFile().mkdirs()
        file << '''
            import one.util.streamex.StreamEx;

            public interface Foo {
                StreamEx<String> lol();
            }
        '''.stripIndent()

        then:
        runRevapiExpectingToFindDifferences("root-project")
    }

    def 'does not error out when project has a version greater than the "old version"'() {
        def revapi = 'revapi'

        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            group = 'org.revapi'
            version = '0.12.0'
            
            revapi {
                oldVersion = '0.11.1'
            }
        """.stripIndent()

        rootProjectNameIs(revapi)

        def file = new File(projectDir, "src/main/java/foo/Foo.java")
        file.getParentFile().mkdirs()
        file << '''
            public interface Foo {
                String lol();
            }
        '''.stripIndent()

        then:
        runRevapiExpectingToFindDifferences(revapi)
    }

    def 'errors out when the old api dependency does not exist but then works once you run the version override task'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = 'does-not-exist'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        and:
        runTasksSuccessfully("revapiVersionOverride", "--replacement-version", "0.11.1")

        then:
        runRevapiExpectingToFindDifferences("root-project")
    }

    def 'errors out when the target dependency does not exist and we do not give an version override'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = 'does-not-exist'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        then:
        runRevapiExpectingResolutionFailure("root-project")
    }

    private File rootProjectNameIs(String projectName) {
        settingsFile << "rootProject.name = '${projectName}'"
    }

    private void runRevapiExpectingToFindDifferences(String projectName) {
        runRevapiExpectingStderrToContain("java.class.removed")
        andJunitXmlToHaveBeenProduced(projectName)
    }

    private void runRevapiExpectingResolutionFailure(String projectName) {
        runRevapiExpectingStderrToContain("Failed to resolve old API")
    }

    private void runRevapiExpectingStderrToContain(String stderrContains) {
        ExecutionResult executionResult = runTasksWithFailure("revapi")
        println executionResult.standardOutput
        println executionResult.standardError
        assert executionResult.standardError.contains(stderrContains)
    }

    private void andJunitXmlToHaveBeenProduced(String projectName) {
        String junitOutput = new File(projectDir, "build/junit-reports/revapi/revapi-${projectName}.xml").text
        assert junitOutput.contains("java.class.removed")
    }
}