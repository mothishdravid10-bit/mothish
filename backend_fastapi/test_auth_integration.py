from datetime import datetime, timedelta
import os
import sqlite3
import tempfile
import unittest

from auth_service import (
    AuthServiceError,
    get_current_user_from_token,
    hash_password,
    login_user,
    register_user,
    request_password_reset,
    run_safe_sqlite_migrations,
    send_verification_otp,
    verify_access_token,
    verify_email_otp,
    verify_email_token,
)
from email_service import EmailDispatchResult


class AuthIntegrationTests(unittest.TestCase):
    def setUp(self):
        self._env_backup = os.environ.copy()
        os.environ["AUTH_SECRET_KEY"] = "test-only-auth-secret-32-bytes-minimum-key"
        os.environ["BACKEND_PUBLIC_URL"] = "https://api.zenoai-test.example.com"
        self.db_fd, self.db_path = tempfile.mkstemp(suffix=".db")
        self.conn = sqlite3.connect(self.db_path)
        run_safe_sqlite_migrations(self.conn)

    def tearDown(self):
        self.conn.close()
        os.close(self.db_fd)
        if os.path.exists(self.db_path):
            os.unlink(self.db_path)
        os.environ.clear()
        os.environ.update(self._env_backup)

    def test_safe_migration_preserves_existing_users(self):
        fd, legacy_path = tempfile.mkstemp(suffix=".db")
        legacy_conn = sqlite3.connect(legacy_path)
        try:
            legacy_conn.execute(
                """
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL
                )
                """
            )
            legacy_conn.execute(
                """
                CREATE TABLE email_otps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT NOT NULL,
                    otp_hash TEXT NOT NULL,
                    otp_salt TEXT NOT NULL,
                    created_at TEXT,
                    expires_at TEXT NOT NULL
                )
                """
            )
            legacy_conn.execute(
                "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
                ("Legacy Owner", "legacy@zenoai.in", hash_password("legacyPass1")),
            )
            legacy_conn.commit()

            run_safe_sqlite_migrations(legacy_conn)

            user_cols = {r[1] for r in legacy_conn.execute("PRAGMA table_info(users)").fetchall()}
            otp_cols = {r[1] for r in legacy_conn.execute("PRAGMA table_info(email_otps)").fetchall()}
            self.assertIn("is_verified", user_cols)
            self.assertIn("company_name", user_cols)
            self.assertIn("token_hash", otp_cols)

            preserved = legacy_conn.execute(
                "SELECT name, email FROM users WHERE email = ?", ("legacy@zenoai.in",)
            ).fetchone()
            self.assertEqual(preserved, ("Legacy Owner", "legacy@zenoai.in"))
        finally:
            legacy_conn.close()
            os.close(fd)
            if os.path.exists(legacy_path):
                os.unlink(legacy_path)

    def test_otp_and_token_verification_expiry_invalid_and_resend_cooldown(self):
        captured = {}
        now = datetime(2026, 11, 13, 10, 0, 0)

        def fake_dispatcher(recipient, otp_code, verification_link=None):
            captured["recipient"] = recipient
            captured["otp"] = otp_code
            captured["link"] = verification_link
            return EmailDispatchResult(success=True)

        send_res = send_verification_otp("mothish@zenoai.in", self.conn, now=now, dispatcher=fake_dispatcher)
        self.assertEqual(send_res["status"], "otp_sent")
        self.assertIn("token=", captured["link"])
        raw_token = captured["link"].split("token=", 1)[1]

        # Resend inside 60s cooldown blocked with 429
        with self.assertRaises(AuthServiceError) as cooldown_ctx:
            send_verification_otp(
                "mothish@zenoai.in",
                self.conn,
                now=now + timedelta(seconds=20),
                dispatcher=fake_dispatcher,
            )
        self.assertEqual(cooldown_ctx.exception.status_code, 429)

        # Invalid link rejected with 400
        with self.assertRaises(AuthServiceError) as invalid_ctx:
            verify_email_token("invalid_token_value_123456", self.conn, now=now)
        self.assertEqual(invalid_ctx.exception.status_code, 400)

        # Expired link rejected with 410
        with self.assertRaises(AuthServiceError) as expired_ctx:
            verify_email_token(raw_token, self.conn, now=now + timedelta(minutes=11))
        self.assertEqual(expired_ctx.exception.status_code, 410)

        # Re-request after cooldown and verify via token link
        send_verification_otp(
            "mothish@zenoai.in",
            self.conn,
            now=now + timedelta(seconds=65),
            dispatcher=fake_dispatcher,
        )
        fresh_token = captured["link"].split("token=", 1)[1]
        verify_res = verify_email_token(fresh_token, self.conn, now=now + timedelta(seconds=70))
        self.assertEqual(verify_res["status"], "verified")

        # Token replay blocked
        with self.assertRaises(AuthServiceError) as replay_ctx:
            verify_email_token(fresh_token, self.conn, now=now + timedelta(seconds=75))
        self.assertEqual(replay_ctx.exception.status_code, 400)

    def test_registration_duplicate_login_and_session_token_roundtrip(self):
        captured = {}
        now = datetime(2026, 11, 13, 12, 0, 0)

        def fake_dispatcher(recipient, otp_code, verification_link=None):
            captured["otp"] = otp_code
            return EmailDispatchResult(success=True)

        # Unverified registration rejected
        with self.assertRaises(AuthServiceError) as unverified_ctx:
            register_user(
                name="Alice",
                email="alice@zenoai.in",
                password="password123",
                confirm_password="password123",
                company_name="Alice Trading",
                gstin="",
                db=self.conn,
            )
        self.assertEqual(unverified_ctx.exception.status_code, 403)

        # Send + verify OTP
        send_verification_otp("alice@zenoai.in", self.conn, now=now, dispatcher=fake_dispatcher)
        verify_email_otp("alice@zenoai.in", captured["otp"], self.conn, now=now + timedelta(seconds=10))

        # Password mismatch rejected
        with self.assertRaises(AuthServiceError) as mismatch_ctx:
            register_user(
                name="Alice",
                email="alice@zenoai.in",
                password="password123",
                confirm_password="different123",
                company_name="Alice Trading",
                gstin="",
                db=self.conn,
            )
        self.assertEqual(mismatch_ctx.exception.status_code, 400)

        # Valid registration succeeds & returns bearer session token
        reg_res = register_user(
            name="Alice",
            email="alice@zenoai.in",
            password="password123",
            confirm_password="password123",
            company_name="Alice Trading",
            gstin="",
            db=self.conn,
        )
        self.assertTrue(reg_res["is_verified"])
        self.assertTrue(reg_res["access_token"])
        self.assertEqual(reg_res["token_type"], "bearer")

        # Duplicate verified email blocked
        with self.assertRaises(AuthServiceError) as dup_ctx:
            register_user(
                name="Alice 2",
                email="alice@zenoai.in",
                password="password123",
                confirm_password="password123",
                company_name="Dup Corp",
                gstin="",
                db=self.conn,
            )
        self.assertEqual(dup_ctx.exception.status_code, 409)

        # Login with wrong password blocked
        with self.assertRaises(AuthServiceError) as wrong_pw_ctx:
            login_user("alice@zenoai.in", "wrongpass", self.conn)
        self.assertEqual(wrong_pw_ctx.exception.status_code, 401)

        # Login with correct password issues verified session token
        login_res = login_user("alice@zenoai.in", "password123", self.conn)
        self.assertEqual(login_res["email"], "alice@zenoai.in")
        payload = verify_access_token(login_res["access_token"])
        self.assertEqual(payload["email"], "alice@zenoai.in")

        me = get_current_user_from_token(f"Bearer {login_res['access_token']}", self.conn)
        self.assertEqual(me["company_name"], "Alice Trading")

        # Unverified account cannot log in
        self.conn.execute(
            "INSERT INTO users (name, email, password_hash, is_verified) VALUES (?, ?, ?, 0)",
            ("Bob", "bob@zenoai.in", hash_password("bobpass123")),
        )
        self.conn.commit()
        with self.assertRaises(AuthServiceError) as unverified_login_ctx:
            login_user("bob@zenoai.in", "bobpass123", self.conn)
        self.assertEqual(unverified_login_ctx.exception.status_code, 403)

    def test_forgot_password_requires_configured_smtp(self):
        self.conn.execute(
            "INSERT INTO users (name, email, password_hash, is_verified) VALUES (?, ?, ?, 1)",
            ("Reset User", "reset@zenoai.in", hash_password("resetpass1")),
        )
        self.conn.commit()
        for k in ("SMTP_HOST", "SMTP_USER", "SMTP_PASSWORD"):
            os.environ.pop(k, None)

        with self.assertRaises(AuthServiceError) as smtp_ctx:
            request_password_reset("reset@zenoai.in", self.conn)
        self.assertEqual(smtp_ctx.exception.status_code, 503)


if __name__ == "__main__":
    unittest.main()
