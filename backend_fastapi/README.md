# ZENO AI — Python FastAPI Backend

This directory contains the standalone Python FastAPI REST API backend for **ZENO AI — Your Personal AI Accountant**.

## Features

- **SQLAlchemy Database Integration**: Configurable via `DATABASE_URL` (PostgreSQL recommended in production; defaults to `sqlite:///./zeno_accounting.db` for standalone local runs) with safe non-destructive schema migrations (`run_safe_migrations`).
- **Authentication & Email Verification**: PBKDF2-SHA256 passwords, 6-digit SHA-256 salted OTPs + cryptographically secure time-limited verification links, 60-second resend cooldown, and HMAC-SHA256 bearer session tokens (`AUTH_SECRET_KEY`).
- **SMTP Email Dispatch**: Configured strictly via environment variables (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM_EMAIL`, `SMTP_USE_TLS`).
- **Double-Entry Accounting & Indian GST Engine**: Balanced debit/credit journal engine, CGST/SGST/IGST reconciliation, and ZENO AI natural-language chat endpoint (`/api/ai/zeno/chat`).

## Local Setup & Run

```bash
cd backend_fastapi
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Fill SMTP_*, AUTH_SECRET_KEY, DATABASE_URL, BACKEND_PUBLIC_URL in .env
uvicorn main:app --host 0.0.0.0 --port 8000
```

## Production Deployment

1. Deploy `backend_fastapi/` to a managed HTTPS service (e.g., Google Cloud Run, Render, or Railway).
2. Set the required environment variables on the host (`DATABASE_URL`, `AUTH_SECRET_KEY`, `BACKEND_PUBLIC_URL`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM_EMAIL`, `SMTP_USE_TLS`, `CORS_ALLOWED_ORIGINS`).
3. Verify the deployment via `GET https://<your-backend-domain>/api/health`.
4. Configure the Android build's centralized API base URL (`ZENO_API_BASE_URL=https://<your-backend-domain>/`).
