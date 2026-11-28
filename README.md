# spring-boot-devops

Application **Spring Boot** conteneurisée, livrée automatiquement par un pipeline **Jenkins** :
build et tests Maven, construction et publication de l'image Docker sur Docker Hub, puis déploiement
d'un conteneur **MySQL** et du conteneur **backend-app**, avec vérification du démarrage.

## Pipeline CD

```
Git (GitHub)
 ↓
Jenkins
 ↓
Build & Test Maven
 ↓
Docker Build
 ↓
Docker Push
 ↓
Déploiement MySQL
 ↓
Déploiement backend-app
 ↓
Vérification (docker ps, docker logs)
```

## Structure du dépôt

```
spring-boot-devops/
├── src/            # code source et tests de l'application
├── pom.xml         # build Maven (Java 21, Spring Boot 3.5.6)
├── Dockerfile      # image de l'application
├── Jenkinsfile     # pipeline CI/CD déclaratif
└── README.md
```

## Technologies

| Outil | Rôle |
|---|---|
| Java 21, Spring Boot 3.5.6 | Application backend (API REST, Spring Data JPA, Actuator) |
| Maven 3.9 | Build, tests, génération de `backend-app.jar` |
| Git / GitHub | Versionnement du code |
| Jenkins (port 8081) | Orchestration du pipeline |
| Docker | Image `backend-app` et conteneurs |
| Docker Hub | Registre des images (dépôt privé `ramzibh1/backend-app`) |
| MySQL 8.4 | Base de données du backend déployé |

## Étapes du pipeline

| # | Étape | Ce qu'elle fait |
|---|---|---|
| 1 | **Checkout** | Récupère le code depuis GitHub (`checkout scm`, branche `main`). |
| 2 | **Build & Test Maven** | `mvn -B clean verify` : compilation, tests, création de `target/backend-app.jar`. |
| 3 | **Docker Build** | Construit l'image `backend-app:latest` à partir du `Dockerfile`. |
| 4 | **Docker Push** | Connexion à Docker Hub avec les Credentials Jenkins, tag `ramzibh1/backend-app:latest`, push, déconnexion. |
| 5 | **Deploy MySQL** | Crée le réseau `spring-boot-network`, lance le conteneur `mysql` et attend qu'il soit `healthy`. |
| 6 | **Deploy backend-app** | Se reconnecte au registre, récupère l'image publiée, supprime l'ancien conteneur `backend-app` s'il existe, lance le nouveau. |
| 7 | **Verify deployment** | `docker ps`, `docker logs backend-app`, puis appel de `/api/health` jusqu'à la réponse `UP`. |

## Commandes Docker utilisées

```bash
# 1. Construction de l'image
docker build -t backend-app:latest .

# 2. Publication dans le registre (authentification via Credentials Jenkins)
echo "$DOCKERHUB_TOKEN" | docker login -u ramzibh1 --password-stdin
docker tag backend-app:latest ramzibh1/backend-app:latest
docker push ramzibh1/backend-app:latest
docker logout

# 3. Réseau et conteneur MySQL (le Jenkinsfile ajoute un health check mysqladmin ping)
docker network create spring-boot-network
docker run -d --name mysql --network spring-boot-network --restart unless-stopped \
  -e MYSQL_DATABASE=devopsdb -e MYSQL_USER=devops \
  -e MYSQL_PASSWORD=<mot_de_passe> -e MYSQL_ROOT_PASSWORD=<mot_de_passe_root> \
  mysql:8.4

# 4. Déploiement du backend
echo "$DOCKERHUB_TOKEN" | docker login -u ramzibh1 --password-stdin
docker pull ramzibh1/backend-app:latest
docker logout
docker rm -f backend-app || true          # supprime l'ancien conteneur s'il existe
docker run -d --name backend-app --network spring-boot-network --restart unless-stopped \
  -p 8082:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/devopsdb \
  -e SPRING_DATASOURCE_USERNAME=devops \
  -e SPRING_DATASOURCE_PASSWORD=<mot_de_passe> \
  ramzibh1/backend-app:latest

# 5. Vérification
docker ps
docker logs backend-app
curl http://localhost:8082/api/health
```

Le backend écoute sur le port **8080** dans le conteneur, publié sur le port **8082** de la machine hôte.

## Configuration de Jenkins

1. **Credential Docker Hub** : *Administrer Jenkins → Credentials → (global)*, ID `dockerhub-creds`,
   contenant un **Personal Access Token Docker Hub avec la permission Read & Write**.
2. **Droits Docker** : l'utilisateur `jenkins` doit pouvoir utiliser Docker.
   ```bash
   sudo usermod -aG docker jenkins
   sudo systemctl restart jenkins
   sudo -u jenkins docker ps   # doit fonctionner sans erreur de permission
   ```
3. **Job** : *Nouveau Item → Pipeline* nommé `spring-boot-devops`, puis dans la section *Pipeline* :
   - Definition : `Pipeline script from SCM`
   - SCM : `Git`, Repository URL : `https://github.com/ramzibh1/spring-boot-devops.git`
   - Branch Specifier : `*/main`
   - Script Path : `Jenkinsfile`
4. Cliquer sur **Lancer un build**.

## Vérifier le déploiement

```bash
docker ps                       # mysql (healthy) et backend-app doivent être "Up"
docker logs backend-app         # doit se terminer par "Started SpringBootDevopsApplication ..."
curl http://localhost:8082/api/health    # {"status":"UP"}
```

## Dépannage

| Message | Cause et solution |
|---|---|
| `permission denied ... docker.sock` | L'utilisateur `jenkins` n'est pas dans le groupe `docker` (voir ci-dessus). |
| `access token has insufficient scopes` | Le token Docker Hub est en lecture seule : en créer un avec **Read & Write**. |
| `password is empty` | Le secret du credential a été enregistré vide : cliquer sur *Change Password* avant de coller le token. |
| `pull access denied` au déploiement | Le dépôt est privé et l'étape de déploiement ne s'est pas authentifiée avant `docker pull`. |
| MySQL reste longtemps en `starting` | Première initialisation de la base : attendre environ une à deux minutes. |

## Remarque de sécurité

Les mots de passe MySQL utilisés dans le `Jenkinsfile` sont des valeurs de test pour ce projet pédagogique.
Dans un vrai projet, ils doivent être stockés comme Credentials Jenkins et jamais écrits dans le dépôt.

## Auteur

Ramzi Ben Hmida
