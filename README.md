# Banking Application

A Spring Boot REST banking service (customers, accounts, transactions) packaged for a
Jenkins CI/CD pipeline:

```
checkout ─► build & test ─► SonarQube + gate ─► OWASP Dependency-Check
        ─► JAR to Nexus ─► docker build ─► Trivy scan ─► image to Nexus
```

Single repo. The pipeline produces a scanned, published artifact (JAR + image in
Nexus). Deployment is out of scope here — point your deploy tooling at the image.

## Tech stack

| Concern        | Choice                                  |
|----------------|-----------------------------------------|
| Language / JDK | Java 21                                 |
| Framework      | Spring Boot 3.3 (Web, Data JPA, Validation, Actuator) |
| Build          | Maven, executable fat JAR (`target/bankapp.jar`) |
| Database       | MySQL 8 in production, H2 in tests / local dev |
| Migrations     | Flyway (`prod` profile only)            |
| API docs       | springdoc-openapi -> `/swagger-ui.html` |
| Coverage       | JaCoCo -> `target/site/jacoco/jacoco.xml` (consumed by Sonar) |
| Packaging      | Docker image (`eclipse-temurin:21-jre`), pushed to Nexus |
| Security scans | SonarQube (quality gate), OWASP Dependency-Check, Trivy (image) |

## Project layout

```
bankapp/
├── pom.xml                      # build, JaCoCo, Sonar plugin, Nexus distributionManagement
├── Jenkinsfile                  # the full pipeline
├── Dockerfile                   # runtime image (expects target/bankapp.jar)
├── docker-compose.yml           # local: MySQL + app
├── ci/settings.xml              # sample Maven settings for Jenkins (Nexus servers)
├── api-examples.http            # sample REST calls
├── src/main/java/com/motivalogic/bankapp/
│   ├── domain/                  # JPA entities + enums
│   ├── repository/              # Spring Data repositories
│   ├── dto/                     # request/response records
│   ├── service/                 # business logic (deposit, withdraw, transfer)
│   ├── web/                     # REST controllers
│   ├── exception/               # error model + @RestControllerAdvice
│   └── config/                  # OpenAPI config
├── src/main/resources/
│   ├── application.yml          # base + dev profile (H2)
│   ├── application-prod.yml     # MySQL + Flyway
│   └── db/migration/            # Flyway SQL
└── src/test/java/...            # service + web integration tests (H2)
```

## Run locally

```bash
# unit + integration tests (H2, no external services)
mvn clean verify

# run the app on H2
mvn spring-boot:run                       # http://localhost:8080/swagger-ui.html

# full stack on MySQL via Docker
mvn -DskipTests package
docker compose up --build
```

## REST API

Base path `/api/v1`. Full contract at `/swagger-ui.html` / `/v3/api-docs`.

| Method | Path                                        | Purpose                    |
|--------|---------------------------------------------|----------------------------|
| POST   | `/customers`                                | create customer            |
| GET    | `/customers`, `/customers/{id}`             | list / fetch               |
| PUT    | `/customers/{id}`                           | update                     |
| DELETE | `/customers/{id}`                           | delete                     |
| POST   | `/accounts`                                 | open account               |
| GET    | `/accounts/{accountNumber}`                 | fetch account              |
| GET    | `/accounts?customerId={id}`                 | list customer accounts     |
| POST   | `/accounts/{accountNumber}/status?status=`  | ACTIVE / FROZEN / CLOSED   |
| POST   | `/accounts/{accountNumber}/deposits`        | deposit                    |
| POST   | `/accounts/{accountNumber}/withdrawals`     | withdraw                   |
| POST   | `/transfers`                                | transfer between accounts  |
| GET    | `/accounts/{accountNumber}/transactions`    | paged statement            |

Money is `BigDecimal` (scale 2). Transfers lock both accounts in a consistent order
and are atomic; withdrawals/transfers reject insufficient funds (HTTP 422) and
non‑ACTIVE accounts (HTTP 409).

