# 🏢 Enterprise Migration & Company Deployment Guide

This guide outlines all the configuration, security, architectural, and infrastructure changes required to transition this dashboard from a local / trial sandbox into an **enterprise-grade corporate production environment**.

---

## 📑 Table of Contents

1. [SAP BTP Tenant & Service Key Configuration](#1-sap-btp-tenant--service-key-configuration)
2. [SAP CPI Role Collections & Security Authorizations](#2-sap-cpi-role-collections--security-authorizations)
3. [Production Deployment Architecture Options](#3-production-deployment-architecture-options)
   - [Option A: SAP BTP Cloud Foundry (Recommended)](#option-a-sap-btp-cloud-foundry)
   - [Option B: SAP BTP Kyma / Enterprise Kubernetes](#option-b-sap-btp-kyma--enterprise-kubernetes)
   - [Option C: On-Premise / Corporate VM Deployment](#option-c-on-premise--corporate-vm-deployment)
4. [Enterprise Network, Ingress & TLS Certificates](#4-enterprise-network-ingress--tls-certificates)
5. [Audit Logging & Regulatory Compliance](#5-audit-logging--regulatory-compliance)
6. [Resent Message State Persistence (High Availability)](#6-resent-message-state-persistence-high-availability)
7. [Production Checklist Summary](#7-production-checklist-summary)

---

## 1. SAP BTP Tenant & Service Key Configuration

In a corporate landscape, you will migrate from trial URLs (`*.cfapps.ap21.hana.ondemand.com`) to your enterprise SAP BTP subaccount (e.g., `eu10`, `us10`, `ap10`).

### Step 1.1: Create Enterprise Service Keys in SAP BTP Cockpit

1. Log in to your corporate **SAP BTP Cockpit** &rarr; Navigate to your **Subaccount** &rarr; **Spaces**.
2. Under **Services** &rarr; **Instances**, create two dedicated service instances:
   - **Instance 1 (API Access):**
     - Service: `Process Integration Runtime`
     - Plan: `api`
     - Grant: `client_credentials`
     - Roles: Assign `MonitoringDataRead`, `MonitoringPayloadsRead`, `WorkspacePackagesEdit`.
     - Generate a **Service Key** (e.g. `cpi-monitor-api-key`).
   - **Instance 2 (Message Replay / Ingress Execution):**
     - Service: `Process Integration Runtime`
     - Plan: `integration-flow`
     - Roles: Assign `ESBMessaging.send`.
     - Generate a **Service Key** (e.g. `cpi-replay-runtime-key`).

### Step 1.2: Configure Environment Variables

Do **NOT** commit corporate secrets to source control. In production, configure environment variables:

```bash
# SAP BTP API Service Instance (Monitoring & Payload Read)
SAP_CI_TENANT_URL=https://<your-tenant>.it-cpi001.cfapps.eu10.hana.ondemand.com
SAP_CI_CLIENT_ID=sb-cpi-monitor-api!b12345|it-rt-cpi001!b678
SAP_CI_CLIENT_SECRET=e7b4a2c1-xxxx-xxxx-xxxx-xxxxxxxxxxxx
SAP_CI_TOKEN_URL=https://<subdomain>.authentication.eu10.hana.ondemand.com/oauth/token

# SAP BTP Integration Flow Runtime (Message Replay Execution)
SAP_CI_RUNTIME_CLIENT_ID=sb-cpi-replay-rt!b54321|it-rt-cpi001!b678
SAP_CI_RUNTIME_CLIENT_SECRET=a1b2c3d4-yyyy-yyyy-yyyy-yyyyyyyyyyyy
SAP_CI_RUNTIME_TOKEN_URL=https://<subdomain>.authentication.eu10.hana.ondemand.com/oauth/token
```

---

## 2. SAP CPI Role Collections & Security Authorizations

### 2.1 Enabling Payload Attachment Reading (`$value`)

In SAP BTP Trial, reading raw attachment payloads (`GET /MessageProcessingLogAttachments('{id}')/$value`) often triggers `403 Forbidden` because trial service keys cannot be assigned privileged payload roles.

In an enterprise company tenant, this must be configured:
1. In **SAP BTP Cockpit** &rarr; **Security** &rarr; **Role Collections**:
   - Assign the role **`MonitoringPayloads.Read`** (or **`AuthGroup_Administrator`**) to your service key instance or corporate user groups.
   - Reference: **SAP Note 2824338** (*Authorization requirements for reading payloads in SAP Cloud Integration*).
2. Once this role is assigned to the service key, the dashboard will immediately stream genuine payload snapshots without requiring user password fallback.

### 2.2 Enabling Message Resend / Replay Execution

To allow the monitor to replay failed messages back into integration flows:
- The runtime client ID must have the scope **`ESBMessaging.send`**.
- Ensure the target integration flow sender adapter is configured for **User Role: `ESBMessaging.send`**.

---

## 3. Production Deployment Architecture Options

### Option A: SAP BTP Cloud Foundry

Deploy both Spring Boot backend and Streamlit frontend directly on SAP BTP Cloud Foundry using `manifest.yml`:

```yaml
---
applications:
  # 1. Spring Boot Backend Service
  - name: sap-ci-monitor-api
    path: backend/target/sap-ci-monitor-0.0.1-SNAPSHOT.jar
    memory: 1024M
    instances: 2
    buildpacks:
      - java_buildpack
    env:
      SPRING_PROFILES_ACTIVE: production
      SAP_CI_TENANT_URL: ((cpi_tenant_url))
      SAP_CI_CLIENT_ID: ((cpi_client_id))
      SAP_CI_CLIENT_SECRET: ((cpi_client_secret))
      SAP_CI_TOKEN_URL: ((cpi_token_url))

  # 2. Streamlit Belize Frontend UI
  - name: sap-ci-monitor-ui
    path: frontend/
    memory: 512M
    instances: 2
    buildpacks:
      - python_buildpack
    command: streamlit run streamlit_app.py --server.port $PORT --server.address 0.0.0.0
    env:
      BACKEND_BASE_URL: https://sap-ci-monitor-api.cfapps.eu10.hana.ondemand.com
```

Deploy using CF CLI:
```bash
cf push
```

---

### Option B: SAP BTP Kyma / Enterprise Kubernetes

Deploy using containerized Docker images in corporate Kubernetes / Kyma:

#### Backend `Dockerfile`:
```dockerfile
FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp
COPY backend/target/sap-ci-monitor-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-Xms512m", "-Xmx1024m", "-jar", "/app.jar"]
```

#### Frontend `Dockerfile`:
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY frontend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY frontend/ .
EXPOSE 8501
ENTRYPOINT ["streamlit", "run", "streamlit_app.py", "--server.port=8501", "--server.address=0.0.0.0"]
```

Store credentials in **Kubernetes Secrets** and inject them into pods via `SecretKeyRef`.

---

### Option C: On-Premise / Corporate VM Deployment

If hosted on an internal corporate virtual machine (RHEL, Ubuntu, Windows Server):
- Place an enterprise reverse proxy (**NGINX**, **Apache HTTPD**, or **SAP Web Dispatcher**) in front of ports 8081 and 8501.
- Configure outbound proxy routing if your company uses a corporate forward proxy (e.g. Zscaler / BlueCoat):
  ```bash
  # In Spring Boot application.yml:
  # -Dhttp.proxyHost=proxy.corp.company.com -Dhttp.proxyPort=8080
  ```

---

## 4. Enterprise Network, Ingress & TLS Certificates

1. **Custom Domain & SSL/TLS Termination:**
   - Map a company DNS record (e.g. `https://cpi-monitor.corp.company.com`).
   - Terminate HTTPS with an enterprise CA certificate (DigiCert, Let's Encrypt, or internal corporate PKI).
2. **CORS & Cross-Origin Policies:**
   - In `backend/src/main/java/com/example/sapcimonitor/controller/MessageController.java`:
   - Replace `@CrossOrigin(origins = "*")` with your specific corporate frontend domain:
     ```java
     @CrossOrigin(origins = "https://cpi-monitor.corp.company.com", allowCredentials = "true")
     ```
3. **Firewall Whitelisting:**
   - Whitelist outbound HTTPS traffic (Port 443) from the backend host to:
     - `*.authentication.<region>.hana.ondemand.com`
     - `*.it-cpi001.cfapps.<region>.hana.ondemand.com`

---

## 5. Audit Logging & Regulatory Compliance

In a corporate environment (SOX, GDPR, ISO 27001), replaying integration messages requires strict non-repudiation:

1. **Log Every Replay Action:**
   - Record in an enterprise SIEM (Splunk, Elastic, or Dynatrace):
     - Corporate User ID (e.g. `john.doe@company.com`)
     - Timestamp (UTC)
     - Original CPI Message ID
     - Target Integration Flow
     - Parent Correlation ID
     - HTTP Status Code & Outcome
2. **Zero-Disk Retention Certification:**
   - Re-certify that raw payload bytes are never written to disk or temp storage.
   - Maintain ephemeral in-memory payload inspection only.

---

## 6. Resent Message State Persistence (High Availability)

In the trial setup, resent status is tracked in-memory (`ConcurrentHashMap`). If your company runs **multiple backend instances** behind a load balancer for High Availability:

- **Redis / Distributed In-Memory Cache:**
  - Connect Spring Boot to a managed Redis instance (e.g. AWS ElastiCache, Azure Cache for Redis, or SAP BTP Redis).
  - Store resent flags (`cpi:resent:<messageId> -> timestamp + operatorId`) with a TTL (e.g. 7 days).
  - This ensures that if Operator A replays a message on Instance 1, Operator B inspecting on Instance 2 immediately sees the message under the `🔄 Resent` column.

---

## 7. Production Checklist Summary

| Step | Area | Action |
| :--- | :--- | :--- |
| **1** | BTP Tenant | Provision production `api` & `integration-flow` service keys in BTP subaccount. |
| **2** | Roles | Grant `MonitoringPayloads.Read` and `ESBMessaging.send` permissions. |
| **3** | Secrets | Set environment variables (`SAP_CI_*`); do not commit keys to Git. |
| **4** | Security | Replace `@CrossOrigin(origins = "*")` with corporate domain. |
| **5** | Network | Configure HTTPS TLS termination and outbound proxy if required. |
| **6** | Audit | Connect resend events to enterprise SIEM (Splunk / Dynatrace). |
| **7** | High Availability | (Optional) Back `resentMessageComments` with Redis for multi-pod clustering. |
