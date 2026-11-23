# Rapport détaillé — TP DevSecOps

Dépôt : https://github.com/amenbhs/tp-devsecops  
Compte GitHub : **amenbhs**  
Dossier local : `C:\Users\yakin\Downloads\tp-devsecops\tp-devsecops`  
Application : **democyber**, Spring Boot **4.2.0**, Java **21**, Tomcat **11.0.27**

Spring Boot a été monté de 3.3.0 à 4.2.0 parce que Trivy avait trouvé 9 CVE critiques et 35 CVE élevées dans Tomcat 10.1.24, Spring 6.1.8 et Jackson 2.17.1. La version 4.2.0 embarque Tomcat 11.0.27, Spring Framework 7.1.0, Jackson 3.1.8 et Micrometer 1.18.0, c’est-à-dire les versions qui corrigent ces failles.

## État de chaque fonctionnalité

| Fonctionnalité | Commande ou URL | Résultat |
|----------------|-----------------|----------|
| Page d’accueil | http://localhost:8081/ | 200, `Bonjour depuis democyber Spring Boot !` |
| Statut JSON | http://localhost:8081/api/status | 200, `{"status":"UP","application":"democyber"}` |
| Santé Actuator | http://localhost:8081/actuator/health | 200, status UP |
| Métriques Prometheus | http://localhost:8081/actuator/prometheus | 200, `jvm_memory_used_bytes` et `democyber_hello_requests_total` |
| Tests Maven | `mvn test` | BUILD SUCCESS, 3 tests |
| Jar | `mvn package` | `target/democyber-0.0.1-SNAPSHOT.jar` |
| Gitleaks | étape GitHub Actions | réussie sur le dernier run |
| Build et test Docker | étapes GitHub Actions | réussis sur le runner GitHub |
| Trivy | scan de `democyber:latest` | l’ancien scan a échoué sur Spring Boot 3.3.0 ; le code corrigé est poussé pour un nouveau scan |
| SonarCloud | étape de `ci.yml` | branchee ; elle s’exécute vraiment quand `SONAR_TOKEN` existe |
| Push Docker Hub | étape de `ci.yml` | branchee ; elle pousse l’image quand `DOCKERHUB_TOKEN` existe |
| Prometheus + Grafana | `monitoring/docker compose` | fichiers prêts ; le moteur Docker local ne démarre pas (Virtual Machine Platform désactivée) |

Le port **8080** est déjà pris par un autre Java (Jetty). L’application du TP est donc lancée sur **8081**. Dans `application.properties`, le port reste 8080, comme dans l’énoncé. L’argument `--server.port=8081` ne sert qu’à cette machine.

## TP1 — Git

Toutes les commandes se lancent dans le dossier du projet.

```powershell
cd C:\Users\yakin\Downloads\tp-devsecops\tp-devsecops
git init -b main
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/amenbhs/tp-devsecops.git
git push -u origin main
```

| Commande | Rôle |
|----------|------|
| `git init -b main` | crée le dépôt local et la branche `main` |
| `git add .` | prépare tous les fichiers suivis (le dossier `target/` est ignoré par `.gitignore`) |
| `git commit` | enregistre un instantané |
| `git remote add origin` | relie le dépôt au compte GitHub amenbhs |
| `git push -u origin main` | envoie `main` et mémorise cette branche pour les prochains push |

Résultat : le projet est en ligne, branche `main`, remote `origin` = `https://github.com/amenbhs/tp-devsecops.git`.

## TP1 — Maven et l’application

Vérifier les outils :

```powershell
java -version
& C:\tools\maven\apache-maven-3.9.9\bin\mvn.cmd -version
```

Java utilisé : 21.0.11. Maven n’est pas dans le PATH Windows ; la version portable est `C:\tools\maven\apache-maven-3.9.9`.

Construire et tester :

```powershell
& C:\tools\maven\apache-maven-3.9.9\bin\mvn.cmd -B clean package
```

`mvn clean package` efface `target/`, compile, lance les 3 tests, produit le jar, puis le plugin Spring Boot y ajoute les dépendances. Résultat obtenu : **BUILD SUCCESS**.

Lancer l’application :

```powershell
java -jar target\democyber-0.0.1-SNAPSHOT.jar --server.port=8081
```

| URL | Fonction | Réponse vérifiée |
|-----|----------|------------------|
| `/` | page d’accueil, incrémente `democyber_hello_requests_total` | `Bonjour depuis democyber Spring Boot !` |
| `/api/status` | statut JSON | `{"status":"UP","application":"democyber"}` |

Fichiers :

- `src/main/java/com/example/democyber/DemocyberApplication.java` : démarre Spring Boot.
- `src/main/java/com/example/democyber/HelloController.java` : les deux URLs et le compteur Prometheus.
- `src/test/java/com/example/democyber/HelloControllerTest.java` : les 3 tests.
- `pom.xml` : dépendances Web, Actuator, Micrometer, tests, JaCoCo.

## TP1 — Docker

