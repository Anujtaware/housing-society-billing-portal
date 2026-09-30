pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out Housing Society Billing Portal'
            }
        }

        stage('Build') {
            steps {
                echo 'Building Housing Society Billing Portal'
            }
        }

        stage('Test') {
            steps {
                echo 'Running tests'
            }
        }

        stage('Deploy') {
            steps {
                echo 'Deployment stage completed'
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed!'
        }
    }
}
