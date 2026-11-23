
pipeline {
    agent any

    tools {
        jdk 'JDK21'
        maven 'Maven3'
    }

    environment {
        DOCKERHUB_CREDENTIALS = 'dockerhub-credentials'
        MYSQL_CONTAINER = 'mysql'
        BACKEND_CONTAINER = 'backend-app'
        DOCKER_NETWORK = 'spring-boot-network'
        MYSQL_DATABASE = 'devopsdb'
        MYSQL_USER = 'devops'
        MYSQL_PASSWORD = 'devops123'
        MYSQL_ROOT_PASSWORD = 'root12345'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test Maven') {
            steps {
                sh 'mvn -B clean verify'
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t backend-app:latest .'
            }
        }

        stage('Docker Push') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: "${DOCKERHUB_CREDENTIALS}",
                        usernameVariable: 'DOCKERHUB_USERNAME',
                        passwordVariable: 'DOCKERHUB_TOKEN'
                    )
                ]) {
                    sh '''
                        set -eu
                        echo "$DOCKERHUB_TOKEN" | docker login \
                            -u "$DOCKERHUB_USERNAME" --password-stdin

                        docker tag backend-app:latest \
                            "$DOCKERHUB_USERNAME/backend-app:latest"

                        docker push "$DOCKERHUB_USERNAME/backend-app:latest"

                        docker logout
                    '''
                }
            }
        }

        stage('Deploy MySQL') {
            steps {
                sh '''
                    set -eu

                    docker network inspect "$DOCKER_NETWORK" \
                        >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK"

                    if docker container inspect "$MYSQL_CONTAINER" \
                        >/dev/null 2>&1; then
                        docker rm -f "$MYSQL_CONTAINER"
                    fi

                    docker run -d \
                        --name "$MYSQL_CONTAINER" \
                        --network "$DOCKER_NETWORK" \
                        --restart unless-stopped \
                        -e MYSQL_DATABASE="$MYSQL_DATABASE" \
                        -e MYSQL_USER="$MYSQL_USER" \
                        -e MYSQL_PASSWORD="$MYSQL_PASSWORD" \
                        -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
                        --health-cmd='mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' \
                        --health-interval=5s \
                        --health-timeout=5s \
                        --health-retries=20 \
                        mysql:8.4

                    echo "Waiting for MySQL..."

                    for i in $(seq 1 60); do
                        STATUS=$(docker inspect \
                            --format='{{.State.Health.Status}}' \
                            "$MYSQL_CONTAINER" 2>/dev/null || true)

                        if [ "$STATUS" = "healthy" ]; then
                            echo "MySQL is healthy"
                            exit 0
                        fi

                        sleep 5
                    done

                    docker logs "$MYSQL_CONTAINER"
                    echo "MySQL did not become healthy"
                    exit 1
                '''
            }
        }

        stage('Deploy backend-app') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: "${DOCKERHUB_CREDENTIALS}",
                        usernameVariable: 'DOCKERHUB_USERNAME',
                        passwordVariable: 'DOCKERHUB_TOKEN'
                    )
                ]) {
                    sh '''
                        set -eu

                        IMAGE="$DOCKERHUB_USERNAME/backend-app:latest"

                        echo "$DOCKERHUB_TOKEN" | docker login \
                            -u "$DOCKERHUB_USERNAME" --password-stdin

                        docker pull "$IMAGE"
                        docker logout

                        if docker container inspect "$BACKEND_CONTAINER" \
                            >/dev/null 2>&1; then
                            docker stop "$BACKEND_CONTAINER" || true
                            docker rm "$BACKEND_CONTAINER" || true
                        fi

                        docker run -d \
                            --name "$BACKEND_CONTAINER" \
                            --network "$DOCKER_NETWORK" \
                            --restart unless-stopped \
                            -p 8082:8080 \
                            -e SPRING_DATASOURCE_URL="jdbc:mysql://mysql:3306/$MYSQL_DATABASE" \
                            -e SPRING_DATASOURCE_USERNAME="$MYSQL_USER" \
                            -e SPRING_DATASOURCE_PASSWORD="$MYSQL_PASSWORD" \
                            "$IMAGE"
                    '''
                }
            }
        }

        stage('Verify deployment') {
            steps {
                sh '''
                    set -eu

                    echo "=== Running containers ==="
                    docker ps

                    echo "=== Backend logs ==="
                    sleep 15
                    docker logs "$BACKEND_CONTAINER"

                    echo "=== Backend health endpoint ==="

                    for i in $(seq 1 30); do
                        if curl -fsS http://127.0.0.1:8082/api/health; then
                            echo
                            echo "Backend is healthy!"
                            exit 0
                        fi

                        sleep 5
                    done

                    echo "Backend health check failed"
                    docker logs "$BACKEND_CONTAINER"
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            echo 'CD pipeline finished. Review stage results and container logs.'
        }
    }
}
