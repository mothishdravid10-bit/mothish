import os
import smtplib
import unittest
from unittest.mock import MagicMock, patch

from email_service import send_otp_email


class EmailServiceTests(unittest.TestCase):
    def setUp(self):
        self._env_backup = os.environ.copy()
        for key in (
            "SMTP_HOST",
            "SMTP_PORT",
            "SMTP_USER",
            "SMTP_PASSWORD",
            "SMTP_FROM_EMAIL",
            "SMTP_USE_TLS",
        ):
            os.environ.pop(key, None)

    def tearDown(self):
        os.environ.clear()
        os.environ.update(self._env_backup)

    def test_missing_smtp_config_returns_unconfigured_error(self):
        result = send_otp_email("user@example.com", "123456")
        self.assertFalse(result.success)
        self.assertFalse(result.configured)
        self.assertIn("not configured", result.error)
        self.assertNotIn("123456", result.error)

    @patch("email_service.smtplib.SMTP")
    def test_send_otp_email_tls_success_and_no_credential_leak(self, mock_smtp_cls):
        os.environ["SMTP_HOST"] = "smtp.gmail.com"
        os.environ["SMTP_PORT"] = "587"
        os.environ["SMTP_USER"] = "noreply@zenoai.in"
        os.environ["SMTP_PASSWORD"] = "app-secret-password"
        os.environ["SMTP_FROM_EMAIL"] = "noreply@zenoai.in"
        os.environ["SMTP_USE_TLS"] = "true"

        smtp_instance = MagicMock()
        mock_smtp_cls.return_value.__enter__.return_value = smtp_instance

        result = send_otp_email("mothish@example.com", "849201")
        self.assertTrue(result.success)
        self.assertTrue(result.configured)
        self.assertIsNone(result.error)

        smtp_instance.starttls.assert_called_once()
        smtp_instance.login.assert_called_once_with("noreply@zenoai.in", "app-secret-password")
        smtp_instance.send_message.assert_called_once()
        sent_msg = smtp_instance.send_message.call_args[0][0]
        self.assertEqual(sent_msg["To"], "mothish@example.com")
        self.assertIn("849201", sent_msg.get_content())

    @patch("email_service.smtplib.SMTP")
    def test_smtp_auth_failure_does_not_expose_password(self, mock_smtp_cls):
        os.environ["SMTP_HOST"] = "smtp.gmail.com"
        os.environ["SMTP_PORT"] = "587"
        os.environ["SMTP_USER"] = "noreply@zenoai.in"
        os.environ["SMTP_PASSWORD"] = "wrong-secret"

        smtp_instance = MagicMock()
        smtp_instance.login.side_effect = smtplib.SMTPAuthenticationError(535, b"Auth failed")
        mock_smtp_cls.return_value.__enter__.return_value = smtp_instance

        result = send_otp_email("mothish@example.com", "123456")
        self.assertFalse(result.success)
        self.assertNotIn("wrong-secret", result.error)
        self.assertNotIn("123456", result.error)
        self.assertIn("authentication failed", result.error.lower())


if __name__ == "__main__":
    unittest.main()
