from datetime import datetime, timedelta
import sqlite3
import unittest

from zeno_assistant import ZenoIntent, parse_date_range, process_zeno_chat


def _create_test_db() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    cur = conn.cursor()
    cur.executescript(
        """
        CREATE TABLE accounts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            code TEXT,
            name TEXT,
            category TEXT,
            subcategory TEXT,
            opening_balance REAL DEFAULT 0.0,
            current_balance REAL DEFAULT 0.0,
            is_system INTEGER DEFAULT 0
        );
        CREATE TABLE parties (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT,
            type TEXT,
            phone TEXT DEFAULT '',
            email TEXT DEFAULT '',
            address TEXT DEFAULT '',
            gstin TEXT DEFAULT '',
            state_code TEXT DEFAULT '27',
            opening_balance REAL DEFAULT 0.0,
            outstanding_amount REAL DEFAULT 0.0,
            is_active INTEGER DEFAULT 1
        );
        CREATE TABLE sales_invoices (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            invoice_number TEXT,
            customer_id INTEGER,
            customer_name TEXT,
            date TEXT,
            subtotal REAL,
            cgst REAL DEFAULT 0.0,
            sgst REAL DEFAULT 0.0,
            igst REAL DEFAULT 0.0,
            total_amount REAL,
            is_interstate INTEGER DEFAULT 0,
            status TEXT DEFAULT 'UNPAID',
            payment_mode TEXT DEFAULT 'CREDIT'
        );
        CREATE TABLE purchase_bills (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            bill_number TEXT,
            supplier_id INTEGER,
            supplier_name TEXT,
            date TEXT,
            subtotal REAL,
            cgst REAL DEFAULT 0.0,
            sgst REAL DEFAULT 0.0,
            igst REAL DEFAULT 0.0,
            total_amount REAL,
            is_interstate INTEGER DEFAULT 0,
            status TEXT DEFAULT 'UNPAID',
            payment_mode TEXT DEFAULT 'CREDIT'
        );
        CREATE TABLE journal_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            entry_number TEXT,
            date TEXT,
            reference TEXT DEFAULT '',
            narration TEXT,
            total_debit REAL,
            total_credit REAL,
            source_module TEXT DEFAULT 'MANUAL'
        );
        CREATE TABLE payment_vouchers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            voucher_number TEXT,
            type TEXT,
            party_id INTEGER,
            party_name TEXT,
            account_id INTEGER,
            account_name TEXT,
            amount REAL,
            payment_mode TEXT DEFAULT 'BANK',
            date TEXT,
            reference TEXT DEFAULT ''
        );
        CREATE TABLE payment_reminders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT,
            party_id INTEGER,
            party_name TEXT,
            amount REAL,
            due_date TEXT,
            type TEXT,
            status TEXT DEFAULT 'PENDING'
        );
        """
    )
    conn.commit()
    return conn


