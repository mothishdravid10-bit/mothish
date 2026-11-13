import base64
from dataclasses import dataclass
from datetime import datetime, timedelta
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
from typing import Any, Dict, Optional, Tuple

from email_service import (
    EmailDispatchResult,
    is_smtp_configured,
    send_otp_email,
    send_password_reset_email,
)

EMAIL_REGEX = re.compile(
    r"^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,7}$"
)
OTP_EXPIRY_SECONDS = 600
OTP_COOLDOWN_SECONDS = 60
OTP_MAX_ATTEMPTS = 5
SESSION_TOKEN_TTL_SECONDS = 7 * 24 * 3600

# Process-local fallback secret when AUTH_SECRET_KEY env var is absent (never a static hardcoded key)
_PROCESS_FALLBACK_SECRET = secrets.token_hex(32)


@dataclass
class AuthServiceError(Exception):
    status_code: int
    detail: str


def _get_auth_secret() -> bytes:
    env_secret = os.getenv("AUTH_SECRET_KEY", "").strip()
    secret = env_secret if env_secret else _PROCESS_FALLBACK_SECRET
    return secret.encode("utf-8")


def hash_otp(otp: str, salt: str) -> str:
    return hashlib.sha256(f"{salt}:{otp}".encode("utf-8")).hexdigest()


def hash_token(raw_token: str) -> str:
    return hashlib.sha256(raw_token.encode("utf-8")).hexdigest()


def hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 120_000)
    return f"pbkdf2_sha256${salt}${dk.hex()}"


def verify_password(password: str, stored_hash: str) -> bool:
    if not stored_hash or not stored_hash.startswith("pbkdf2_sha256$"):
        return False
    parts = stored_hash.split("$", 2)
    if len(parts) != 3:
        return False
    _, salt, expected_hex = parts
    dk = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 120_000)
    return hmac.compare_digest(dk.hex(), expected_hex)


def _b64url_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _b64url_decode(encoded: str) -> bytes:
    padding = "=" * (-len(encoded) % 4)
    return base64.urlsafe_b64decode(encoded + padding)


def create_access_token(user_id: int, email: str, now_ts: Optional[int] = None) -> str:
    issued_at = int(now_ts if now_ts is not None else datetime.utcnow().timestamp())
    header = _b64url_encode(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode("utf-8"))
    payload = _b64url_encode(
        json.dumps(
            {
                "sub": str(user_id),
                "email": email,
                "iat": issued_at,
                "exp": issued_at + SESSION_TOKEN_TTL_SECONDS,
            },
            separators=(",", ":"),
        ).encode("utf-8")
    )
    signing_input = f"{header}.{payload}".encode("ascii")
    sig = hmac.new(_get_auth_secret(), signing_input, hashlib.sha256).digest()
    return f"{header}.{payload}.{_b64url_encode(sig)}"


def verify_access_token(token: str, now_ts: Optional[int] = None) -> Dict[str, Any]:
    parts = (token or "").strip().split(".")
    if len(parts) != 3:
        raise AuthServiceError(401, "Invalid authentication token.")
    header_b64, payload_b64, sig_b64 = parts
    signing_input = f"{header_b64}.{payload_b64}".encode("ascii")
    expected_sig = _b64url_encode(hmac.new(_get_auth_secret(), signing_input, hashlib.sha256).digest())
    if not hmac.compare_digest(sig_b64, expected_sig):
        raise AuthServiceError(401, "Invalid authentication signature.")
    try:
        payload = json.loads(_b64url_decode(payload_b64).decode("utf-8"))
    except Exception:
        raise AuthServiceError(401, "Malformed authentication token.")
    current_ts = int(now_ts if now_ts is not None else datetime.utcnow().timestamp())
    if int(payload.get("exp", 0)) < current_ts:
        raise AuthServiceError(401, "Your session has expired. Please log in again.")
    return payload


