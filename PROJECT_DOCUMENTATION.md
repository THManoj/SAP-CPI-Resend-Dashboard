# SAP Cloud Integration (CI/CPI) Monitoring & Resend Dashboard

## 📋 System Overview & Architecture

This project is a high-performance, **stateless monitoring and on-demand resend engine** for **SAP Cloud Integration (SAP BTP Integration Suite)**. Built following the **Zero-Persistence Blueprint** (`plan.pdf`), the application operates with strict corporate data residency compliance: **0 bytes persisted to external databases or local disk**, maintaining all payload transformations strictly within volatile in-memory RAM.

---

## 🏗️ Architecture & Component Stack

```
                                  ┌────────────────────────────────────────────────────────┐
                                  │           SAP BTP Cloud Integration (CPI)              │
                                  │  - Message Processing Logs OData API (/api/v1)        │
                                  │  - ErrorInformation (Root Cause Analysis)              │
                                  │  - Runtime iFlow Endpoint (/http/test/resending)      │
                                  └───────────▲────────────────────────────▲───────────────┘
                                              │ OAuth2 Token (XSUAA)       │ Runtime POST
                                              │ OData GET Logs / Errors    │
                                  ┌───────────┴────────────────────────────┴───────────────┐
                                  │        Spring Boot 3.1.4 Backend (Port 8081)           │
                                  │  - SapCiClient (OAuth2, OData Scraper, Resend Engine)  │
                                  │  - MessageController & IngestController               │
                                  │  - Stateless RAM Scope & Auto Garbage Collection       │
                                  └───────────▲────────────────────────────▲───────────────┘
                                              │ REST API (/api/messages)   │
                        ┌─────────────────────┴────────┐     ┌─────────────┴───────────────────────┐
                        │                              │     │                                     │
┌───────────────────────┴────────────────────────┐     │     │  ┌──────────────────────────────────┴─────────────────┐
│     Streamlit 1.48.1 Dashboard (Port 8501)     │     │     │  │     React 18 + Vite 5 Client (Optional SPA)        │
│  - Live KPI Metrics (Total, Failed, Completed) │     │     │  │  - Lightweight TypeScript Web App                  │
│  - Searchable & Filterable Message Log Table   │     │     │  │  - Axios REST Client                               │
│  - Root Cause Error Inspection Box             │     │     │  └────────────────────────────────────────────────────┘
│  - Interactive Resend Execution Pipeline       │     │
│  - Direct SAP Cockpit Jump Links               │     │
└────────────────────────────────────────────────┘     │
                                                       │
                                  ┌────────────────────┴───────────────────────────────────┐
                                  │  Direct Test Receiver: Beeceptor (HTTP 200/500 mock)  │
                                  │  https://resend-testing.free.beeceptor.com             │
                                  └────────────────────────────────────────────────────────┘
```

---

## 🛠️ Complete Technology & Dependency Inventory

### 1. Backend Layer (`backend/`)
- **Runtime**: Java 17 (OpenJDK 17.0.20.1 Temurin)
- **Framework**: Spring Boot 3.1.4 (Spring Web, Spring Security 6.0)
- **Build System**: Apache Maven 3.9.6
- **Key Modules**:
  - `org.springframework.boot:spring-boot-starter-web`: REST endpoints and embedded Tomcat web server.
  - `org.springframework.boot:spring-boot-starter-security`: Configures CORS policy and secure filter chain.
  - `org.springframework.boot:spring-boot-starter-test`: JUnit 5 test harness with live SAP CPI integration tests.
  - `com.fasterxml.jackson.core:jackson-databind`: JSON parsing for SAP OData responses and token payloads.
  - `RestTemplate`: Managed HTTP client with OAuth2 Bearer token injection and connection pooling.

### 2. Frontend Layer (`frontend/`)
- **Runtime**: Python 3.14.5 (isolated in workspace `.venv`)
- **Framework**: Streamlit 1.48.1
- **Key Python Packages**:
  - `requests 2.32.4`: Communicates with backend REST API.
  - `pandas 2.3.3` & `pyarrow 25.0.1`: Fast in-memory message table processing and display.
  - `altair 5.5.0`: Data visualization utilities.
  - `python-dateutil 2.9.0`: ISO-8601 UTC to browser local timezone converter.

