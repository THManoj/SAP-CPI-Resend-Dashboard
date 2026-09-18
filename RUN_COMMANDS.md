# 🚀 SAP CI Message Monitor — Commands to Run

This document provides exact, copy-pasteable commands to build, configure, run, and test both the **Spring Boot Backend** and **Streamlit Frontend** on your local machine or server.

---

## 📋 System Prerequisites

| Component | Minimum Version | Check Command |
| :--- | :--- | :--- |
| **Java JDK** | OpenJDK 17 or Oracle JDK 17+ | `java -version` |
| **Apache Maven** | 3.8.x or higher | `mvn -v` |
| **Python** | Python 3.10 or higher | `python --version` |
| **pip** | Latest | `python -m pip --version` |

---

## ⚡ Quick Start (2-Step Run)

### Step 1: Start the Spring Boot Backend (Port 8081)

Open a terminal in the project root:

```bash
# Navigate to backend directory
cd backend

# Build and package the executable JAR (skipping unit tests for fast launch)
mvn clean package -DskipTests

# Run the packaged Spring Boot JAR
java -jar target/sap-ci-monitor-0.0.1-SNAPSHOT.jar
```

*Alternatively, run directly with Maven:*
```bash
mvn spring-boot:run
```
> The backend server will initialize on: **`http://localhost:8081`**

---

### Step 2: Start the Streamlit Frontend (Port 8501)

Open a second terminal in the project root:

```bash
# 1. Activate the Python virtual environment
# On Windows (PowerShell):
.\.venv\Scripts\Activate.ps1
# Or Windows (Command Prompt):
.\.venv\Scripts\activate.bat
# Or macOS / Linux:
source .venv/bin/activate

# 2. Install required Python packages (only required once)
pip install -r frontend/requirements.txt

# 3. Launch the Streamlit SAP Belize Monitoring Dashboard
streamlit run frontend/streamlit_app.py --server.port 8501
```
> The dashboard UI will automatically open in your browser at: **`http://localhost:8501`**

---

## 🔑 Demo / Preview Login Credentials

When prompted on the SAP Process Orchestration logon screen:
- **User:** `thmanoj272@gmail.com`
- **Password:** `admin`

---

## ⚙️ Running with Custom Environment Variables

You can override default settings without modifying code by supplying environment variables:

### Backend Options (Spring Boot)

```bash
# Run on custom port (e.g. 9090)
java -Dserver.port=9090 -jar target/sap-ci-monitor-0.0.1-SNAPSHOT.jar

# Run with custom SAP BTP credentials passed as environment variables
set SAP_CI_CLIENT_ID=sb-your-client-id
set SAP_CI_CLIENT_SECRET=your-client-secret
set SAP_CI_TOKEN_URL=https://your-tenant.authentication.eu10.hana.ondemand.com/oauth/token
set SAP_CI_TENANT_URL=https://your-tenant.it-cpi001.cfapps.eu10.hana.ondemand.com
java -jar target/sap-ci-monitor-0.0.1-SNAPSHOT.jar
```

### Frontend Options (Streamlit)

```bash
# Specify custom backend URL
set BACKEND_BASE_URL=http://localhost:8081

# Run on custom port (e.g. 8080) and bind to all network interfaces
streamlit run frontend/streamlit_app.py --server.port 8080 --server.address 0.0.0.0
```

---

## 🧪 API Verification & Health Check Commands

You can test backend REST endpoints directly using `curl` or PowerShell:

### 1. Health & Message Overview
```bash
# Fetch aggregated list of recent CPI messages and dynamic packages
curl -X GET http://localhost:8081/api/messages
```

### 2. Message Detail Inspection
```bash
# Replace <MESSAGE_ID> with a real CPI Message ID (e.g. AGqiq3-moewVOSgV4ySZVhBYu8qS)
curl -X GET http://localhost:8081/api/messages/<MESSAGE_ID>
```

### 3. Check Attachments
```bash
# Fetch attachments list for a message
curl -X GET http://localhost:8081/api/messages/<MESSAGE_ID>/attachments
```

### 4. Fetch Attachment Raw Content (Zero-disk RAM Stream)
```bash
# Fetch raw attachment bytes with authenticated user header
curl -X GET http://localhost:8081/api/messages/<MESSAGE_ID>/attachments/<ATTACHMENT_ID> \
     -H "X-BTP-Username: thmanoj272@gmail.com" \
     -H "X-BTP-Password: your_password"
```

### 5. Trigger Selective Resend / Replay
```bash
# Replay failed message directly back to its original iFlow endpoint
curl -X POST http://localhost:8081/api/messages/<MESSAGE_ID>/resend \
     -H "Content-Type: application/json" \
     -d '{"mode": "IFLOW_ENDPOINT", "payload": "<order><id>123</id></order>"}'
```

---

## 🛠️ Port Conflict Troubleshooting (Windows)

If port `8081` (backend) or `8501` (frontend) is already occupied:

```powershell
# 1. Find the Process ID (PID) holding port 8081
netstat -ano | findstr :8081

# 2. Terminate the process by PID
taskkill /PID <PID_NUMBER> /F

# 3. Repeat for port 8501 if needed
netstat -ano | findstr :8501
taskkill /PID <PID_NUMBER> /F
```

---

## 💡 Quick Launch Scripts

For instant execution, use the provided batch scripts in the project root:
- **`run_backend.bat`** — Automatically builds and starts the backend service.
- **`run_frontend.bat`** — Automatically activates the Python environment and starts the Streamlit UI.
