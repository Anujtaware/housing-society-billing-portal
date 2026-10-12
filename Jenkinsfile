pipeline {
    agent any

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
    }

    post {
        always {
            echo '=== Stage 5: Archive Build Artifact & Test Reports ==='
            archiveArtifacts artifacts: 'application/target/*.jar', fingerprint: true, allowEmptyArchive: false
            junit testResults: 'application/target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            echo '✅ Continuous Integration Pipeline completed successfully!'
        }
        failure {
            echo '❌ Continuous Integration Pipeline failed!'
        }
    }
}
