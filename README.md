# 🏢 SAP Cloud Integration (CPI) Message Monitor

A stateless, real-time message monitoring and selective replay dashboard for **SAP Cloud Integration (SAP BTP)** designed with classic **SAP Process Orchestration (PO / PI Message Monitor - PIMON)** Belize aesthetics.

---

## 📚 Essential Documentation

| Document | Purpose |
| :--- | :--- |
| [🚀 RUN_COMMANDS.md](file:///c:/Users/Manoj/Desktop/Dashboard-SAP%20CI/RUN_COMMANDS.md) | Copy-pasteable commands to build, run, configure, and test backend and frontend. |
| [🏛️ IMPLEMENTATION_PLAN.md](file:///c:/Users/Manoj/Desktop/Dashboard-SAP%20CI/IMPLEMENTATION_PLAN.md) | Complete end-to-end implementation architecture, data flows, and design specifications. |
| [🏢 COMPANY_DEPLOYMENT_GUIDE.md](file:///c:/Users/Manoj/Desktop/Dashboard-SAP%20CI/COMPANY_DEPLOYMENT_GUIDE.md) | Guide for corporate enterprise production rollout (BTP keys, roles, Cloud Foundry/K8s). |
| [🔐 ENTERPRISE_AUTHENTICATION_PLAN.md](file:///c:/Users/Manoj/Desktop/Dashboard-SAP%20CI/ENTERPRISE_AUTHENTICATION_PLAN.md) | Enterprise SSO, Corporate IdP (Azure AD / SAP IAS), OIDC, JWT, and RBAC matrix. |
| [📖 PROJECT_DOCUMENTATION.md](file:///c:/Users/Manoj/Desktop/Dashboard-SAP%20CI/PROJECT_DOCUMENTATION.md) | Detailed technical and component reference. |

---

## ⚡ Quick Start (Local Run)

### 1. Backend (Spring Boot 3.1.4, Java 17)
```bash
cd backend
mvn clean package -DskipTests
java -jar target/sap-ci-monitor-0.0.1-SNAPSHOT.jar
```
*Initializes on `http://localhost:8081`.*

### 2. Frontend Dashboard (Streamlit, Python 3.10+)
```bash
# Activate virtual environment
.\.venv\Scripts\activate
# Run Streamlit app
streamlit run frontend/streamlit_app.py --server.port 8501
```
*Opens in your browser on `http://localhost:8501`.*

*(Windows users can also simply double-click `run_backend.bat` and `run_frontend.bat`)*.

---

## 📁 Clean Repository Structure

```
├── .gitignore                         # Git exclusion rules
├── backend/                           # Spring Boot REST API & SAP CPI Connector
│   ├── src/main/java/                 # Controllers, Services, Models, Security
│   ├── src/main/resources/            # application.yml (SAP BTP tenant config)
│   └── pom.xml                        # Maven configuration
│
├── frontend/                          # Streamlit SAP Belize Monitoring Dashboard
│   ├── streamlit_app.py               # Main UI Application
│   ├── requirements.txt               # Python dependencies
│   └── README.md                      # Frontend-specific notes
│
├── RUN_COMMANDS.md                    # Exact commands to run and test
├── IMPLEMENTATION_PLAN.md             # Complete technical architecture & plan
├── COMPANY_DEPLOYMENT_GUIDE.md        # Corporate enterprise deployment guide
├── ENTERPRISE_AUTHENTICATION_PLAN.md  # Corporate SSO, IdP, and RBAC plan
├── PROJECT_DOCUMENTATION.md           # Deep component documentation
├── run_backend.bat                    # 1-click Windows backend launcher
├── run_frontend.bat                   # 1-click Windows frontend launcher
└── plan.pdf                           # Original architecture blueprint
```

---

## 🔑 Key Features
- **SAP PO Belize Look-and-Feel:** Designed to match the classic SAP NetWeaver PIMON monitor.
- **Dynamic Package Mapping:** Integration flow packages are queried dynamically via SAP CPI APIs (`/api/v1/IntegrationPackages`).
- **Interactive Metric Badges:** Clicking counts (`🔴 Failed`, `🟢 Successful`, `🔄 Resent`) directly filters the message log table.
- **Selective Row Inspection:** Message root cause error, technical attributes, and payload display downside *strictly when a Message ID or row is selected*.
- **On-Demand Silent Payload Viewing:** Payloads are loaded silently in memory without downloading progress banners.
- **Payload Availability Guard:** Resend is strictly blocked if no payload attachment exists in the message log, preventing corrupt/empty executions.
- **Dedicated Resent Queue:** Resent messages automatically transition to a separate `Resent` column (`🔄 X`), keeping pending failures clear.
- **Zero Disk Persistence:** 100% compliant with enterprise privacy and `plan.pdf` constraints (volatile RAM streaming only).
