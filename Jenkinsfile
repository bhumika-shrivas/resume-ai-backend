pipeline {
    agent any

    tools {
        maven 'Maven' // Replace with your configured Maven installation
        jdk 'JDK25'    // Replace with your JDK version
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Microservices') {
            steps {
                script {
                    def services = [
                        'api-gateway', 
                        'discovery-server', 
                        'auth-service', 
                        'resume-service', 
                        'jobmatch-service', 
                        'template-service', 
                        'export-service', 
                        'ai-service', 
                        'payment-service', 
                        'section-service', 
                        'notification-service',
                        'resumeai-web'
                    ]
                    
                    for (int i = 0; i < services.size(); ++i) {
                        def svc = services[i]
                        dir(svc) {
                            echo "Building ${svc}..."
                            // Use 'bat' if Jenkins is on Windows
                            bat 'mvn clean package -DskipTests'
                        }
                    }
                }
            }
        }

        stage('Deploy Locally') {
            steps {
                echo 'Build completed. Initiating local deployment...'
                // JENKINS_NODE_COOKIE=dontKillMe tells Jenkins NOT to terminate background processes when the job finishes
                withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
                    powershell './deploy.ps1'
                }
            }
        }
    }
}