Le `Dockerfile` a deux étapes. La première image `maven:3.9-eclipse-temurin-21` compile le jar. La seconde image `eclipse-temurin:21-jre-jammy` ne garde que le jar et tourne avec un utilisateur non-root.

Commandes de l’énoncé, une fois Docker Desktop démarré :

```powershell
docker build -t amenbhs/democyber:latest .
docker run -p 8080:8080 amenbhs/democyber:latest
docker login
docker push amenbhs/democyber:latest
docker run --pull always -p 8080:8080 amenbhs/democyber:latest
```

Sur cette machine, `docker info` ne répond pas : Docker Desktop est installé, mais **Virtual Machine Platform** n’est pas activée. Le build et le test de l’image ont donc été faits par GitHub Actions, qui a Docker. Sur le run https://github.com/amenbhs/tp-devsecops/actions/runs/45240709136 les étapes `Build Docker image` et `Test Docker image locally` ont réussi : le conteneur a démarré et `curl` a reçu la page d’accueil.

Pour débloquer Docker en local, PowerShell administrateur puis redémarrage :

```powershell
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
```

## TP1 et TP2 — Pipeline

Fichier : `.github/workflows/ci.yml`.  
Elle part à chaque push ou pull request vers `main`.

| Étape | Commande ou action | Rôle | Dernier résultat connu |
|-------|--------------------|------|------------------------|
| Checkout | `actions/checkout@v4`, `fetch-depth: 0` | récupère tout l’historique, nécessaire à Gitleaks | succès |
| Gitleaks | `gitleaks/gitleaks-action@v2` | cherche des secrets dans le code et l’historique | succès |
| Upload Gitleaks | `actions/upload-artifact@v4`, fichier `results.sarif` | conserve le rapport même si le scan échoue | succès, artifact `gitleaks-report` |
| JDK 21 | `actions/setup-java@v4`, Temurin | installe Java sur le runner | succès |
| Compile | `mvn -B compile` | vérifie que le code compile | succès |
| Build et tests | `mvn -B clean verify` | tests + rapport JaCoCo | succès |
| SonarCloud | `mvn sonar:sonar` si `SONAR_TOKEN` est présent | analyse qualité et sécurité du code | ignorée tant que le secret n’existe pas, l’étape reste verte |
| Login Docker Hub | `docker login` si `DOCKERHUB_TOKEN` est présent | authentifie le push | ignorée sans secret, l’étape reste verte |
| Build image | `docker build -t democyber:latest .` | construit l’image | succès |
| Test image | `docker run` puis `curl http://localhost:8080` | vérifie que le conteneur répond | succès |
| Trivy JSON | `aquasecurity/trivy-action@v0.36.0`, `exit-code: 0` | rapport, ne bloque pas | succès, artifact `trivy-report` |
| Trivy quality gate | même action, `exit-code: 1`, `severity: CRITICAL,HIGH` | bloque le push si une CVE grave a un correctif | échec sur Spring Boot 3.3.0 ; corrigé par le passage en 4.2.0 |
| Push | `docker push amenbhs/democyber:latest` | publie l’image | exécuté seulement si le gate est vert et si le token Docker Hub existe |

Secrets à créer dans GitHub → Settings → Secrets and variables → Actions :

| Secret | Usage |
|--------|--------|
| `DOCKERHUB_USERNAME` | nom Docker Hub, ici `amenbhs` si le compte porte ce nom |
| `DOCKERHUB_TOKEN` | personal access token Docker Hub, pas le mot de passe |
| `SONAR_TOKEN` | jeton SonarCloud, généré une seule fois |
| `SONAR_ORGANIZATION` | clé de l’organisation SonarCloud |
| `SONAR_PROJECT_KEY` | clé du projet SonarCloud |

Ces valeurs ne vont pas dans le code. Gitleaks ferait échouer la pipeline.

## TP2 — Gitleaks, en détail

Gitleaks parcourt les fichiers et chaque commit. Il reconnaît les formes de secrets : `ghp_...`, mots de passe, clés privées, clés d’API.

L’énoncé demande d’ajouter puis de retirer :

```java
private static final String GITHUB_TOKEN = "ghp_1234567890abcdef1234567890abcdef1234";
```

```powershell
git add .
git commit -m "test: inject fake secret"
git push
```

La pipeline doit devenir rouge : la chaîne a la forme d’un jeton GitHub. Le rapport SARIF donne le fichier, la ligne et le commit.

Commenter la ligne et pousser à nouveau ne suffit souvent pas, parce que l’ancien commit reste dans l’historique (`fetch-depth: 0`). Il faut révoquer un vrai jeton, puis l’enlever de l’historique. Pour le TP, on peut ignorer l’empreinte avec `.gitleaksignore`. Le dépôt actuel ne contient pas ce faux secret, donc Gitleaks est vert.

## TP2 — Trivy, en détail

Trivy compare les bibliothèques de l’image à une base de CVE. Paramètres du gate :

