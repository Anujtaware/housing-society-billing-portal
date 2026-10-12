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

        stage('Test') {
            steps {
                echo '=== Stage 3: Run Automated Test Suite ==='
                bat 'mvn test -f application/pom.xml'
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
    }

    post {
        always {
            echo '=== Archiving Build Artifact & Test Reports ==='
            archiveArtifacts artifacts: 'application/target/*.jar', fingerprint: true, allowEmptyArchive: false
            junit testResults: 'application/target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            echo "✅ Week 8 Continuous Deployment Pipeline completed successfully!"
            echo "🚀 Application is live at: http://localhost:${params.DEPLOY_PORT}/index.html"
        }
        failure {
            echo "❌ Week 8 Continuous Deployment Pipeline failed!"
        }
    }
}
