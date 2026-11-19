// Declarative CI/CD pipeline for fee-service
// Stages: Checkout -> Build -> Test -> Package -> Deploy (Test env)

pipeline {
    agent any

    // Tool names must match the installations configured in Jenkins
    // (see jenkins/casc.yaml -> tool.maven / tool.jdk).
    tools {
        jdk   'JDK-17'
        maven 'Maven-3'
    }

    triggers {
        // Fires on GitHub webhook pushes (needs a public URL, e.g. ngrok, for a local Jenkins)
        githubPush()
        // Fallback: poll the repo every ~2 minutes in case the webhook can't reach Jenkins
        pollSCM('H/2 * * * *')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '5'))
        timeout(time: 20, unit: 'MINUTES')
    }

    environment {
        APP_NAME   = 'fee-service'
        APP_PORT   = '8085'
        DEPLOY_DIR = "${env.JENKINS_HOME}/deployments/test/fee-service"
        MAVEN_ARGS = '-B -ntp'   // batch mode, no download-progress noise in logs
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --pretty=format:"Commit %h by %an: %s%n"'
            }
        }

        stage('Build') {
            steps {
                sh 'java -version && mvn -v'
                sh 'mvn $MAVEN_ARGS clean compile'
            }
        }

        stage('Test') {
            steps {
                // Any failing test makes `mvn test` exit non-zero, which fails this stage and the pipeline
                sh 'mvn $MAVEN_ARGS test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn $MAVEN_ARGS -DskipTests package'
                archiveArtifacts artifacts: 'target/fee-service.jar', fingerprint: true, onlyIfSuccessful: true
            }
        }

        stage('Deploy (Test env)') {
            steps {
                // Secret comes from the Jenkins credential store; it is masked in the console log
                withCredentials([string(credentialsId: 'test-env-deploy-token', variable: 'DEPLOY_TOKEN')]) {
                    // JENKINS_NODE_COOKIE stops Jenkins killing the app process when the build ends
                    withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
                        sh 'chmod +x scripts/deploy.sh && ./scripts/deploy.sh target/fee-service.jar'
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ ${env.APP_NAME} build #${env.BUILD_NUMBER} built, tested and deployed to test (http://localhost:${env.APP_PORT}/health)"
        }
        failure {
            echo "❌ Pipeline failed at build #${env.BUILD_NUMBER} - check the stage logs above"
        }
        always {
            cleanWs(deleteDirs: true, notFailBuild: true)
        }
    }
}
