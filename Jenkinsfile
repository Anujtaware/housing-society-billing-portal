pipeline {
    agent any

    parameters {
        choice(name: 'DEPLOY_ENV', choices: ['DEV', 'TEST', 'STAGING', 'PROD'], description: 'Deployment Target Environment')
        string(name: 'DEPLOY_PORT', defaultValue: '8081', description: 'Nginx Web Server / Reverse Proxy Port')
        string(name: 'APP_PORT', defaultValue: '8082', description: 'Spring Boot Application Port')
    }

    environment {
        JENKINS_NODE_COOKIE = 'dontKillMe'
    }

    options {
        disableConcurrentBuilds()
    }

    triggers {
        // Poll SCM every minute for changes pushed to GitHub
        pollSCM('* * * * *')
    }

    stages {
        stage('Checkout') {
            steps {
                echo '=== Stage 1: Checkout Housing Society Billing Portal ==='
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo '=== Stage 2: Clean & Compile Maven Project ==='
                bat 'mvn clean compile -f application/pom.xml'
            }
        }

        stage('Unit Tests') {
            steps {
                echo '=== Stage 3: Run Unit & Integration Tests ==='
                bat 'mvn test -Dtest=!SeleniumE2ETests -f application/pom.xml'
            }
        }

        stage('Package') {
            steps {
                echo '=== Stage 4: Package Spring Boot Executable JAR ==='
                bat 'mvn package -DskipTests=true -f application/pom.xml'
            }
        }

        stage('Deploy') {
            steps {
                echo "=== Stage 5: Deploy Application to Nginx Reverse Proxy (Env: ${params.DEPLOY_ENV}) ==="
                bat "powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1 -EnvName ${params.DEPLOY_ENV} -DeployPort ${params.DEPLOY_PORT} -AppPort ${params.APP_PORT} -JarPath application/target/billing-portal-0.0.1-SNAPSHOT.jar"
            }
        }

        stage('Health Check') {
            steps {
                echo "=== Stage 6: Verify Deployed Application Health (Port: ${params.DEPLOY_PORT}) ==="
                bat "powershell -ExecutionPolicy Bypass -File scripts/healthcheck.ps1 -DeployPort ${params.DEPLOY_PORT} -MaxRetries 30 -DelaySeconds 2"
            }
        }

        stage('Selenium Tests') {
            steps {
                echo "=== Stage 7: Run Selenium Automated End-to-End UI Tests (Port: ${params.DEPLOY_PORT}) ==="
                bat "mvn test -Dtest=SeleniumE2ETests -Dapp.url=http://localhost:${params.DEPLOY_PORT}/index.html -f application/pom.xml"
            }
        }
    }

    post {
        always {
            echo '=== Archiving Build Artifact, Screenshots & Test Reports ==='
            archiveArtifacts artifacts: 'application/target/*.jar', fingerprint: true, allowEmptyArchive: false
            archiveArtifacts artifacts: 'application/test-results/screenshots/*.png', allowEmptyArchive: true
            junit testResults: 'application/target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            echo "✅ Week 10 Continuous Testing Pipeline completed successfully!"
            echo "🚀 Application is verified & live at: http://localhost:${params.DEPLOY_PORT}/index.html"
        }
        failure {
            echo "❌ Week 10 Continuous Testing Pipeline failed!"
        }
    }
}