class ZenoAssistantTests(unittest.TestCase):
    def setUp(self):
        self.db = _create_test_db()
        # Local timezone IST = UTC+330 minutes; Local time: 2026-11-13 10:00:00 => UTC: 2026-11-13 04:30:00
        self.tz_offset = 330
        self.now_utc = datetime(2026, 11, 13, 4, 30, 0)

        # Seed realistic accounting data across yesterday (Nov 12 IST), today (Nov 13 IST), and last week (Nov 6 IST)
        yday_utc = datetime(2026, 11, 12, 8, 0, 0).strftime("%Y-%m-%d %H:%M:%S")
        today_utc = datetime(2026, 11, 13, 3, 0, 0).strftime("%Y-%m-%d %H:%M:%S")
        last_week_utc = datetime(2026, 11, 6, 10, 0, 0).strftime("%Y-%m-%d %H:%M:%S")
        last_month_utc = datetime(2026, 10, 15, 10, 0, 0).strftime("%Y-%m-%d %H:%M:%S")

        cur = self.db.cursor()
        cur.executemany(
            "INSERT INTO accounts (code, name, category, subcategory, current_balance) VALUES (?, ?, ?, ?, ?)",
            [
                ("1010", "Cash on Hand", "ASSET", "Current Assets", 15000.0),
                ("1020", "Bank Account", "ASSET", "Bank Accounts", 85000.0),
                ("4010", "Sales Account", "INCOME", "Direct Income", 35000.0),
                ("5010", "Purchases Account", "EXPENSE", "Direct Expenses", 12000.0),
            ],
        )
        cur.executemany(
            "INSERT INTO parties (name, type, outstanding_amount) VALUES (?, ?, ?)",
            [
                ("Rajesh Electronics", "CUSTOMER", 12000.0),
                ("Meera Traders", "CUSTOMER", 0.0),
                ("ABC Suppliers", "SUPPLIER", 4500.0),
            ],
        )
        cur.executemany(
            "INSERT INTO sales_invoices (invoice_number, customer_id, customer_name, date, subtotal, total_amount, status, payment_mode) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            [
                ("INV-101", 1, "Rajesh Electronics", yday_utc, 10000.0, 11800.0, "UNPAID", "CREDIT"),
                ("INV-102", 2, "Meera Traders", today_utc, 5000.0, 5900.0, "PAID", "BANK"),
                ("INV-100", 2, "Meera Traders", last_month_utc, 8000.0, 9440.0, "PAID", "CASH"),
            ],
        )
        cur.executemany(
            "INSERT INTO purchase_bills (bill_number, supplier_id, supplier_name, date, subtotal, total_amount, status, payment_mode) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            [
                ("BILL-201", 3, "ABC Suppliers", yday_utc, 4000.0, 4720.0, "UNPAID", "CREDIT"),
                ("BILL-200", 3, "ABC Suppliers", last_week_utc, 2000.0, 2360.0, "PAID", "CASH"),
            ],
        )
        cur.execute(
            "INSERT INTO payment_vouchers (voucher_number, type, party_id, party_name, account_id, account_name, amount, payment_mode, date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ("VO-301", "PAYMENT", 3, "Office Rent", 1, "Cash on Hand", 3000.0, "CASH", yday_utc),
        )
        cur.execute(
            "INSERT INTO payment_reminders (title, party_id, party_name, amount, due_date, type, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
            ("Collect from Rajesh", 1, "Rajesh Electronics", 12000.0, today_utc, "CUSTOMER", "PENDING"),
        )
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def test_date_recognition_ranges(self):
        dr_yday = parse_date_range("Show me yesterday's transactions", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertIsNotNone(dr_yday)
        self.assertEqual(dr_yday.label, "yesterday")
        self.assertEqual(dr_yday.start_local.strftime("%Y-%m-%d"), "2026-11-12")

        dr_7 = parse_date_range("Show last 7 days transactions", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(dr_7.start_local.strftime("%Y-%m-%d"), "2026-11-07")
        self.assertEqual(dr_7.end_local.strftime("%Y-%m-%d"), "2026-11-13")

        dr_last_month = parse_date_range("Transactions for last month", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(dr_last_month.start_local.strftime("%Y-%m-%d"), "2026-10-01")
        self.assertEqual(dr_last_month.end_local.strftime("%Y-%m-%d"), "2026-10-31")

        dr_custom = parse_date_range("Show transactions from Nov 1 to Nov 10", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertIsNotNone(dr_custom)
        self.assertEqual(dr_custom.start_local.strftime("%Y-%m-%d"), "2026-11-01")
        self.assertEqual(dr_custom.end_local.strftime("%Y-%m-%d"), "2026-11-10")

    def test_view_and_report_queries_never_route_to_create(self):
        res_yday = process_zeno_chat("Show me yesterday's transactions.", self.db, session_id="s1", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_yday["intent"], ZenoIntent.VIEW_TRANSACTIONS.value)
        self.assertEqual(len(res_yday["transactions"]), 3)  # INV-101, BILL-201, VO-301
        self.assertAlmostEqual(res_yday["summary"]["total_income"], 11800.0)
        self.assertAlmostEqual(res_yday["summary"]["total_expenses"], 7720.0)  # 4720 + 3000

        res_exp = process_zeno_chat("What were my total expenses yesterday?", self.db, session_id="s2", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_exp["intent"], ZenoIntent.VIEW_EXPENSES.value)
        self.assertAlmostEqual(res_exp["summary"]["total_expenses"], 7720.0)
        self.assertEqual(len(res_exp["transactions"]), 2)

        res_inc = process_zeno_chat("How much income did I receive yesterday?", self.db, session_id="s3", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_inc["intent"], ZenoIntent.VIEW_INCOME.value)
        self.assertAlmostEqual(res_inc["summary"]["total_income"], 11800.0)

        res_today = process_zeno_chat("Show today's transactions.", self.db, session_id="s4", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_today["intent"], ZenoIntent.VIEW_TRANSACTIONS.value)
        self.assertEqual(len(res_today["transactions"]), 1)

        res_month = process_zeno_chat("Show transactions for this month.", self.db, session_id="s5", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_month["intent"], ZenoIntent.VIEW_TRANSACTIONS.value)
        self.assertEqual(len(res_month["transactions"]), 5)  # excludes Oct 15 INV-100

        res_bal = process_zeno_chat("What is my current balance?", self.db, session_id="s6", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_bal["intent"], ZenoIntent.VIEW_BALANCE.value)
        self.assertAlmostEqual(res_bal["summary"]["total_balance"], 100000.0)

        res_pnl = process_zeno_chat("Give me my profit and loss report.", self.db, session_id="s7", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_pnl["intent"], ZenoIntent.PROFIT_LOSS.value)
        self.assertAlmostEqual(res_pnl["summary"]["net_profit"], 23000.0)

        res_pending = process_zeno_chat("Show my pending payments.", self.db, session_id="s8", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_pending["intent"], ZenoIntent.VIEW_PENDING_PAYMENTS.value)
        self.assertAlmostEqual(res_pending["summary"]["total_receivables"], 12000.0)
        self.assertAlmostEqual(res_pending["summary"]["total_payables"], 4500.0)

        res_owe = process_zeno_chat("Which customers owe me money?", self.db, session_id="s9", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_owe["intent"], ZenoIntent.VIEW_PENDING_PAYMENTS.value)
        self.assertIn("Rajesh Electronics", res_owe["response"])

        res_ledger = process_zeno_chat("Show ledger for Rajesh Electronics", self.db, session_id="s10", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_ledger["intent"], ZenoIntent.VIEW_LEDGER.value)
        self.assertIn("Rajesh Electronics", res_ledger["response"])

    def test_empty_yesterday_returns_exact_message(self):
        empty_db = _create_test_db()
        try:
            res = process_zeno_chat("Show yesterday's transactions", empty_db, session_id="empty", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
            self.assertEqual(res["intent"], ZenoIntent.VIEW_TRANSACTIONS.value)
            self.assertEqual(res["response"], "No transactions were found for yesterday.")
            self.assertEqual(len(res["transactions"]), 0)
        finally:
            empty_db.close()

    def test_conversational_create_transaction_and_followup_context(self):
        # Multi-turn transaction creation
        step1 = process_zeno_chat("Create a transaction", self.db, session_id="conv1", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(step1["intent"], ZenoIntent.CREATE_TRANSACTION.value)
        self.assertEqual(step1["response"], "What type of transaction? Income or Expense?")

        step2 = process_zeno_chat("Income", self.db, session_id="conv1", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(step2["intent"], ZenoIntent.CREATE_TRANSACTION.value)
        self.assertTrue(step2["needs_clarification"])

        step3 = process_zeno_chat("5000 from Rajesh", self.db, session_id="conv1", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(step3["intent"], ZenoIntent.CREATE_TRANSACTION.value)
        self.assertAlmostEqual(step3["summary"]["amount"], 5000.0)
        self.assertEqual(step3["summary"]["transaction_nature"], "INCOME")

        # Follow-up date context
        q1 = process_zeno_chat("Show me yesterday's transactions", self.db, session_id="conv2", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(len(q1["transactions"]), 3)
        q2 = process_zeno_chat("What about today?", self.db, session_id="conv2", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(q2["intent"], ZenoIntent.DATE_FILTER.value)
        self.assertEqual(q2["date_range"]["label"], "today")
        self.assertEqual(len(q2["transactions"]), 1)

    def test_today_vs_yesterday_expenses_isolation(self):
        # Insert a today expense bill of 1500.0 into purchase_bills
        cur = self.db.cursor()
        today_expense_utc = datetime(2026, 11, 13, 2, 0, 0).strftime("%Y-%m-%d %H:%M:%S")
        cur.execute(
            "INSERT INTO purchase_bills (bill_number, supplier_id, supplier_name, date, subtotal, total_amount, status, payment_mode) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ("BILL-TODAY", 3, "ABC Suppliers", today_expense_utc, 1500.0, 1500.0, "PAID", "CASH"),
        )
        self.db.commit()

        # Query yesterday's expenses - should be 7720 (4720 bill + 3000 voucher)
        res_yday = process_zeno_chat("What were my total expenses yesterday?", self.db, session_id="test_exp", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_yday["intent"], ZenoIntent.VIEW_EXPENSES.value)
        self.assertEqual(res_yday["date_range"]["label"], "yesterday")
        self.assertAlmostEqual(res_yday["summary"]["total_expenses"], 7720.0)

        # Query today's expenses - should strictly be 1500, NOT yesterday's 7720 and NOT all-time total
        res_today = process_zeno_chat("What were my total expenses today?", self.db, session_id="test_exp", now_utc=self.now_utc, tz_offset_minutes=self.tz_offset)
        self.assertEqual(res_today["intent"], ZenoIntent.VIEW_EXPENSES.value)
        self.assertEqual(res_today["date_range"]["label"], "today")
        self.assertAlmostEqual(res_today["summary"]["total_expenses"], 1500.0)



if __name__ == "__main__":
    unittest.main()
