import os
import smtplib
import ssl
from dataclasses import dataclass
from email.message import EmailMessage
from typing import Optional


@dataclass
class EmailDispatchResult:
    success: bool
    error: Optional[str] = None
    configured: bool = True


def _parse_bool(value: str, default: bool = True) -> bool:
    if not value:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def is_smtp_configured() -> bool:
    smtp_host = os.getenv("SMTP_HOST", "").strip()
    smtp_user = os.getenv("SMTP_USER", "").strip()
    smtp_password = os.getenv("SMTP_PASSWORD", "").strip()
    return bool(smtp_host and smtp_user and smtp_password)


def _send_email(recipient_email: str, subject: str, body_text: str) -> EmailDispatchResult:
    smtp_host = os.getenv("SMTP_HOST", "").strip()
    smtp_port_raw = os.getenv("SMTP_PORT", "587").strip()
    smtp_user = os.getenv("SMTP_USER", "").strip()
    smtp_password = os.getenv("SMTP_PASSWORD", "").strip()
    smtp_from = os.getenv("SMTP_FROM_EMAIL", smtp_user).strip()
    use_tls = _parse_bool(os.getenv("SMTP_USE_TLS", "true"), default=True)

    if not smtp_host or not smtp_user or not smtp_password:
        return EmailDispatchResult(
            success=False,
            configured=False,
            error=(
                "Backend SMTP email service is not configured. "
                "Please set SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, and SMTP_FROM_EMAIL."
            ),
        )

    try:
        smtp_port = int(smtp_port_raw)
    except ValueError:
        return EmailDispatchResult(
            success=False,
            configured=False,
            error="Invalid SMTP_PORT configuration on backend server.",
        )

    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = smtp_from or smtp_user
    msg["To"] = recipient_email
    msg.set_content(body_text)

    try:
        context = ssl.create_default_context()
        if smtp_port == 465:
            with smtplib.SMTP_SSL(smtp_host, smtp_port, timeout=15, context=context) as server:
                server.login(smtp_user, smtp_password)
                server.send_message(msg)
        else:
            with smtplib.SMTP(smtp_host, smtp_port, timeout=15) as server:
                server.ehlo()
                if use_tls:
                    server.starttls(context=context)
                    server.ehlo()
                server.login(smtp_user, smtp_password)
                server.send_message(msg)
        return EmailDispatchResult(success=True)
    except smtplib.SMTPAuthenticationError:
        return EmailDispatchResult(
            success=False,
            error="Email service authentication failed. Please check SMTP credentials on the backend.",
        )
    except smtplib.SMTPException as exc:
        return EmailDispatchResult(
            success=False,
            error=f"SMTP delivery error: {type(exc).__name__}",
        )
    except OSError:
        return EmailDispatchResult(
            success=False,
            error="Unable to connect to the configured SMTP mail server.",
        )


def send_otp_email(
    recipient_email: str,
    otp_code: str,
    verification_link: Optional[str] = None,
) -> EmailDispatchResult:
    """
    Sends a 6-digit verification OTP (and optional time-limited verification link)
    using SMTP credentials configured strictly via environment variables.
    Never logs or exposes OTPs, tokens, or credentials.
    """
    link_section = (
        f"\nOr verify directly using this secure time-limited link:\n{verification_link}\n"
        if verification_link
        else ""
    )
    body = (
        "Welcome to ZENO AI – Your Personal AI Accountant.\n\n"
        f"Your 6-digit email verification code is: {otp_code}\n"
        f"{link_section}\n"
        "This verification code/link expires in 10 minutes. Do not share it with anyone.\n"
        "If you did not request account registration, you can safely ignore this email."
    )
    return _send_email(recipient_email, "ZENO AI – Email Verification", body)


def send_password_reset_email(recipient_email: str, reset_code: str) -> EmailDispatchResult:
    """Sends a 6-digit password reset verification code via SMTP."""
    body = (
        "ZENO AI Password Reset Request\n\n"
        f"Your 6-digit password reset code is: {reset_code}\n\n"
        "This code expires in 10 minutes. If you did not request a password reset, ignore this email."
    )
    return _send_email(recipient_email, "ZENO AI – Password Reset Code", body)
