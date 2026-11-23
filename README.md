# tp-devsecops

Projet complet regroupant les 4 TP DevSecOps : application Spring Boot, Docker, pipeline CI/CD GitHub Actions sécurisé (Gitleaks, Trivy, SonarCloud) et supervision Prometheus / Grafana.

## Structure

```
tp-devsecops/
├── .github/workflows/ci.yml     # TP1-3 : pipeline CI/CD
├── monitoring/
│   ├── docker-compose.yml       # TP4 : Prometheus + Grafana
│   └── prometheus.yml
├── src/main/java/com/example/democyber/
│   ├── DemocyberApplication.java
│   └── HelloController.java
├── src/main/resources/application.properties
├── src/test/java/.../HelloControllerTest.java
├── Dockerfile
└── pom.xml
```

## Lien avec les TP

| TP | Contenu | Où dans le projet |
|----|---------|-------------------|
| TP1 | Git, Maven, Docker, CI/CD de base | `pom.xml`, `Dockerfile`, `ci.yml` |
| TP2 | Gitleaks + Trivy + push Docker Hub | étapes Gitleaks / Trivy / Push de `ci.yml` |
| TP3 | SonarCloud | étape SonarCloud de `ci.yml`, JaCoCo dans `pom.xml` |
| TP4 | Actuator, Micrometer, Prometheus, Grafana | `application.properties`, `monitoring/` |

## 1. Lancer en local

```bash
mvn clean package
java -jar target/democyber-0.0.1-SNAPSHOT.jar
```

- http://localhost:8080 : page d'accueil
- http://localhost:8080/api/status : statut JSON
- http://localhost:8080/actuator : endpoints Actuator
- http://localhost:8080/actuator/prometheus : métriques Prometheus

## 2. Docker

```bash
docker build -t <username>/democyber:latest .
docker run -p 8080:8080 <username>/democyber:latest
docker login
docker push <username>/democyber:latest
```

## 3. Git et GitHub

```bash
git init
git add .
git commit -m "feat: initial commit"
git branch -M main
git remote add origin https://github.com/<user>/tp-devsecops.git
git push -u origin main
```

## 4. Secrets GitHub à créer

Settings → Secrets and variables → Actions → New repository secret :

| Secret | Valeur |
|--------|--------|
| `DOCKERHUB_USERNAME` | nom d'utilisateur Docker Hub |
| `DOCKERHUB_TOKEN` | Personal Access Token Docker Hub |
| `SONAR_TOKEN` | jeton SonarCloud (My Account → Security) |
| `SONAR_ORGANIZATION` | clé de l'organisation SonarCloud |
| `SONAR_PROJECT_KEY` | clé du projet SonarCloud |

Ne jamais écrire ces valeurs dans le code ou dans un fichier du dépôt : Gitleaks les détecterait, et un jeton exposé doit être révoqué immédiatement.

## 5. Pipeline CI/CD

Ordre des étapes de `ci.yml` :

1. Checkout (historique complet)
2. Gitleaks : détection de secrets (échoue si secret trouvé) + upload du rapport
3. Compilation et tests Maven (`mvn clean verify`, couverture JaCoCo)
4. SonarCloud : analyse qualité et sécurité du code
5. Build de l'image Docker et test local (`curl`)
6. Trivy : rapport JSON (artifact) puis contrôle bloquant sur CRITICAL / HIGH
7. Push sur Docker Hub, uniquement sur `main` et seulement si tout a réussi

### Tester Gitleaks (TP2)

Ajouter dans `DemocyberApplication.java` :

```java
private static final String GITHUB_TOKEN = "ghp_1234567890abcdef1234567890abcdef1234";
```

Commit, push : le pipeline doit échouer. Supprimer la ligne et pousser à nouveau. Comme l'historique git conserve le faux secret, Gitleaks peut continuer à échouer sur le commit ancien : pour le TP, créer un fichier `.gitleaksignore` avec l'empreinte indiquée dans le rapport, ou repartir d'un dépôt propre.

## 6. Supervision (TP4)

```bash
mvn spring-boot:run          # ou java -jar ... (application sur le port 8080)
cd monitoring
docker compose up -d
```

- Prometheus : http://localhost:9090 (Status → Targets doit montrer `democyber` en UP)
- Grafana : http://localhost:3001 (admin / admin ; le port 3000 du conteneur est publié sur 3001)
- Data source Prometheus : `http://prometheus:9090`
- Dashboard : Dashboards → New → Import → ID `11378` (choisir la data source Prometheus)

Générer du trafic pour voir bouger les courbes :

```bash
for i in $(seq 1 100); do curl -s localhost:8080 > /dev/null; done
```

La métrique personnalisée `democyber_hello_requests_total` est visible dans Prometheus.

## 7. Compte rendu : différence entre les outils

| Outil | Cible | Détecte |
|-------|-------|---------|
| Gitleaks | code source et historique git | secrets (tokens, mots de passe, clés) |
| Trivy | image Docker / dépendances | vulnérabilités connues (CVE) |
| SonarCloud | code source | bugs, vulnérabilités, code smells, couverture, duplication |