| Paramètre | Valeur | Effet |
|-----------|--------|--------|
| `image-ref` | `democyber:latest` | image construite juste avant |
| `format` | `json` puis `table` | fichier d’artifact, puis tableau dans les logs |
| `severity` | `CRITICAL,HIGH` | seules les failles graves comptent |
| `exit-code` | `0` pour le JSON, `1` pour le gate | le JSON est toujours gardé ; le gate casse la pipeline |
| `ignore-unfixed` | `true` | une CVE sans correctif connu ne bloque pas |

Rapport de l’image Spring Boot 3.3.0, fichier `captures/trivy-report.json` :

- 9 CRITICAL, surtout `tomcat-embed-core` 10.1.24 et `spring-webmvc` 6.1.8 (CVE-2026-47884, exécution de code) ;
- 35 HIGH, dans Tomcat, Spring, Jackson et Micrometer.

C’est ce rapport qui a arrêté le push. Le `pom.xml` est maintenant en Spring Boot 4.2.0 pour sortir de ces versions.

## TP3 — SonarCloud

SonarCloud lit le code source. Il note :

- Security : failles dans la façon d’écrire le code ;
- Reliability : bugs ;
- Maintainability : code smells, duplication ;
- couverture : grâce à JaCoCo, généré par `mvn verify` dans `target/site/jacoco/jacoco.xml`.

Mise en place, sur https://sonarcloud.io connecté avec GitHub :

1. Autoriser SonarCloud sur le dépôt `amenbhs/tp-devsecops`.
2. Créer l’organisation et le projet.
3. My Account → Security → Generate Token, nom `sonar-token`.
4. Copier le jeton tout de suite : il ne réapparaît pas.
5. Ajouter `SONAR_TOKEN`, `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY` dans les secrets GitHub.
6. Pousser sur `main`. L’étape `SonarCloud Scan` envoie alors l’analyse.

Sans ces secrets, l’étape écrit `SONAR_TOKEN absent` et sort avec le code 0, pour que Gitleaks, Maven, Docker et Trivy puissent quand même s’exécuter.

Porte de qualité raisonnable pour un responsable sécurité : aucune nouvelle vulnérabilité, notes Sécurité et Fiabilité au moins A sur le nouveau code, couverture du nouveau code d’au moins 80 %, duplications inférieures à 3 %.

Différence des trois outils :

| Outil | Il regarde | Il trouve |
|-------|------------|-----------|
| Gitleaks | code et historique Git | un secret copié dans le dépôt |
| Trivy | image Docker et bibliothèques | une CVE déjà publiée |
| SonarCloud | le code écrit par le développeur | un bug, une faille de code, un manque de tests |

## TP4 — Actuator, Prometheus, Grafana

`application.properties` :

```properties
management.endpoints.web.exposure.include=*
management.endpoint.prometheus.enabled=true
```

Vérifications déjà faites sur http://localhost:8081 :

```powershell
curl.exe http://localhost:8081/actuator/health
curl.exe -H "Accept: text/plain" http://localhost:8081/actuator/prometheus
```

Santé : UP. Prometheus : métriques JVM et compteur `democyber_hello_requests_total`, qui augmente à chaque appel de `/`.

Quand Docker local fonctionne :

```powershell
cd C:\Users\yakin\Downloads\tp-devsecops\tp-devsecops
java -jar target\democyber-0.0.1-SNAPSHOT.jar
cd monitoring
docker compose up -d
```

| Service | URL | Identifiants |
|---------|-----|----------------|
| Prometheus | http://localhost:9090 | Status → Targets → `democyber` doit être UP |
| Grafana | http://localhost:3001 | `admin` / `admin` |

Le conteneur Grafana écoute 3000 et il est publié sur **3001**, comme dans le `docker-compose.yml` de l’énoncé. La source de données Prometheus, depuis Grafana, est `http://prometheus:9090`. Dashboard Spring Boot : Import, ID `11378`. Il affiche la mémoire JVM, le CPU et les requêtes HTTP.

`monitoring/prometheus.yml` interroge `host.docker.internal:8080` toutes les 5 secondes. Il faut donc que l’application écoute sur 8080 au moment de cette supervision. Sur cette machine, libérer d’abord le Java qui occupe 8080, puis relancer le jar sans `--server.port=8081`.

## Commandes de contrôle, dans l’ordre

```powershell
cd C:\Users\yakin\Downloads\tp-devsecops\tp-devsecops
& C:\tools\maven\apache-maven-3.9.9\bin\mvn.cmd -B test
java -jar target\democyber-0.0.1-SNAPSHOT.jar --server.port=8081
curl.exe http://localhost:8081/
curl.exe http://localhost:8081/api/status
curl.exe http://localhost:8081/actuator/health
curl.exe -H "Accept: text/plain" http://localhost:8081/actuator/prometheus
git status -sb
git push origin main
```

Le dernier `git push` relance la pipeline complète sur https://github.com/amenbhs/tp-devsecops/actions.