def _is_sqlite_conn(db: Any) -> bool:
    return isinstance(db, sqlite3.Connection)


def _parse_dt(val: Any) -> Optional[datetime]:
    if val is None:
        return None
    if isinstance(val, datetime):
        return val
    try:
        return datetime.fromisoformat(str(val))
    except ValueError:
        return None


def _build_verification_link(raw_token: str) -> Optional[str]:
    public_url = os.getenv("BACKEND_PUBLIC_URL", "").strip().rstrip("/")
    if not public_url:
        return None
    return f"{public_url}/api/auth/verify-email?token={raw_token}"


def run_safe_sqlite_migrations(conn: sqlite3.Connection) -> None:
    """Safely creates/migrates SQLite auth & accounting tables without deleting existing records."""
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            company_name TEXT DEFAULT 'My Enterprise',
            gstin TEXT DEFAULT '',
            state_code TEXT DEFAULT '27',
            state_name TEXT DEFAULT 'Maharashtra',
            is_verified INTEGER NOT NULL DEFAULT 0,
            created_at TEXT
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS email_otps (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            email TEXT NOT NULL,
            otp_hash TEXT NOT NULL,
            otp_salt TEXT NOT NULL,
            token_hash TEXT,
            purpose TEXT DEFAULT 'REGISTRATION',
            created_at TEXT,
            expires_at TEXT NOT NULL,
            last_sent_at TEXT,
            attempts_left INTEGER DEFAULT 5,
            is_used INTEGER DEFAULT 0,
            is_verified INTEGER DEFAULT 0
        )
        """
    )
    existing_user_cols = {row[1] for row in conn.execute("PRAGMA table_info(users)").fetchall()}
    user_additions = {
        "company_name": "TEXT DEFAULT 'My Enterprise'",
        "gstin": "TEXT DEFAULT ''",
        "state_code": "TEXT DEFAULT '27'",
        "state_name": "TEXT DEFAULT 'Maharashtra'",
        "is_verified": "INTEGER NOT NULL DEFAULT 0",
        "created_at": "TEXT",
    }
    for col, ddl in user_additions.items():
        if col not in existing_user_cols:
            conn.execute(f"ALTER TABLE users ADD COLUMN {col} {ddl}")

    existing_otp_cols = {row[1] for row in conn.execute("PRAGMA table_info(email_otps)").fetchall()}
    otp_additions = {
        "token_hash": "TEXT",
        "purpose": "TEXT DEFAULT 'REGISTRATION'",
        "last_sent_at": "TEXT",
        "attempts_left": "INTEGER DEFAULT 5",
        "is_used": "INTEGER DEFAULT 0",
        "is_verified": "INTEGER DEFAULT 0",
    }
    for col, ddl in otp_additions.items():
        if col not in existing_otp_cols:
            conn.execute(f"ALTER TABLE email_otps ADD COLUMN {col} {ddl}")
    conn.commit()


def send_verification_otp(
    email: str,
    db: Any,
    now: Optional[datetime] = None,
    dispatcher=send_otp_email,
) -> Dict[str, Any]:
    clean_email = (email or "").strip().lower()
    if not clean_email or not EMAIL_REGEX.match(clean_email):
        raise AuthServiceError(400, "Please enter a valid email address (e.g. name@domain.com).")

    current_time = now or datetime.utcnow()

    if _is_sqlite_conn(db):
        existing = db.execute(
            "SELECT id, is_verified FROM users WHERE lower(email) = ?", (clean_email,)
        ).fetchone()
        if existing and bool(existing[1]):
            raise AuthServiceError(409, f"An account with {clean_email} already exists. Please log in instead.")

        latest = db.execute(
            "SELECT id, last_sent_at FROM email_otps WHERE lower(email) = ? ORDER BY id DESC LIMIT 1",
            (clean_email,),
        ).fetchone()
        if latest and latest[1]:
            last_sent = _parse_dt(latest[1])
            if last_sent:
                elapsed = (current_time - last_sent).total_seconds()
                if elapsed < OTP_COOLDOWN_SECONDS:
                    wait_sec = max(1, int(OTP_COOLDOWN_SECONDS - elapsed))
                    raise AuthServiceError(429, f"Please wait {wait_sec} seconds before requesting another OTP.")

        otp_code = f"{secrets.randbelow(900_000) + 100_000:06d}"
        salt = secrets.token_hex(16)
        raw_token = secrets.token_urlsafe(32)
        verification_link = _build_verification_link(raw_token)

        dispatch: EmailDispatchResult = dispatcher(clean_email, otp_code, verification_link=verification_link)
        if not dispatch.success:
            status_code = 503 if not dispatch.configured else 502
            raise AuthServiceError(status_code, dispatch.error or "Failed to send verification email.")

        db.execute(
            "UPDATE email_otps SET is_used = 1, is_verified = 0 WHERE lower(email) = ?",
            (clean_email,),
        )
        db.execute(
            """
            INSERT INTO email_otps
                (email, otp_hash, otp_salt, token_hash, purpose, created_at, expires_at, last_sent_at, attempts_left, is_used, is_verified)
            VALUES (?, ?, ?, ?, 'REGISTRATION', ?, ?, ?, ?, 0, 0)
            """,
            (
                clean_email,
                hash_otp(otp_code, salt),
                salt,
                hash_token(raw_token),
                current_time.isoformat(),
                (current_time + timedelta(seconds=OTP_EXPIRY_SECONDS)).isoformat(),
                current_time.isoformat(),
                OTP_MAX_ATTEMPTS,
            ),
        )
        db.commit()
    else:
        import models

        existing_user = db.query(models.User).filter(models.User.email == clean_email).first()
        if existing_user and existing_user.is_verified:
            raise AuthServiceError(409, f"An account with {clean_email} already exists. Please log in instead.")

        latest_otp = (
            db.query(models.EmailOtp)
            .filter(models.EmailOtp.email == clean_email)
            .order_by(models.EmailOtp.id.desc())
            .first()
        )
        if latest_otp and latest_otp.last_sent_at:
            elapsed = (current_time - latest_otp.last_sent_at).total_seconds()
            if elapsed < OTP_COOLDOWN_SECONDS:
                wait_sec = max(1, int(OTP_COOLDOWN_SECONDS - elapsed))
                raise AuthServiceError(429, f"Please wait {wait_sec} seconds before requesting another OTP.")

        db.query(models.EmailOtp).filter(models.EmailOtp.email == clean_email).update(
            {"is_used": True, "is_verified": False}
        )

        otp_code = f"{secrets.randbelow(900_000) + 100_000:06d}"
        salt = secrets.token_hex(16)
        raw_token = secrets.token_urlsafe(32)
        verification_link = _build_verification_link(raw_token)

        record = models.EmailOtp(
            email=clean_email,
            otp_hash=hash_otp(otp_code, salt),
            otp_salt=salt,
            token_hash=hash_token(raw_token),
            purpose="REGISTRATION",
            created_at=current_time,
            expires_at=current_time + timedelta(seconds=OTP_EXPIRY_SECONDS),
            last_sent_at=current_time,
            attempts_left=OTP_MAX_ATTEMPTS,
            is_used=False,
            is_verified=False,
        )
        db.add(record)
        db.flush()

        dispatch = dispatcher(clean_email, otp_code, verification_link=verification_link)
        if not dispatch.success:
            db.rollback()
            status_code = 503 if not dispatch.configured else 502
            raise AuthServiceError(status_code, dispatch.error or "Failed to send verification email.")
        db.commit()

    return {
        "status": "otp_sent",
        "email": clean_email,
        "message": f"6-digit verification code sent to {clean_email}. Valid for 10 minutes.",
        "expires_in_seconds": OTP_EXPIRY_SECONDS,
        "cooldown_seconds": OTP_COOLDOWN_SECONDS,
    }


def verify_email_otp(email: str, otp: str, db: Any, now: Optional[datetime] = None) -> Dict[str, Any]:
    clean_email = (email or "").strip().lower()
    clean_otp = (otp or "").strip()

    if not clean_email or not EMAIL_REGEX.match(clean_email):
        raise AuthServiceError(400, "Invalid email address.")
    if len(clean_otp) != 6 or not clean_otp.isdigit():
        raise AuthServiceError(400, "Please enter a valid 6-digit numeric OTP.")

    current_time = now or datetime.utcnow()

    if _is_sqlite_conn(db):
        row = db.execute(
            """
            SELECT id, otp_hash, otp_salt, expires_at, attempts_left
            FROM email_otps
            WHERE lower(email) = ? AND is_used = 0
            ORDER BY id DESC LIMIT 1
            """,
            (clean_email,),
        ).fetchone()
        if not row:
            raise AuthServiceError(400, f"No active verification code found for {clean_email}. Please request a new OTP.")

        rec_id, rec_hash, rec_salt, exp_str, attempts_left = row
        exp_dt = _parse_dt(exp_str)
        if exp_dt and current_time > exp_dt:
            db.execute("UPDATE email_otps SET is_used = 1 WHERE id = ?", (rec_id,))
            db.commit()
            raise AuthServiceError(400, "Verification code has expired. Please request a new OTP.")

        if int(attempts_left or 0) <= 0:
            db.execute("UPDATE email_otps SET is_used = 1 WHERE id = ?", (rec_id,))
            db.commit()
            raise AuthServiceError(429, "Maximum verification attempts exceeded. Please request a new OTP.")

        computed = hash_otp(clean_otp, rec_salt)
        if not hmac.compare_digest(computed, rec_hash):
            new_attempts = int(attempts_left) - 1
            if new_attempts <= 0:
                db.execute("UPDATE email_otps SET attempts_left = 0, is_used = 1 WHERE id = ?", (rec_id,))
                db.commit()
                raise AuthServiceError(429, "Incorrect OTP. Maximum verification attempts exceeded. Please request a new OTP.")
            db.execute("UPDATE email_otps SET attempts_left = ? WHERE id = ?", (new_attempts, rec_id))
            db.commit()
            raise AuthServiceError(400, f"Invalid OTP code. {new_attempts} attempts remaining.")

        db.execute("UPDATE email_otps SET is_verified = 1, is_used = 1 WHERE id = ?", (rec_id,))
        db.execute("UPDATE users SET is_verified = 1 WHERE lower(email) = ?", (clean_email,))
        db.commit()
    else:
        import models

        record = (
            db.query(models.EmailOtp)
            .filter(models.EmailOtp.email == clean_email, models.EmailOtp.is_used == False)
            .order_by(models.EmailOtp.id.desc())
            .first()
        )
        if not record:
            raise AuthServiceError(400, f"No active verification code found for {clean_email}. Please request a new OTP.")
        if current_time > record.expires_at:
            record.is_used = True
            db.commit()
            raise AuthServiceError(400, "Verification code has expired. Please request a new OTP.")
        if record.attempts_left <= 0:
            record.is_used = True
            db.commit()
            raise AuthServiceError(429, "Maximum verification attempts exceeded. Please request a new OTP.")

        computed = hash_otp(clean_otp, record.otp_salt)
        if not hmac.compare_digest(computed, record.otp_hash):
            record.attempts_left -= 1
            if record.attempts_left <= 0:
                record.is_used = True
                db.commit()
                raise AuthServiceError(429, "Incorrect OTP. Maximum verification attempts exceeded. Please request a new OTP.")
            db.commit()
            raise AuthServiceError(400, f"Invalid OTP code. {record.attempts_left} attempts remaining.")

        record.is_verified = True
        record.is_used = True
        pending_user = db.query(models.User).filter(models.User.email == clean_email).first()
        if pending_user:
            pending_user.is_verified = True
        db.commit()

    return {
        "status": "verified",
        "email": clean_email,
        "message": "Email verified successfully.",
    }


def verify_email_token(raw_token: str, db: Any, now: Optional[datetime] = None) -> Dict[str, Any]:
    clean_token = (raw_token or "").strip()
    if len(clean_token) < 16:
        raise AuthServiceError(400, "Invalid verification link.")

    token_digest = hash_token(clean_token)
    current_time = now or datetime.utcnow()

    if _is_sqlite_conn(db):
        row = db.execute(
            """
            SELECT id, email, expires_at, is_used
            FROM email_otps
            WHERE token_hash = ?
            ORDER BY id DESC LIMIT 1
            """,
            (token_digest,),
        ).fetchone()
        if not row:
            raise AuthServiceError(400, "Invalid or unknown verification link.")
        rec_id, rec_email, exp_str, is_used = row
        if bool(is_used):
            raise AuthServiceError(400, "This verification link has already been used or invalidated.")
        exp_dt = _parse_dt(exp_str)
        if exp_dt and current_time > exp_dt:
            db.execute("UPDATE email_otps SET is_used = 1 WHERE id = ?", (rec_id,))
            db.commit()
            raise AuthServiceError(410, "Verification link has expired. Please request a new verification email.")
        db.execute("UPDATE email_otps SET is_verified = 1, is_used = 1 WHERE id = ?", (rec_id,))
        db.execute("UPDATE users SET is_verified = 1 WHERE lower(email) = ?", (rec_email.lower(),))
        db.commit()
        verified_email = rec_email
    else:
        import models

        record = (
            db.query(models.EmailOtp)
            .filter(models.EmailOtp.token_hash == token_digest)
            .order_by(models.EmailOtp.id.desc())
            .first()
        )
        if not record:
            raise AuthServiceError(400, "Invalid or unknown verification link.")
        if record.is_used:
            raise AuthServiceError(400, "This verification link has already been used or invalidated.")
        if current_time > record.expires_at:
            record.is_used = True
            db.commit()
            raise AuthServiceError(410, "Verification link has expired. Please request a new verification email.")
        record.is_verified = True
        record.is_used = True
        pending_user = db.query(models.User).filter(models.User.email == record.email).first()
        if pending_user:
            pending_user.is_verified = True
        db.commit()
        verified_email = record.email

    return {
        "status": "verified",
        "email": verified_email,
        "message": "Email verified successfully.",
    }


def register_user(
    name: str,
    email: str,
    password: str,
    confirm_password: Optional[str],
    company_name: str,
    gstin: str,
    db: Any,
) -> Dict[str, Any]:
    clean_name = (name or "").strip()
    clean_email = (email or "").strip().lower()
    clean_password = (password or "").strip()
    clean_company = (company_name or "").strip() or "My Enterprise"
    clean_gstin = (gstin or "").strip().upper()

    if not clean_name:
        raise AuthServiceError(400, "Full name is required.")
    if not EMAIL_REGEX.match(clean_email):
        raise AuthServiceError(400, "Valid email address is required.")
    if len(clean_password) < 6:
        raise AuthServiceError(400, "Password must be at least 6 characters.")
    if confirm_password is not None and clean_password != confirm_password.strip():
        raise AuthServiceError(400, "Passwords do not match. Please verify.")

    pw_hash = hash_password(clean_password)

    if _is_sqlite_conn(db):
        existing_user = db.execute(
            "SELECT id, is_verified FROM users WHERE lower(email) = ?", (clean_email,)
        ).fetchone()
        if existing_user and bool(existing_user[1]):
            raise AuthServiceError(409, f"An account with {clean_email} already exists.")

        verified_otp = db.execute(
            """
            SELECT id FROM email_otps
            WHERE lower(email) = ? AND is_verified = 1
            ORDER BY id DESC LIMIT 1
            """,
            (clean_email,),
        ).fetchone()
        if not verified_otp:
            raise AuthServiceError(403, "Account cannot be activated before successful email OTP verification.")

        if existing_user:
            user_id = int(existing_user[0])
            db.execute(
                """
                UPDATE users
                SET name = ?, password_hash = ?, company_name = ?, gstin = ?, is_verified = 1
                WHERE id = ?
                """,
                (clean_name, pw_hash, clean_company, clean_gstin, user_id),
            )
        else:
            cur = db.execute(
                """
                INSERT INTO users (name, email, password_hash, company_name, gstin, is_verified, created_at)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """,
                (clean_name, clean_email, pw_hash, clean_company, clean_gstin, datetime.utcnow().isoformat()),
            )
            user_id = int(cur.lastrowid)
        db.commit()
    else:
        import models

        existing_user = db.query(models.User).filter(models.User.email == clean_email).first()
        if existing_user and existing_user.is_verified:
            raise AuthServiceError(409, f"An account with {clean_email} already exists.")

        verified_otp = (
            db.query(models.EmailOtp)
            .filter(models.EmailOtp.email == clean_email, models.EmailOtp.is_verified == True)
            .order_by(models.EmailOtp.id.desc())
            .first()
        )
        if not verified_otp:
            raise AuthServiceError(403, "Account cannot be activated before successful email OTP verification.")

        if existing_user:
            existing_user.name = clean_name
            existing_user.password_hash = pw_hash
            existing_user.company_name = clean_company
            existing_user.gstin = clean_gstin
            existing_user.is_verified = True
            user = existing_user
        else:
            user = models.User(
                name=clean_name,
                email=clean_email,
                password_hash=pw_hash,
                company_name=clean_company,
                gstin=clean_gstin,
                is_verified=True,
            )
            db.add(user)
        db.commit()
        db.refresh(user)
        user_id = int(user.id)

    token = create_access_token(user_id, clean_email)
    return {
        "id": user_id,
        "name": clean_name,
        "email": clean_email,
        "company_name": clean_company,
        "is_verified": True,
        "access_token": token,
        "token_type": "bearer",
        "expires_in_seconds": SESSION_TOKEN_TTL_SECONDS,
    }


def login_user(email: str, password: str, db: Any) -> Dict[str, Any]:
    clean_email = (email or "").strip().lower()
    clean_password = (password or "").strip()
    if not clean_email or not EMAIL_REGEX.match(clean_email):
        raise AuthServiceError(400, "Please enter a valid email address.")
    if not clean_password:
        raise AuthServiceError(400, "Please enter your password.")

    if _is_sqlite_conn(db):
        row = db.execute(
            "SELECT id, name, email, password_hash, company_name, is_verified FROM users WHERE lower(email) = ?",
            (clean_email,),
        ).fetchone()
        if not row or not verify_password(clean_password, row[3]):
            raise AuthServiceError(401, "Invalid email address or password.")
        if not bool(row[5]):
            raise AuthServiceError(403, "Your email is not verified yet. Please verify your email before logging in.")
        user_id, user_name, user_email, _, company_name, _ = row
    else:
        import models

        user = db.query(models.User).filter(models.User.email == clean_email).first()
        if not user or not verify_password(clean_password, user.password_hash):
            raise AuthServiceError(401, "Invalid email address or password.")
        if not user.is_verified:
            raise AuthServiceError(403, "Your email is not verified yet. Please verify your email before logging in.")
        user_id = int(user.id)
        user_name = user.name
        user_email = user.email
        company_name = user.company_name

    token = create_access_token(user_id, user_email)
    return {
        "id": user_id,
        "name": user_name,
        "email": user_email,
        "company_name": company_name,
        "is_verified": True,
        "access_token": token,
        "token_type": "bearer",
        "expires_in_seconds": SESSION_TOKEN_TTL_SECONDS,
    }


def get_current_user_from_token(authorization_header: Optional[str], db: Any) -> Dict[str, Any]:
    if not authorization_header or not authorization_header.lower().startswith("bearer "):
        raise AuthServiceError(401, "Missing authentication bearer token.")
    token = authorization_header.split(" ", 1)[1].strip()
    payload = verify_access_token(token)
    user_id = int(payload.get("sub", 0))

    if _is_sqlite_conn(db):
        row = db.execute(
            "SELECT id, name, email, company_name, is_verified FROM users WHERE id = ?",
            (user_id,),
        ).fetchone()
        if not row:
            raise AuthServiceError(401, "User session no longer exists.")
        if not bool(row[4]):
            raise AuthServiceError(403, "Account is not verified.")
        return {
            "id": int(row[0]),
            "name": row[1],
            "email": row[2],
            "company_name": row[3],
            "is_verified": True,
        }
    else:
        import models

        user = db.query(models.User).filter(models.User.id == user_id).first()
        if not user:
            raise AuthServiceError(401, "User session no longer exists.")
        if not user.is_verified:
            raise AuthServiceError(403, "Account is not verified.")
        return {
            "id": int(user.id),
            "name": user.name,
            "email": user.email,
            "company_name": user.company_name,
            "is_verified": True,
        }


def request_password_reset(
    email: str,
    db: Any,
    now: Optional[datetime] = None,
    dispatcher=send_password_reset_email,
) -> Dict[str, Any]:
    clean_email = (email or "").strip().lower()
    if not clean_email or not EMAIL_REGEX.match(clean_email):
        raise AuthServiceError(400, "Please enter a valid email address.")
    current_time = now or datetime.utcnow()

    if _is_sqlite_conn(db):
        user = db.execute("SELECT id FROM users WHERE lower(email) = ?", (clean_email,)).fetchone()
    else:
        import models

        user = db.query(models.User).filter(models.User.email == clean_email).first()

    if not user:
        raise AuthServiceError(404, f"No registered account found for {clean_email}.")

    reset_code = f"{secrets.randbelow(900_000) + 100_000:06d}"
    salt = secrets.token_hex(16)
    dispatch: EmailDispatchResult = dispatcher(clean_email, reset_code)
    if not dispatch.success:
        status_code = 503 if not dispatch.configured else 502
        raise AuthServiceError(status_code, dispatch.error or "Failed to send password reset email.")

    if _is_sqlite_conn(db):
        db.execute(
            """
            INSERT INTO email_otps
                (email, otp_hash, otp_salt, purpose, created_at, expires_at, last_sent_at, attempts_left, is_used, is_verified)
            VALUES (?, ?, ?, 'PASSWORD_RESET', ?, ?, ?, ?, 0, 0)
            """,
            (
                clean_email,
                hash_otp(reset_code, salt),
                salt,
                current_time.isoformat(),
                (current_time + timedelta(seconds=OTP_EXPIRY_SECONDS)).isoformat(),
                current_time.isoformat(),
                OTP_MAX_ATTEMPTS,
            ),
        )
        db.commit()
    else:
        import models

        rec = models.EmailOtp(
            email=clean_email,
            otp_hash=hash_otp(reset_code, salt),
            otp_salt=salt,
            purpose="PASSWORD_RESET",
            created_at=current_time,
            expires_at=current_time + timedelta(seconds=OTP_EXPIRY_SECONDS),
            last_sent_at=current_time,
            attempts_left=OTP_MAX_ATTEMPTS,
            is_used=False,
            is_verified=False,
        )
        db.add(rec)
        db.commit()

    return {
        "status": "reset_sent",
        "email": clean_email,
        "message": f"Password reset code sent to {clean_email}.",
    }
