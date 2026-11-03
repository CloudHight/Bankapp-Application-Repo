// bankapp application pipeline (declarative).
//
//  Checkout (release ID) ─► Set release version ─► SonarQube + Quality Gate ─►
//  OWASP Dependency-Check ─► Build & Unit Test ─► Publish JAR to Nexus ─►
//  Build Docker image ─► Trivy scan ─► Push image to Nexus (capture digest + release.json) ─►
//  Deploy to Stage (by digest) ─► DAST (OWASP ZAP baseline) ─► [manual approval] ─►
//  Deploy to Prod (same digest).
//
// CT-57 Immutable artifact promotion:
//   - Every build gets a unique RELEASE_ID: <version>-<build number>-<short commit>, e.g. 1.0.0-42-a1b2c3d
//   - The JAR is published with that version (maven-releases, can't be overwritten)
//   - The image is tagged with RELEASE_ID only (no "latest") and labelled with version + commit
//   - After the push, the image digest (sha256:...) is captured
//   - Stage and prod are both deployed with <registry>/bankapp@sha256:..., so prod runs exactly
//     the image that was tested on stage
//   - release.json (commit, version, digest) and deployment-<env>.json are archived with the build
//
// Deploy is done by Ansible on the Ansible server, started through SSM Run Command.
// The playbook deploy-bankapp.yml pulls the image this pipeline pushed and (re)runs it on the
// stage_hosts / prod_hosts inventory. Nexus and DB credentials are read from Secrets Manager on
// the target hosts; none are passed from Jenkins.
//
// Requires on the Jenkins agent: JDK 17, Maven, docker CLI, trivy, aws CLI, python3, curl
// (ZAP runs from the ghcr.io/zaproxy/zaproxy image; the agent must reach STAGE_APP_URL).
// Jenkins credentials / config used:
//   - SonarQube server 'sonarqube'          (Manage Jenkins > System, + webhook in SonarQube
//                                            to https://jenkins.<domain>/sonarqube-webhook/)
//   - Config File 'nexus-maven-settings'    (Config File Provider)
//   - Username/password 'nexus-cred'        (Nexus login: Maven repo + Docker registry;
//                                            bound to NX_USER/NX_PASS for settings.xml)
// AWS access comes from the Jenkins instance role (ssm:SendCommand on the Ansible server).

