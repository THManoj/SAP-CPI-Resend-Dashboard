# Frontend (Streamlit)

This frontend is implemented in Streamlit and follows a stateless dashboard pattern:

- Loads message metadata from `GET /api/messages`
- Fetches message detail only on explicit user request
- Triggers resend only on explicit confirmation
- Avoids any file or database persistence in the frontend layer

## Run

```bash
cd frontend
pip install -r requirements.txt
streamlit run streamlit_app.py
```

## Configuration

Set backend URL with environment variable or in the sidebar:

- `BACKEND_BASE_URL` (default: `http://localhost:8081`)

Example:

```bash
set BACKEND_BASE_URL=http://localhost:8081
streamlit run streamlit_app.py
```