### 3. Alternative React SPA (`frontend/src/`)
- **Framework**: React 18.2.0 + TypeScript 5.4.2
- **Bundler / Dev Server**: Vite 5.0.0
- **HTTP Client**: Axios 1.4.0

### 4. Demo Ingest & Tunnel Server (`demo-server/`)
- **Runtime**: Node.js
- **Framework**: Express 4.18.2 + Body-Parser 1.20.2

---

## 🔑 External Integrations & Configuration

| Configuration Key | Purpose | Configured Target / Endpoint |
| :--- | :--- | :--- |
| `sapci.tenantUrl` | SAP BTP Cloud Integration Tenant | `https://b65f2da8trial.it-cpitrial03.cfapps.ap21.hana.ondemand.com` |
| `sapci.tokenUrl` | SAP Cloud Foundry XSUAA OAuth2 Token | `https://b65f2da8trial.authentication.ap21.hana.ondemand.com/oauth/token` |
| `sapci.messageProcessingApi` | CPI Message Processing Logs OData API | `/api/v1/MessageProcessingLogs` |
| `sapci.runtimeUrl` | CPI Runtime Node URL | `https://b65f2da8trial.it-cpitrial03-rt.cfapps.ap21.hana.ondemand.com` |
| `sapci.runtimeClientId` | Runtime OAuth2 Client ID | Integration Runtime instance credentials |
| `sapci.targetReceiverUrl` | Direct Receiver Test Mock | `https://resend-testing.free.beeceptor.com` |

---

## 🚀 How to Run the Project

### Option A: Run Backend (Spring Boot)
```powershell
cd backend
mvn spring-boot:run
# Or run packaged JAR:
java -jar target/sap-ci-monitor-0.0.1-SNAPSHOT.jar
```
*Backend runs on `http://localhost:8081`.*

### Option B: Run Frontend Dashboard (Streamlit)
```powershell
# From project root:
.\.venv\Scripts\streamlit.exe run frontend/streamlit_app.py --server.port 8501
```
*Frontend opens at `http://localhost:8501`.*

---

---

## 📡 API Reference

### `GET /api/messages`
Fetches recent 50 Message Processing Logs directly from SAP CPI.
- **Returns**: Array of `MessageSummary` objects containing `iFlowName`, `messageId`, `status` (`FAILED` / `COMPLETED`), `error`, `timestamp`, `failedStep`, `alternateWebLink`, `correlationId`.

### `GET /api/messages/{messageId}`
Fetches single message detail, scraping error root cause from SAP CPI `/ErrorInformation/$value`.

### `GET /api/messages/{messageId}/attachments`
Discovers all available payload snapshots attached to the message execution in SAP CPI (`/api/v1/MessageProcessingLogs('{id}')/Attachments`).
- **Returns**: Array of `AttachmentInfo` objects (`id`, `name`, `contentType`, `payloadSize`, `timestamp`).

### `GET /api/messages/{messageId}/attachments/{attachmentId}`
Streams raw attachment content on-demand from SAP CPI directly into volatile server RAM. **0 bytes written to disk or database.**

### `POST /api/messages/{messageId}/resend`
Executes on-demand payload reprocessing using the **SAP PO Replay Mechanism**:
- Propagates `SAP_MplCorrelationId` and `SAP_OriginalMessageId` to maintain parent-child execution hierarchy in the SAP BTP Cockpit.
- **Body Options**:
```json
{
  "attachmentId": "6236356632646138747269616c2f6d65737361676573746f7265...",
  "payload": "{\"orderId\":\"ORD-2024\",\"resend\":true}",
  "mode": "IFLOW_ENDPOINT",
  "targetUrl": "https://resend-testing.free.beeceptor.com"
}
```
- **Modes**:
  - `IFLOW_ENDPOINT`: Auto-discovers and re-triggers the specific matching iFlow runtime endpoint (`Testing` $\rightarrow$ `/http/test/resend`), creating a linked execution log under the same Correlation ID in SAP CI.
  - `DIRECT_RECEIVER`: Forwards payload directly to target receiver endpoint (e.g. Beeceptor).

---

## 🔒 Security & Data Privacy Compliance
- **Zero Persistent Storage**: No database credentials or local log file writes for payload data.
- **Dynamic Token Expiry Management**: OAuth2 tokens cached in RAM and renewed before TTL expiration.
- **Immediate De-allocation**: Payload strings in memory fall out of scope immediately after dispatch.