pipeline {
    agent any

    tools {
        maven 'maven'
        jdk 'java'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 45, unit: 'MINUTES')
    }

    environment {
        SONARQUBE_ENV         = 'sonarqube'              // Manage Jenkins > System > SonarQube servers
        MAVEN_SETTINGS        = 'nexus-maven-settings'   // Config File Provider managed file id
        NEXUS_DOCKER_REGISTRY = 'nexus.everythingops.io:8085' // Nexus docker-hosted registry (terraform output nexus_registry)
        NEXUS_DOCKER_CREDS    = 'nexus-cred'
        IMAGE_NAME            = 'bankapp'
        TRIVY_SEVERITY        = 'HIGH,CRITICAL'
        TRIVY_CACHE_DIR       = '/var/lib/jenkins/trivy-cache'   // persists across cleanWs()
        DC_FAIL_CVSS          = '11'                     // Dependency-Check: 11 = report-only, 7 = fail on HIGH+

        // Deploy targets, looked up at deploy time (no IPs or endpoints hard-coded)
        AWS_REGION            = 'eu-west-3'
        ANSIBLE_SERVER_NAME   = 'ansible_19thoct'        // Name tag of the Ansible server
        SCRIPTS_BUCKET_PREFIX = 'ansible.tf'             // + "-<account id>": holds deploy-bankapp.yml
        DB_INSTANCE           = 'bankapp_db_19thoct'     // RDS instance; endpoint + managed secret read from it
        NVD_API_KEY           = credentials('nvd-key')   // optional: speeds up OWASP Dependency-Check
        // DAST - URL of the app as deployed on the stage hosts
        STAGE_APP_URL         = 'https://stage.everythingops.io'   // stage ALB (TLS on 443; 8080 is not exposed)
        ZAP_IMAGE             = 'ghcr.io/zaproxy/zaproxy:stable'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    // Version from pom.xml, e.g. 1.0.0-SNAPSHOT
                    env.APP_VERSION = sh(
                        returnStdout: true,
                        script: "awk '/<artifactId>bankapp<\\/artifactId>/{f=1} f && /<version>/{gsub(/.*<version>|<\\/version>.*/, \"\"); print; exit}' pom.xml"
                    ).trim()
                    // Drop "-SNAPSHOT" so releases get a clean version, e.g. 1.0.0
                    env.BASE_VERSION = env.APP_VERSION.replace('-SNAPSHOT', '')

                    // The exact Git commit this build comes from
                    env.GIT_COMMIT_FULL  = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                    env.GIT_COMMIT_SHORT = sh(returnStdout: true, script: 'git rev-parse --short=7 HEAD').trim()

                    // Unique release ID, e.g. 1.0.0-42-a1b2c3d
                    env.RELEASE_ID = "${env.BASE_VERSION}-${env.BUILD_NUMBER}-${env.GIT_COMMIT_SHORT}"
                    env.IMAGE_TAG  = env.RELEASE_ID

                    // Show the release ID on the build in the Jenkins UI
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.RELEASE_ID}"

                    echo "RELEASE_ID=${env.RELEASE_ID}"
                    echo "GIT_COMMIT=${env.GIT_COMMIT_FULL}"
                }
            }
        }

        stage('Set release version') {
            steps {
                withCredentials([usernamePassword(credentialsId: env.NEXUS_DOCKER_CREDS,
                                                  usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
                    configFileProvider([configFile(fileId: "${MAVEN_SETTINGS}", variable: 'SETTINGS')]) {
                        // Change the version in pom.xml from 1.0.0-SNAPSHOT to the release ID,
                        // only inside this build's workspace (nothing is committed back to Git)
                        sh 'mvn -B -s $SETTINGS versions:set -DnewVersion=$RELEASE_ID -DgenerateBackupPoms=false'
                    }
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withCredentials([usernamePassword(credentialsId: env.NEXUS_DOCKER_CREDS,
                                                  usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
                    configFileProvider([configFile(fileId: "${MAVEN_SETTINGS}", variable: 'SETTINGS')]) {
                        withSonarQubeEnv("${SONARQUBE_ENV}") {
                            // Sonar needs compiled classes (and picks up the JaCoCo report),
                            // so run the build in the same invocation - this stage precedes
                            // 'Build & Unit Test' in the pipeline order.
                            sh 'mvn -B -s $SETTINGS clean verify sonar:sonar -Dsonar.projectVersion=$RELEASE_ID'
                        }
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('OWASP Dependency-Check') {
            steps {
                // Set NVD_API_KEY as a Jenkins global env var to speed up the NVD
                // download. Without it the first run can take longer than the
                // pipeline timeout; later runs reuse the NVD data cached in ~/.m2.
                // Report-only like Trivy: DC_FAIL_CVSS=11 never fails on findings
                // (lower it to 7 to gate on HIGH/CRITICAL once dependencies are updated).
                withCredentials([usernamePassword(credentialsId: env.NEXUS_DOCKER_CREDS,
                                                  usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
                    configFileProvider([configFile(fileId: "${MAVEN_SETTINGS}", variable: 'SETTINGS')]) {
                        sh '''
                            set +x
                            mvn -B -s $SETTINGS dependency-check:check \
                              -Ddependency-check.failBuildOnCVSS=$DC_FAIL_CVSS \
                              ${NVD_API_KEY:+-Dnvd.api.key=$NVD_API_KEY}
                        '''
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.*', allowEmptyArchive: true
                    publishHTML(target: [reportDir: 'target', reportFiles: 'dependency-check-report.html',
                                         reportName: 'OWASP Dependency-Check', keepAll: true,
                                         alwaysLinkToLastBuild: true, allowMissing: true])
                }
            }
        }

        stage('Build & Unit Test') {
            // Build + tests already ran in 'SonarQube Analysis' (clean verify sonar:sonar);
            // this stage just publishes the reports from that run.
            steps {
                junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                jacoco execPattern: 'target/jacoco.exec'
            }
        }

        stage('Publish JAR to Nexus') {
            steps {
                // The version is now RELEASE_ID (not a SNAPSHOT), so this goes to maven-releases,
                // where the same version can't be uploaded twice.
                withCredentials([usernamePassword(credentialsId: env.NEXUS_DOCKER_CREDS,
                                                  usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
                    configFileProvider([configFile(fileId: "${MAVEN_SETTINGS}", variable: 'SETTINGS')]) {
                        sh 'mvn -B -s $SETTINGS deploy -DskipTests'
                    }
                }
            }
        }

        stage('Build Docker image') {
            steps {
                // DOCKER_BUILDKIT=0: the old Nexus (3.23) registry rejects BuildKit's
                // OCI manifests / chunked blob uploads. The legacy builder produces a
                // Docker v2 schema2 image that pushes cleanly.
                //
                // Bounded on its own: apt-get inside the Dockerfile hitting a flaky
                // mirror once ate ~30 min with nothing to stop it short of the
                // pipeline-wide 45 min budget, forcing a manual abort.
                //
                // The labels record the release ID, Git commit, build time and Jenkins build
                // inside the image (read them with: docker inspect <image>).
                timeout(time: 10, unit: 'MINUTES') {
                    sh '''
                        DOCKER_BUILDKIT=0 docker build \
                          --build-arg JAR_FILE=target/bankapp.jar \
                          --label "org.opencontainers.image.version=$RELEASE_ID" \
                          --label "org.opencontainers.image.revision=$GIT_COMMIT_FULL" \
                          --label "org.opencontainers.image.created=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
                          --label "jenkins.build.url=$BUILD_URL" \
                          -t $IMAGE_NAME:$IMAGE_TAG .
                    '''
                }
            }
        }

        stage('Trivy scan') {
            steps {
                sh 'df -h / | tail -1'
                // Report-only: surface HIGH/CRITICAL findings in the log but do not
                // fail the build on them (--exit-code 0). Tighten to --exit-code 1
                // once the dependency tree is clean.
                sh '''
                    mkdir -p "$TRIVY_CACHE_DIR"
                    trivy image --scanners vuln --no-progress --ignore-unfixed \
                      --cache-dir "$TRIVY_CACHE_DIR" \
                      --severity $TRIVY_SEVERITY --exit-code 0 \
                      $IMAGE_NAME:$IMAGE_TAG
                '''
            }
        }

        stage('Push image to Nexus') {
            steps {
                script {
                    // Push the unique release tag only. No "latest" tag is pushed.
                    docker.withRegistry("https://${NEXUS_DOCKER_REGISTRY}", env.NEXUS_DOCKER_CREDS) {
                        sh 'docker tag  $IMAGE_NAME:$IMAGE_TAG $NEXUS_DOCKER_REGISTRY/$IMAGE_NAME:$IMAGE_TAG'
                        sh 'docker push $NEXUS_DOCKER_REGISTRY/$IMAGE_NAME:$IMAGE_TAG'
                    }

                    // After the push, Docker knows the digest the registry gave the image.
                    // The digest (sha256:...) is the image's fingerprint: it can never point
                    // at different content, unlike a tag.
                    env.IMAGE_DIGEST = sh(returnStdout: true, script: '''
                        docker inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' \
                          $NEXUS_DOCKER_REGISTRY/$IMAGE_NAME:$IMAGE_TAG \
                          | grep "^$NEXUS_DOCKER_REGISTRY/$IMAGE_NAME@" | head -1 | cut -d@ -f2
                    ''').trim()

                    if (!env.IMAGE_DIGEST.startsWith('sha256:')) {
                        error "Could not read the image digest after the push"
                    }

                    // The reference every deployment uses: registry/bankapp@sha256:...
                    env.IMAGE_REF = "${env.NEXUS_DOCKER_REGISTRY}/${env.IMAGE_NAME}@${env.IMAGE_DIGEST}"
                    echo "IMAGE_DIGEST=${env.IMAGE_DIGEST}"
                    echo "IMAGE_REF=${env.IMAGE_REF}"
                }

                // Release metadata: which commit, which version, which image digest
                sh '''
                    python3 -c '
import json, os, datetime
e = os.environ
json.dump({
    "release_id":       e["RELEASE_ID"],
    "git_commit":       e["GIT_COMMIT_FULL"],
    "artifact_version": e["RELEASE_ID"],
    "jar":              "bankapp-" + e["RELEASE_ID"] + ".jar",
    "image_tag":        e["NEXUS_DOCKER_REGISTRY"] + "/" + e["IMAGE_NAME"] + ":" + e["IMAGE_TAG"],
    "image_digest":     e["IMAGE_DIGEST"],
    "image_ref":        e["IMAGE_REF"],
    "build_number":     e["BUILD_NUMBER"],
    "build_url":        e["BUILD_URL"],
    "built_at":         datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
}, open("release.json", "w"), indent=2)
'
                    cat release.json
                '''
                archiveArtifacts artifacts: 'release.json', fingerprint: true
            }
        }

        stage('Deploy to Stage') {
            steps {
                deployWithAnsible('stage')
            }
        }

        stage('DAST - OWASP ZAP baseline') {
            steps {
                // Wait for the freshly deployed stage container to report healthy
                // before scanning it (30 tries, at most ~10 min).
                sh '''
                    for i in $(seq 1 30); do
                      curl -fsS --connect-timeout 5 --max-time 10 "$STAGE_APP_URL/actuator/health" && exit 0
                      echo "waiting for $STAGE_APP_URL ($i/30)"; sleep 10
                    done
                    echo "stage app never became healthy"; exit 1
                '''
                // Passive baseline scan (spider + passive rules, no attacks) against
                // stage. ZAP runs as uid 1000 inside the container, so give it a
                // world-writable report dir. Exit codes: 0 clean, 1 FAIL rules,
                // 2 WARN rules, 3+ ZAP error. Report-only like Trivy: findings mark
                // the build UNSTABLE but still allow promotion; ZAP errors fail it.
                script {
                    sh 'mkdir -p zap-report && chmod 777 zap-report'
                    def rc = sh(returnStatus: true, script: '''
                        docker run --rm --network host \
                          -v "$WORKSPACE/zap-report:/zap/wrk:rw" \
                          $ZAP_IMAGE zap-baseline.py \
                            -t "$STAGE_APP_URL" \
                            -r zap-report.html -J zap-report.json -w zap-report.md \
                            -m 3
                    ''')
                    if (rc >= 3) {
                        error "ZAP baseline scan failed to run (exit ${rc})"
                    } else if (rc != 0) {
                        unstable "ZAP baseline reported findings (exit ${rc}) - see the ZAP report"
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'zap-report/zap-report.*', allowEmptyArchive: true
                    publishHTML(target: [reportDir: 'zap-report', reportFiles: 'zap-report.html',
                                         reportName: 'OWASP ZAP DAST', keepAll: true,
                                         alwaysLinkToLastBuild: true, allowMissing: true])
                }
            }
        }

        stage('Approve production deployment') {
            steps {
                timeout(time: 1, unit: 'HOURS') {
                    input message: "Deploy ${env.RELEASE_ID} (${env.IMAGE_DIGEST}) to PRODUCTION? This is the same image that was tested on stage.",
                          ok: 'Deploy to prod'
                }
            }
        }

        stage('Deploy to Prod') {
            steps {
                // Same IMAGE_REF (same digest) that was deployed to stage
                deployWithAnsible('prod')
            }
        }
    }

    post {
        success { echo "OK - deployed ${env.RELEASE_ID} (${env.IMAGE_REF}) to stage and prod" }
        failure { echo 'Pipeline failed - see stage logs' }
        always  {
            // Drop the images/build cache this build created so the agent disk doesn't fill up.
            sh '''
                docker rmi $IMAGE_NAME:$IMAGE_TAG \
                           $NEXUS_DOCKER_REGISTRY/$IMAGE_NAME:$IMAGE_TAG 2>/dev/null || true
                docker image prune -f || true
                docker builder prune -f || true
            '''
            cleanWs()
        }
    }
}

// Run deploy-bankapp.yml on the Ansible server for one environment ('stage' or 'prod') through
// SSM Run Command, wait for it, print Ansible's output and fail the stage if the playbook failed.
// It always deploys env.IMAGE_REF (registry/bankapp@sha256:...), never a tag, and records what it
// deployed in deployment-<env>.json.
def deployWithAnsible(String envName) {
    withEnv(["DEPLOY_ENV=${envName}"]) {
        try {
            sh '''
                set -eu
                echo "Deploying $RELEASE_ID to $DEPLOY_ENV"
                echo "Image: $IMAGE_REF"

                ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
                BUCKET="$SCRIPTS_BUCKET_PREFIX-$ACCOUNT_ID"
                DB_HOST=$(aws rds describe-db-instances --region "$AWS_REGION" --db-instance-identifier "$DB_INSTANCE" \
                            --query 'DBInstances[0].Endpoint.Address' --output text)
                DB_SECRET=$(aws rds describe-db-instances --region "$AWS_REGION" --db-instance-identifier "$DB_INSTANCE" \
                            --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)
                INSTANCE_ID=$(aws ec2 describe-instances --region "$AWS_REGION" \
                            --filters "Name=tag:Name,Values=$ANSIBLE_SERVER_NAME" Name=instance-state-name,Values=running \
                            --query 'Reservations[0].Instances[0].InstanceId' --output text)
                echo "Ansible server: $INSTANCE_ID"

                # Command run as root on the Ansible server: fetch the current playbook, run it as ec2-user
                CMD="cd /home/ec2-user/ansible && aws s3 cp s3://$BUCKET/ansible-script/deploy-bankapp.yml deploy-bankapp.yml --quiet && sudo -u ec2-user env ANSIBLE_HOST_KEY_CHECKING=False ansible-playbook -i ${DEPLOY_ENV}_hosts deploy-bankapp.yml --private-key /home/ec2-user/.ssh/id_rsa -e image_ref=$IMAGE_REF -e db_host=$DB_HOST -e db_secret_id=$DB_SECRET"
                python3 -c 'import json,sys; print(json.dumps({"commands": [sys.argv[1]], "executionTimeout": ["900"]}))' "$CMD" > ssm-params.json

                COMMAND_ID=$(aws ssm send-command --region "$AWS_REGION" --instance-ids "$INSTANCE_ID" \
                               --document-name AWS-RunShellScript --comment "bankapp $DEPLOY_ENV $RELEASE_ID" \
                               --parameters file://ssm-params.json --query Command.CommandId --output text)

                STATUS=Pending
                for i in $(seq 1 90); do
                    STATUS=$(aws ssm get-command-invocation --region "$AWS_REGION" --command-id "$COMMAND_ID" \
                               --instance-id "$INSTANCE_ID" --query Status --output text 2>/dev/null || echo Pending)
                    case "$STATUS" in Pending|InProgress|Delayed) sleep 10 ;; *) break ;; esac
                done

                aws ssm get-command-invocation --region "$AWS_REGION" --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" \
                  --query '[StandardOutputContent,StandardErrorContent]' --output text
                echo "SSM command $COMMAND_ID finished: $STATUS"

                # Deployment record (evidence): what was deployed, where, and whether it worked
                export COMMAND_ID INSTANCE_ID STATUS
                python3 -c '
import json, os, datetime
e = os.environ
json.dump({
    "environment":    e["DEPLOY_ENV"],
    "release_id":     e["RELEASE_ID"],
    "git_commit":     e["GIT_COMMIT_FULL"],
    "image_digest":   e["IMAGE_DIGEST"],
    "image_ref":      e["IMAGE_REF"],
    "ssm_command_id": e["COMMAND_ID"],
    "ansible_server": e["INSTANCE_ID"],
    "status":         e["STATUS"],
    "deployed_at":    datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
    "build_url":      e["BUILD_URL"],
}, open("deployment-" + e["DEPLOY_ENV"] + ".json", "w"), indent=2)
'
                cat "deployment-$DEPLOY_ENV.json"

                [ "$STATUS" = "Success" ]
            '''
        } finally {
            archiveArtifacts artifacts: "deployment-${envName}.json", allowEmptyArchive: true, fingerprint: true
        }
    }
}