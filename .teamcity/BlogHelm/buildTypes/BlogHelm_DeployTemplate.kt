package BlogHelm.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_1.*
import jetbrains.buildServer.configs.kotlin.v2018_1.buildSteps.exec
import jetbrains.buildServer.configs.kotlin.v2018_1.buildSteps.script

object BlogHelm_DeployTemplate : Template({
    uuid = "bdb9db38-14a7-4b33-a762-f84f97141b1c"
    id("BlogHelm_DeployTemplate")
    name = "Deploy Template"
    buildNumberPattern = "%dep.BlogHelm_CommitStage.build.number%"
    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    maxRunningBuilds = 1

    params {
        text("app.env", "", label = "Environment", description = "Select the environment to deploy to",
              regex = "^test|acc|prod${'$'}", validationMessage = "Must be one of test acc prod")
        param("app.host", "")
        param("app.baseurl", "http://%app.host%")
        param("app.version.url", "%app.baseurl%/version")
    }

    vcs {
        root(BlogHelm.vcsRoots.BlogHelm_BlogHelm)
        showDependenciesChanges = true
    }

    steps {
        script {
            name = "Deploy using Helm"
            scriptContent = """
                ./ci-scripts/deploy.sh "%env.KUBECTL_CONFIG%" %app.env% %build.number%
            """.trimIndent()
            dockerImage = "lachlanevenson/k8s-helm:%lachlanevenson.k8s-helm.tag%"
            dockerRunParameters = "--rm"
        }
        script {
            name = "Workaround for local HOSTS file"
            scriptContent = """
                grep -v %app.host% /etc/hosts > /etc/hosts.tmp
                echo "%minikube.ip% %app.host%" >> /etc/hosts.tmp
                cat /etc/hosts.tmp > /etc/hosts
                rm /etc/hosts.tmp
            """.trimIndent()
        }
        exec {
            name = "Wait until the correct version is available"
            path = "ci-scripts/wait-for-version.sh"
            arguments = "%app.version.url% %build.number%"
        }
        script {
            name = "Login to Docker registry"
            scriptContent = "docker login -u %docker.username% -p %docker.password% %docker.server%"
            enabled = false
        }
        script {
            name = "Run WebdriverIO tests"
            scriptContent = """
                docker run \
                    --rm -v ${'$'}(pwd)/test-reports:/app/test-reports \
                    blog-helm-ci:%build.number% \
                    ./ci-scripts/wdio-tests.sh \
                        --url %app.baseurl% \
                        --ip %minikube.ip% \
                        --host %app.host%

                docker run \
                  --rm -v ${'$'}(pwd)/test-reports:/app/test-reports \
                  blog-helm-ci:%build.number% \
                  chown -R ${'$'}(id -u):${'$'}(id -g) test-reports
            """.trimIndent()
        }
        script {
            name = "Logout from Docker registry"
            scriptContent = "docker logout %docker.server%"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            enabled = false
        }
    }

    dependencies {
        dependency(BlogHelm.buildTypes.BlogHelm_CommitStage) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
            }

            artifacts {
                cleanDestination = true
                artifactRules = """
                    *.tgz => artifacts
                    values-*.yaml => artifacts
                """.trimIndent()
            }
        }
    }

    features {
        feature {
            type = "perfmon"
        }
        feature {
            type = "xml-report-plugin"
            param("xmlReportParsing.reportType", "junit")
            param("xmlReportParsing.reportDirs", "test-reports/WD*.xml")
            param("xmlReportParsing.verboseOutput", "true")
        }
    }
})
