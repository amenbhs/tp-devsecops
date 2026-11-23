# Compte rendu — TP DevSecOps (TP1 à TP4)

Application Spring Boot `democyber` (Java 21, Maven, Docker, GitHub Actions, Gitleaks, Trivy, SonarCloud, Prometheus, Grafana).

## Ce qui est déjà en place

- Application : `src/main/java/com/example/democyber` (`DemocyberApplication`, `HelloController`).
- `pom.xml` : Spring Web, Actuator, Micrometer Prometheus, tests, JaCoCo (couverture pour SonarCloud).
- `Dockerfile` multi-étapes (build Maven, image JRE non-root).
- Pipeline : `.github/workflows/ci.yml` (Gitleaks, Maven, SonarCloud, Docker, Trivy, push Docker Hub).
- Supervision : `monitoring/docker-compose.yml` et `monitoring/prometheus.yml`.
- `mvn clean package` : **BUILD SUCCESS** (3 tests).

Vérification locale (le port 8080 est déjà pris par un autre processus Java/Jetty, donc l’application tourne sur **8081**) :

- http://localhost:8081 → `Bonjour depuis democyber Spring Boot !`
- http://localhost:8081/api/status → `{"status":"UP","application":"democyber"}`
- http://localhost:8081/actuator/prometheus → HTTP 200, métriques JVM et `democyber_hello_requests_total`

## Réponses TP2 — Gitleaks et Trivy

### Que fait Gitleaks ?

Gitleaks parcourt le code source **et l’historique Git** pour détecter des secrets : jetons GitHub (`ghp_...`), mots de passe, clés d’API, clés privées. Dans la pipeline, l’étape est placée **avant** la compilation. Si un secret est trouvé, le job échoue et le code n’est ni construit ni publié.

### Pourquoi le pipeline échoue après le faux secret ?

La ligne

```java
private static final String GITHUB_TOKEN = "ghp_1234567890abcdef1234567890abcdef1234";
```

correspond au motif d’un jeton GitHub. Gitleaks empêche de **publier des identifiants** dans le dépôt : un secret commité peut être réutilisé par n’importe qui qui clone le projet.

### Après avoir commenté la ligne, le pipeline réussit-il ?

Souvent **non**. Git conserve le secret dans l’ancien commit. Gitleaks relit tout l’historique (`fetch-depth: 0`). Commenter la ligne ne l’efface pas. Il faut révoquer le vrai jeton, puis supprimer le commit de l’historique, ou (pour ce TP uniquement) ignorer l’empreinte indiquée dans le rapport via `.gitleaksignore`. Le fichier final du projet **ne contient pas** ce faux secret, pour que la pipeline puisse rester verte.

### Que montre le rapport Gitleaks (`results.sarif`) ?

Le fichier, la ligne, le commit et le type de secret. Il sert à savoir **quoi** a fuité et **où**, pour révoquer le jeton tout de suite, le retirer de l’historique et le remplacer. Un secret détecté ne se « corrige » pas en le laissant dans Git.

### Que vérifie le test Docker de la pipeline ?

L’image est démarrée, puis `curl` appelle l’application. On vérifie qu’elle répond avant le `docker push`. Publier une image qui ne démarre pas rendrait le registre Docker Hub inutilisable pour le déploiement.

### Paramètres Trivy

| Paramètre | Rôle |
|-----------|------|
| `image-ref` | Image à analyser (`democyber:latest`) |
| `format` | `table` pour la lecture humaine, `json` pour l’artifact `trivy-report` |
| `severity` | `CRITICAL,HIGH` : seules les vulnérabilités graves font échouer le contrôle |
| `exit-code: 1` | La pipeline **échoue** si une CVE de ce niveau est trouvée |
| `ignore-unfixed: true` | Les CVE sans correctif connu dans l’image de base ne bloquent pas le push |

Le rapport JSON est uploadé même si le scan bloquant échoue (`if: always()`).

## Réponses TP3 — SonarCloud

### Rôle de SonarCloud

Analyse **statique** du code : bugs, vulnérabilités, code smells, duplications, couverture (JaCoCo). La porte de qualité (Quality Gate) refuse de considérer le code comme livrable si les règles ne sont pas tenues. Ce n’est ni un scan de secrets (Gitleaks) ni un scan de CVE d’image (Trivy).

### Statut et notes

L’analyse n’a pas encore été lancée : elle exige un compte SonarCloud et les secrets GitHub `SONAR_TOKEN`, `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY`. Après le premier push sur `main`, relever sur SonarCloud :

- statut Passed ou Failed ;
- notes Security, Reliability, Maintainability.

### Règles à ajouter au Quality Gate

- aucune nouvelle vulnérabilité ni aucun nouveau security hotspot non revu ;
- note de sécurité et de fiabilité au moins A sur le nouveau code ;
- couverture du nouveau code ≥ 80 % ;
- duplications du nouveau code < 3 % ;
- aucun bug bloquant.

### Différence entre les outils

| Outil | Cible | Détecte |
|-------|--------|---------|
| Gitleaks | code et historique Git | secrets (jetons, mots de passe, clés) |
| Trivy | image Docker et dépendances | CVE connues |
| SonarCloud | code source | bugs, failles de code, code smells, couverture |

## TP4 — Prometheus et Grafana

Actuator est activé (`management.endpoints.web.exposure.include=*`) et `/actuator/prometheus` répond. Prometheus scrape `host.docker.internal:8080`. Grafana est publié sur le port **3001** (le conteneur écoute 3000). Identifiants : `admin` / `admin`. Source de données : `http://prometheus:9090`. Dashboard Spring Boot : ID `11378`.

## Étapes qui restent sur ta machine

Docker Desktop est installé, mais le moteur Linux ne démarre pas : **Virtual Machine Platform** n’est pas activée. Sans cela, impossible de builder l’image, de lancer Prometheus/Grafana, ou de pousser sur Docker Hub.

1. PowerShell **en administrateur** :

```powershell
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
```

Puis redémarrer Windows, ouvrir Docker Desktop, et attendre que `docker info` réponde.

2. Libérer le port 8080 (un Java/Jetty l’occupe déjà), puis :

```powershell
cd C:\Users\yakin\Downloads\tp-devsecops\tp-devsecops
java -jar target\democyber-0.0.1-SNAPSHOT.jar
cd monitoring
docker compose up -d
```

3. GitHub : créer un dépôt vide `tp-devsecops` (sans README), puis :

```powershell
cd C:\Users\yakin\Downloads\tp-devsecops\tp-devsecops
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<utilisateur>/tp-devsecops.git
git push -u origin main
```

4. Secrets GitHub (Settings → Secrets and variables → Actions) :

| Secret | Valeur |
|--------|--------|
| `DOCKERHUB_USERNAME` | utilisateur Docker Hub |
| `DOCKERHUB_TOKEN` | personal access token Docker Hub |
| `SONAR_TOKEN` | jeton SonarCloud (nom suggéré : `sonar-token`) |
| `SONAR_ORGANIZATION` | clé d’organisation SonarCloud |
| `SONAR_PROJECT_KEY` | clé du projet SonarCloud |

Ne pas écrire ces valeurs dans le code : Gitleaks ferait échouer la pipeline.

5. Captures à prendre pour le rendu : pipeline verte, artifact `gitleaks-report`, étapes Docker login / build / test, scan Trivy, artifact `trivy-report`, push Docker Hub, page SonarCloud, Prometheus Targets UP, dashboard Grafana 11378.
