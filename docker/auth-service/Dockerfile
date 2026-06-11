FROM eclipse-temurin:21-jre

WORKDIR /opt/geyser-edu-auth-service

COPY auth-service/build/libs/geyser-edu-auth-service-*-standalone.jar ./geyser-edu-auth-service.jar

RUN mkdir -p /var/lib/geyser-edu-auth /var/log/geyser-edu-auth

ENV GEYSER_EDU_ENV=production
ENV GEYSER_EDU_AUTH_PORT=8080
ENV GEYSER_EDU_TICKET_STORE=/var/lib/geyser-edu-auth/participation-tickets.tsv
ENV GEYSER_EDU_AUDIT_LOG=/var/log/geyser-edu-auth/audit.tsv

EXPOSE 8080

CMD ["java", "-jar", "./geyser-edu-auth-service.jar"]
