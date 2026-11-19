# Jenkins CI/CD Pipeline: fee-service

A Jenkins **declarative pipeline** that automatically builds, tests, packages and deploys a small Java service every time code is pushed to GitHub.

The application, **fee-service**, is a small HTTP API that returns a tiered transfer fee for an amount:

```
GET /health               -> {"status":"UP","version":"1.0.0"}
GET /api/fee?amount=1000  -> {"amount":"1000","fee":"10.75","total":"1010.75"}
```

| Stack | |
|---|---|
| Language / build | Java 17, Maven 3 |
| Tests | JUnit 5 (unit + HTTP integration tests) |
| CI/CD | Jenkins LTS in Docker, configured entirely as code (JCasC + Job DSL) |
| Trigger | GitHub webhook, with SCM polling as a fallback |

---

## 1. Architecture

```
 Developer ──git push──▶ GitHub repo ──webhook / poll──▶ Jenkins (Docker)
                                                          │
            ┌─────────────────────────────────────────────┘
            ▼
   Checkout ─▶ Build ─▶ Test ─▶ Package ─▶ Deploy (Test env)
   (git)       (mvn      (mvn     (jar +      (deploy.sh: install release,
               compile)  test +   archive)    start app on :8085, health
                         JUnit                 check + smoke test)
                         report)
```

## 2. Repository layout

```
.
├── Jenkinsfile               # Declarative pipeline (the automation workflow)
├── pom.xml                   # Maven build: JUnit 5, Surefire, executable jar
├── src/main/java/...         # App.java (HTTP server), FeeCalculator.java
├── src/test/java/...         # FeeCalculatorTest, AppTest
├── scripts/deploy.sh         # Simulated deployment + smoke tests
├── docker-compose.yml        # Runs Jenkins locally
├── jenkins/
│   ├── Dockerfile            # Jenkins LTS + Maven + plugins
│   ├── plugins.txt           # Plugin list (installed at image build time)
│   └── casc.yaml             # JCasC: admin user, security, tools, credentials, pipeline job
├── .env.example              # Template for secrets (real .env is git-ignored)
└── docs/screenshots/         # Pipeline execution evidence
```

---

## 3. Setup process

### Prerequisites
- Docker Desktop (or Colima) running
- Git, plus a GitHub account
- Optional, to build outside Jenkins: JDK 17+ and Maven (`brew install openjdk@17 maven`)

### Task 1: Environment setup (Jenkins)
Jenkins runs as a container built from `jenkins/Dockerfile`, which is based on `jenkins/jenkins:lts-jdk17` (this image supports both Apple Silicon and x86). The Dockerfile:
- installs Maven so pipelines can build Java projects
- pre-installs all plugins from `plugins.txt`
- skips the setup wizard and configures everything from `casc.yaml`

### Task 2: Push the code to GitHub and connect Jenkins
```bash
cd jenkins-cicd-pipeline
git init -b main
git add .
git commit -m "Initial commit: fee-service with Jenkins declarative pipeline"
# create an empty repo named jenkins-cicd-pipeline on GitHub, then:
git remote add origin https://github.com/<you>/jenkins-cicd-pipeline.git
git push -u origin main
```

Create a **fine-grained Personal Access Token** (GitHub → Settings → Developer settings) that can read **Contents** of this repository only. Jenkins uses it to clone the repo.

### Task 7: Plugins and secure credentials
```bash
cp .env.example .env
# edit .env: admin password, APP_REPO_URL, GITHUB_USERNAME, GITHUB_TOKEN
# generate a deploy token:
openssl rand -hex 24      # paste into TEST_DEPLOY_TOKEN
```

How the credentials are kept secure:
- No secret is committed. `casc.yaml` only contains `${VARIABLE}` placeholders, and they are filled from `.env`, which is listed in `.gitignore`.
- On startup, JCasC creates two entries in the Jenkins credential store:
  - `github-pat` (username/password): used by the SCM checkout
  - `test-env-deploy-token` (secret text): injected into the Deploy stage with `withCredentials`. Jenkins masks it as `****` in the console log, and `deploy.sh` only prints its length.
- Security realm: one local admin user, sign-up disabled, and anonymous read access turned off. CSRF protection (crumb issuer) is on.
- The GitHub PAT is fine-grained and read-only, so it has the least privilege it needs.

Plugins installed (`jenkins/plugins.txt`):

| Plugin | Purpose |
|---|---|
| workflow-aggregator | Pipeline (declarative) support |
| git, github | SCM checkout, `githubPush()` webhook trigger |
| credentials, credentials-binding, plain-credentials | Credential store + `withCredentials` |
| configuration-as-code, job-dsl | Jenkins + pipeline job defined as code |
| junit | Publishes test results and fails/unstables on test failures |
| pipeline-stage-view, pipeline-graph-view | Stage visualisation on the dashboard |
| timestamper, ws-cleanup | Timestamped logs, workspace cleanup |

