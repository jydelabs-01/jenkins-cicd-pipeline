# Jenkins LTS (JDK 17) with Maven, pre-installed plugins and Configuration-as-Code.
# Multi-arch base image: works on Apple Silicon (arm64) and x86_64.
FROM jenkins/jenkins:lts-jdk17

USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends maven curl \
 && rm -rf /var/lib/apt/lists/*
USER jenkins

# Skip the interactive setup wizard - admin user, tools, credentials and the job come from casc.yaml
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false" \
    CASC_JENKINS_CONFIG=/var/jenkins_conf/casc.yaml

COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY --chown=jenkins:jenkins casc.yaml /var/jenkins_conf/casc.yaml
