/*
 * Canonical Multibranch Maven pipeline for Java/Quarkus repositories (CMS-2050).
 *
 * Source of truth: simonsoft-agents/jenkins/java-quarkus/Jenkinsfile
 * Policy and setup: simonsoft-agents/docs/maven-versioning.md
 * Copy this file unchanged to the repository root. It needs no per-repo edits.
 *
 * Version policy (committed revision 4.1-SNAPSHOT):
 *   main / master            -> 4.1-SNAPSHOT,         verify + deploy
 *   maintenance/4.0.x        -> 4.0-SNAPSHOT,         verify + deploy
 *   PR #123                  -> 4.1-PR123-SNAPSHOT,   verify + deploy
 *   tag v4.1.2               -> 4.1.2,                verify + deploy
 *   any other branch         -> 4.1-SNAPSHOT,         verify only
 *
 * Jenkins discovers only master/main, maintenance/MAJOR.MINOR.x, PRs and tags; the
 * verify-only case guards against a misconfigured discovery filter.
 *
 * The root POM must use Maven CI-friendly versioning:
 *   <version>${revision}</version>
 *   <properties><revision>4.1-SNAPSHOT</revision></properties>
 *
 * Maven runs inside withMaven so Jenkins records consumed dependencies,
 * produced artifacts, fingerprints, and the Pipeline Maven dependency graph.
 */

pipeline {
    agent any

    tools {
        // Manage Jenkins -> Tools -> Maven installations: 'maven-3.9', installed
        // automatically from Apache. Puts mvn on PATH, where withMaven finds it.
        maven 'maven-3.9'
    }

    options {
        // A Multibranch child job publishes one stable Maven coordinate. Do not let
        // two builds of the same PR/branch/tag race while publishing that coordinate.
        disableConcurrentBuilds()
        timestamps()
    }

    // Values derived in "Determine version" are set on env from the script block.
    // Do not declare them in an environment {} directive: declarative environment
    // values cannot be overwritten from script, so they would stay empty.

    stages {
        stage('Determine version') {
            steps {
                script {
                    // Read the literal committed property. No Maven run: it would need
                    // repository settings and is not part of the dependency graph.
                    def committedVersion = readMavenPom(file: 'pom.xml').properties['revision']

                    if (!(committedVersion ==~ /\d+\.\d+-SNAPSHOT/)) {
                        error("Expected committed revision like 4.1-SNAPSHOT in pom.xml, got '${committedVersion}'")
                    }

                    def developmentLine = committedVersion.replaceFirst(/-SNAPSHOT$/, '')
                    def buildKind
                    def buildVersion

                    if (env.TAG_NAME) {
                        if (!(env.TAG_NAME ==~ /v\d+\.\d+\.\d+/)) {
                            error("Release tag must match vMAJOR.MINOR.PATCH; got '${env.TAG_NAME}'")
                        }

                        def releaseVersion = env.TAG_NAME.substring(1)
                        def parts = releaseVersion.tokenize('.')
                        def releaseLine = "${parts[0]}.${parts[1]}"
                        if (releaseLine != developmentLine) {
                            error("Tag ${env.TAG_NAME} is for ${releaseLine}, but this source line is ${committedVersion}")
                        }

                        buildKind = 'release'
                        buildVersion = releaseVersion
                    } else if (env.CHANGE_ID) {
                        buildKind = 'pull-request'
                        buildVersion = "${developmentLine}-PR${env.CHANGE_ID}-SNAPSHOT"
                    } else if (env.BRANCH_NAME in ['main', 'master']) {
                        buildKind = 'integration'
                        buildVersion = committedVersion
                    } else if (env.BRANCH_NAME ==~ /maintenance\/\d+\.\d+\.x/) {
                        // A maintenance branch publishes its own line's SNAPSHOT only.
                        def branchLine = env.BRANCH_NAME.replaceFirst(/^maintenance\//, '').replaceFirst(/\.x$/, '')
                        if (branchLine != developmentLine) {
                            error("Branch ${env.BRANCH_NAME} must declare revision ${branchLine}-SNAPSHOT, got ${committedVersion}")
                        }
                        buildKind = 'maintenance'
                        buildVersion = committedVersion
                    } else {
                        buildKind = 'branch'
                        buildVersion = committedVersion
                    }

                    env.BUILD_KIND = buildKind
                    env.BUILD_VERSION = buildVersion
                    // Only PR and tag builds override the committed revision.
                    env.MAVEN_REVISION_ARG = (buildVersion == committedVersion) ? '' : "-Drevision=${buildVersion}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${buildVersion}"

                    echo "Build kind: ${buildKind}"
                    echo "Maven version: ${buildVersion}"
                }
            }
        }

        stage('Verify') {
            steps {
                withMaven(traceability: true) {
                    sh 'mvn ${MAVEN_REVISION_ARG} clean verify'
                }
            }
        }

        stage('Deploy Maven artifacts') {
            when {
                expression { env.BUILD_KIND != 'branch' }
            }
            steps {
                withMaven(traceability: true) {
                    // Verify has already run. Maven still executes the lifecycle needed
                    // for deploy, but tests are not run a second time.
                    sh 'mvn ${MAVEN_REVISION_ARG} -DskipTests deploy'
                }
            }
        }
    }
}
