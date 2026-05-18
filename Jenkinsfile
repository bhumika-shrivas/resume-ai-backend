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
                        'notification-service'
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

        stage('Deploy') {
            steps {
                echo 'Build completed. The .jar files in target/ directories are ready for deployment.'
                // Example: Deploy using Docker or run java -jar
                // bat 'docker-compose up -d --build'
            }
        }
    }
}
