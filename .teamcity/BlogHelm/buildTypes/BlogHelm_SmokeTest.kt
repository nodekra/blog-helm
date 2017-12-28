package BlogHelm.buildTypes

import BlogHelm.vcsRoots.BlogHelm_BlogHelm
import jetbrains.buildServer.configs.kotlin.v2017_2.*
import jetbrains.buildServer.configs.kotlin.v2017_2.buildSteps.dockerBuild
import jetbrains.buildServer.configs.kotlin.v2017_2.buildSteps.exec
import jetbrains.buildServer.configs.kotlin.v2017_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2017_2.triggers.finishBuildTrigger

object BlogHelm_SmokeTest : BuildType({
    uuid = "d555deaf-076e-4997-8756-95da85e8d785"
    id = "BlogHelm_SmokeTest"
    name = "Smoke Test"
    buildNumberPattern = "%dep.BlogHelm_CommitStage.build.number%"
    enablePersonalBuilds = false
    maxRunningBuilds = 1

    params {
        param("env.IMAGE_TAG", "")
    }

    vcs {
        root(BlogHelm.vcsRoots.BlogHelm_BlogHelm)

        showDependenciesChanges = true
    }

    triggers {
        finishBuildTrigger {
            buildTypeExtId = "BlogHelm_CommitStage"
            successfulOnly = true
            branchFilter = "+:*"
        }
    }

    steps {
        exec {
            name = "Smoke test Docker image"
            path = "ci-scripts/smoke-test-docker-image.sh"
            arguments = "%docker.registry% blog-helm"
        }
    }

    features {
        feature {
            type = "perfmon"
        }
    }

    dependencies {
        dependency("BlogHelm_CommitStage") {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                cleanDestination = true
                artifactRules = "*.txt => artifacts"
            }
        }
    }
})