## Pipeline (`Jenkinsfile`, declarative)

| Stage | What it does |
|---|---|
| Checkout | `checkout scm`; resolve `APP_VERSION` / `IMAGE_TAG` from `pom.xml` |
| Build & Unit Test | `mvn -s settings clean verify` (JAR + JUnit + JaCoCo) |
| SonarQube Analysis + Quality Gate | `withSonarQubeEnv` + `mvn sonar:sonar`, `waitForQualityGate abortPipeline: true` |
| OWASP Dependency-Check | `mvn dependency-check:check` — report-only (`DC_FAIL_CVSS=11`; set 7 to fail on HIGH+), HTML report published |
| Publish JAR to Nexus | `mvn -s settings deploy` → `maven-releases` / `maven-snapshots` |
| Build Docker image | `docker build -t bankapp:$IMAGE_TAG .` |
| Trivy scan | `trivy image --severity HIGH,CRITICAL --exit-code 1` — fails the build on findings |
| Push image to Nexus | tag + `docker push` to the Nexus docker (hosted) registry (`$IMAGE_TAG` and `latest`) |
| DAST - OWASP ZAP baseline | after Deploy to Stage: waits for `/actuator/health`, runs `zap-baseline.py` against `STAGE_APP_URL`; findings mark the build UNSTABLE, HTML report published |

### Agent requirements

The Jenkins agent needs **JDK 17, Maven, the `docker` CLI, and `trivy`** on `PATH`.

### Required Jenkins plugins

Pipeline, Git, **SonarQube Scanner**, **Config File Provider**, **Docker Pipeline**,
**HTML Publisher**, JaCoCo, JUnit.

### Jenkins configuration to provide

| Item | Where | Notes |
|------|-------|-------|
| `maven`, `java` (JDK 17) | Manage Jenkins → Tools | names must match `tools {}` |
| `sonarqube` server | Manage Jenkins → System → SonarQube servers | matches `SONARQUBE_ENV` |
| `nexus-maven-settings` | Managed files (Config File Provider) | paste `ci/settings.xml` verbatim; point URLs at your Nexus |
| `nexus-cred` | Credential (username/password) | Nexus login — Maven repo (bound to `NX_USER`/`NX_PASS`) and Docker registry |
| `NVD_API_KEY` | Global env var (optional) | speeds up the OWASP NVD download |

Set `NEXUS_DOCKER_REGISTRY` in the `Jenkinsfile` and `nexus.url` in `pom.xml` for
your environment. SNAPSHOT vs release is chosen by `project.version`.

## Secure Local Database Configuration

### Purpose

Docker Compose is used to run BankApp and a local MySQL database for development and testing.

Our AWS deployment uses Jenkins, Ansible, Amazon RDS, and AWS Secrets Manager instead. Docker Compose is not used for our AWS deployment.

### What is a .env file?

A `.env` file stores local environment variables, such as database passwords, outside the application configuration.

The `.env` file is ignored by Git to prevent local credentials from being accidentally committed to GitHub.

### How to configure your local database

1. Clone the repository to your computer.
2. Copy `.env.example` and rename the copy to `.env`.
3. Open `.env` and replace the example values with your own strong local passwords:

   ```dotenv
   MYSQL_PASSWORD=your_own_local_password
   MYSQL_ROOT_PASSWORD=your_own_local_root_password
   ```

4. Save the `.env` file. Do not commit or push this file to GitHub.
5. Make sure Docker is running, then start the application and MySQL database:

   ```bash
   mvn -DskipTests package
   docker compose up --build
   ```

Docker Compose reads the passwords from `.env` and passes them to the local MySQL database and BankApp.

### Security Notes

- Never commit `.env` or real database passwords to GitHub.
- `.env.example` contains placeholders only and can be safely committed.
- Use strong, unique passwords for local development.
- AWS database credentials are managed separately through AWS Secrets Manager.
- Changing `.env` does not automatically change passwords in an existing MySQL database.