### Start Jenkins
```bash
docker compose up -d --build
docker compose logs -f jenkins     # wait for "Jenkins is fully up and running"
```
Open http://localhost:8080 and log in with the admin credentials from `.env`. The job **fee-service-pipeline** already exists because JCasC creates it with Job DSL.

### Task 2 (cont.): Automatic triggers
The `triggers {}` block in the Jenkinsfile sets up both trigger types:
- **`githubPush()`** builds as soon as GitHub delivers a push webhook. GitHub can't reach `localhost`, so expose Jenkins to the internet first:
  ```bash
  ngrok http 8080
  ```
  Then in the GitHub repo go to Settings → Webhooks → Add webhook:
  - Payload URL: `https://<ngrok-id>.ngrok-free.app/github-webhook/` (the trailing slash is required)
  - Content type: `application/json`
  - Events: *Just the push event*
- **`pollSCM('H/2 * * * *')`** is a fallback that checks the repo for new commits about every 2 minutes. It works without a public URL.

> Jenkins only registers these triggers after the pipeline has run once, so run the first build manually with **Build Now**.

### Tasks 3–6: The Jenkinsfile
| Stage | What it does | How it can fail |
|---|---|---|
| **Checkout** | `checkout scm`, prints the commit | Repo or credential problem |
| **Build** | `mvn clean compile` (downloads dependencies, compiles) | Compilation error |
| **Test** | `mvn test` runs 18 JUnit tests; `junit` publishes the report | **Any failing test fails the stage and the pipeline** |
| **Package** | `mvn package` builds `fee-service.jar`, archived and fingerprinted | Packaging error |
| **Deploy (Test env)** | `deploy.sh` stops the previous version, installs the new release, starts it on port 8085, checks `/health`, then smoke-tests `/api/fee` | Health or smoke check fails, or the credential is missing |

Pipeline options: timestamps, no concurrent builds, a 20-minute timeout, build/artifact retention limits, and the workspace is cleaned after every run.

### Task 8: Validation
1. Jenkins → **fee-service-pipeline** → **Build Now**
2. Watch **Stage View** (or **Pipeline Overview**): all five stages should turn green
3. Open **Console Output** to read the full log, including the masked token and the smoke-test output
4. Check the **Tests** tab: 18 passed
5. Check that the deployed service is running:
   ```bash
   curl http://localhost:8085/health
   curl "http://localhost:8085/api/fee?amount=20000"
   ```
6. Test the automatic trigger: change something, `git push`, and a new build should start without clicking anything
7. Prove the quality gate works: change an expected value in `FeeCalculatorTest`, push, and confirm the **Test** stage goes red while **Package** and **Deploy** are skipped. Revert the change and push again to get back to green.

### Run locally without Jenkins
```bash
mvn clean verify
APP_PORT=8085 java -jar target/fee-service.jar
```

---

## 4. Screenshots

Save the screenshots in `docs/screenshots/`:

| File | Shows |
|---|---|
| `01-jenkins-dashboard.png` | Dashboard with the job |
| `02-stage-view-green.png` | All stages green (Checkout, Build, Test, Package, Deploy) |
| `03-console-output.png` | Console log with the deploy smoke test and masked credential |
| `04-test-results.png` | JUnit results, 18/18 passed |
| `05-credentials.png` | Credentials page (IDs only, no secret values) |
| `06-webhook-triggered-build.png` | A build "Started by GitHub push" |

![Stage view](docs/screenshots/02-stage-view-green.png)

---

## 5. Challenges and how they were resolved

| Challenge | Resolution |
|---|---|
| Jenkins kills background processes when a build ends, so the deployed app died right after the Deploy stage | Run the deploy step with `JENKINS_NODE_COOKIE=dontKillMe` so the process tree killer leaves the app running |
| GitHub webhooks can't reach a Jenkins on `localhost` | Expose port 8080 with ngrok; `pollSCM` covers builds when the tunnel is down |
| The `triggers {}` in a Jenkinsfile only take effect after the first run | Run the first build manually (documented above) |
| The official Jenkins image doesn't include Maven | Install Maven in a custom image and register it as tool `Maven-3` with JCasC |
| Secrets must not be in Git, but Jenkins needs them on startup | Use JCasC `${VAR}` placeholders filled from a git-ignored `.env` file, then consume them with `withCredentials` |
| Manual UI configuration isn't reproducible | Define plugins, security, tools, credentials and the job as code; `docker compose up` rebuilds the whole setup |
| Running on Apple Silicon (arm64) | Use the multi-arch `jenkins/jenkins:lts-jdk17` image |

*Add any other issues you hit during your own run here, and put the relevant build logs in `docs/logs/`.*

---

## 6. Teardown
```bash
docker compose down        # stop Jenkins and keep its data
docker compose down -v     # also delete the jenkins_home volume
```
