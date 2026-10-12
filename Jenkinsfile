pipeline {
    agent any

    parameters {
        choice(name: 'DEPLOY_ENV', choices: ['DEV', 'TEST', 'STAGING', 'PROD'], description: 'Deployment Target Environment')
        string(name: 'DEPLOY_PORT', defaultValue: '8081', description: 'Nginx Web Server / Reverse Proxy Port')
        string(name: 'APP_PORT', defaultValue: '8082', description: 'Spring Boot Application Port')
        string(name: 'DOCKER_HOST_PORT', defaultValue: '8084', description: 'Docker Container Host Port Mapping')
    }

    environment {
        JENKINS_NODE_COOKIE = 'dontKillMe'
        DOCKER_IMAGE_NAME = 'housing-society-billing-portal'
        DOCKER_CONTAINER_NAME = 'housing-billing-cd'
        DOCKER_CONFIG = 'C:\\Users\\anujt\\.docker'
        DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
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

        stage('Docker Build') {
            steps {
                echo "=== Stage 8: Verify Docker & Build Image (${env.DOCKER_IMAGE_NAME}:build-${BUILD_NUMBER}) ==="
                bat 'docker version'
                bat 'docker info'
                bat "docker build -t ${env.DOCKER_IMAGE_NAME}:build-${BUILD_NUMBER} -t ${env.DOCKER_IMAGE_NAME}:latest ."
                bat "docker images ${env.DOCKER_IMAGE_NAME}"
            }
        }

        stage('Docker Deploy') {
            steps {
                echo "=== Stage 9: Deploy Fresh Docker Container (${env.DOCKER_CONTAINER_NAME} on Port ${params.DOCKER_HOST_PORT}) ==="
                bat "powershell -ExecutionPolicy Bypass -File scripts/docker-deploy.ps1 -ImageName ${env.DOCKER_IMAGE_NAME} -ImageTag build-${BUILD_NUMBER} -HostPort ${params.DOCKER_HOST_PORT} -ContainerName ${env.DOCKER_CONTAINER_NAME}"
            }
        }

        stage('Docker Health Check') {
            steps {
                echo "=== Stage 10: Verify Docker Application Health & APIs (Port: ${params.DOCKER_HOST_PORT}) ==="
                bat "powershell -ExecutionPolicy Bypass -File scripts/docker-healthcheck.ps1 -HostPort ${params.DOCKER_HOST_PORT} -ContainerName ${env.DOCKER_CONTAINER_NAME} -MaxRetries 30 -DelaySeconds 2"
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
            echo "✅ Week 12 Jenkins-Docker Continuous Deployment Pipeline completed successfully!"
            echo "🚀 Nginx Reverse Proxy Live: http://localhost:${params.DEPLOY_PORT}/index.html"
            echo "🐳 Docker Containerized CD Live: http://localhost:${params.DOCKER_HOST_PORT}/index.html"
        }
        failure {
            echo "❌ Week 12 Jenkins-Docker Continuous Deployment Pipeline failed!"
        }
    }
}
