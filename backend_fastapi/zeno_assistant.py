from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from enum import Enum
import re
import sqlite3
from typing import Any, Dict, List, Optional, Tuple


class ZenoIntent(str, Enum):
    VIEW_TRANSACTIONS = "VIEW_TRANSACTIONS"
    CREATE_TRANSACTION = "CREATE_TRANSACTION"
    VIEW_INCOME = "VIEW_INCOME"
    VIEW_EXPENSES = "VIEW_EXPENSES"
    VIEW_BALANCE = "VIEW_BALANCE"
    PROFIT_LOSS = "PROFIT_LOSS"
    VIEW_LEDGER = "VIEW_LEDGER"
    VIEW_PENDING_PAYMENTS = "VIEW_PENDING_PAYMENTS"
    DATE_FILTER = "DATE_FILTER"


@dataclass
class DateRange:
    label: str
    start_local: datetime
    end_local: datetime
    start_utc: datetime
    end_utc: datetime

    def to_dict(self) -> Dict[str, str]:
        return {
            "label": self.label,
            "start": self.start_local.strftime("%Y-%m-%d %H:%M:%S"),
            "end": self.end_local.strftime("%Y-%m-%d %H:%M:%S"),
        }


@dataclass
class ConversationState:
    last_intent: Optional[ZenoIntent] = None
    last_date_range: Optional[DateRange] = None
    pending_stage: Optional[str] = None  # "AWAITING_TRANSACTION_TYPE" | "AWAITING_TRANSACTION_DETAILS"
    pending_tx_nature: Optional[str] = None  # "INCOME" | "EXPENSE"
    history: List[Dict[str, str]] = field(default_factory=list)


_CONVERSATION_STORE: Dict[str, ConversationState] = {}

MONTH_MAP = {
    "jan": 1, "january": 1,
    "feb": 2, "february": 2,
    "mar": 3, "march": 3,
    "apr": 4, "april": 4,
    "may": 5,
    "jun": 6, "june": 6,
    "jul": 7, "july": 7,
    "aug": 8, "august": 8,
    "sep": 9, "sept": 9, "september": 9,
    "oct": 10, "october": 10,
    "nov": 11, "november": 11,
    "dec": 12, "december": 12,
}


def get_local_now(now_utc: Optional[datetime] = None, tz_offset_minutes: Optional[int] = None) -> Tuple[datetime, timedelta]:
    utc_now = now_utc or datetime.utcnow()
    if tz_offset_minutes is not None:
        offset = timedelta(minutes=tz_offset_minutes)
    else:
        local_dt = datetime.now().astimezone()
        offset = local_dt.utcoffset() or timedelta(0)
    return utc_now + offset, offset


def _make_range(label: str, start_local: datetime, end_local: datetime, offset: timedelta) -> DateRange:
    s = start_local.replace(hour=0, minute=0, second=0, microsecond=0)
    e = end_local.replace(hour=23, minute=59, second=59, microsecond=999999)
    return DateRange(
        label=label,
        start_local=s,
        end_local=e,
        start_utc=s - offset,
        end_utc=e - offset,
    )


def _parse_single_date_token(token: str, default_year: int) -> Optional[datetime]:
    cleaned = token.strip().rstrip(",.")
    for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y", "%d/%m/%y", "%d-%m-%y"):
        try:
            return datetime.strptime(cleaned, fmt)
        except ValueError:
            pass

    # "Nov 10" or "Nov 10 2026"
    m1 = re.match(r"^(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\s+(\d{1,2})(?:[,\s]+(\d{4}))?$", cleaned, re.IGNORECASE)
    if m1:
        month = MONTH_MAP[m1.group(1).lower()]
        day = int(m1.group(2))
        year = int(m1.group(3)) if m1.group(3) else default_year
        try:
            return datetime(year, month, day)
        except ValueError:
            return None

    # "10 Nov" or "10 Nov 2026"
    m2 = re.match(r"^(\d{1,2})\s+(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)(?:[,\s]+(\d{4}))?$", cleaned, re.IGNORECASE)
    if m2:
        day = int(m2.group(1))
        month = MONTH_MAP[m2.group(2).lower()]
        year = int(m2.group(3)) if m2.group(3) else default_year
        try:
            return datetime(year, month, day)
        except ValueError:
            return None

    return None


_DATE_TOKEN = (
    r"(?:\d{4}-\d{2}-\d{2}"
    r"|\d{1,2}[/-]\d{1,2}[/-]\d{2,4}"
    r"|(?:jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\s+\d{1,2}(?:[,\s]+\d{4})?"
    r"|\d{1,2}\s+(?:jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)(?:[,\s]+\d{4})?)"
)


def parse_date_range(
    query: str,
    now_utc: Optional[datetime] = None,
    tz_offset_minutes: Optional[int] = None,
) -> Optional[DateRange]:
    local_now, offset = get_local_now(now_utc, tz_offset_minutes)
    lower = query.lower().strip()

    # Custom date range: "from X to Y" or "between X and Y" or "X - Y"
    range_patterns = [
        rf"(?:from\s+)?({_DATE_TOKEN})\s+(?:to|till|until|-)\s+({_DATE_TOKEN})",
        rf"between\s+({_DATE_TOKEN})\s+and\s+({_DATE_TOKEN})",
    ]
    for pat in range_patterns:
        match = re.search(pat, query, flags=re.IGNORECASE)
        if match:
            left = _parse_single_date_token(match.group(1), local_now.year)
            right = _parse_single_date_token(match.group(2), local_now.year)
            if left and right:
                if left > right:
                    left, right = right, left
                label = f"{left.strftime('%d %b %Y')} to {right.strftime('%d %b %Y')}"
                return _make_range(label, left, right, offset)

    # Relative keywords
    has_today = any(k in lower for k in ["today", "innaiku", "இன்று", "aaj"])
    has_yesterday = any(k in lower for k in ["yesterday", "nethu", "நேற்று"])

    if has_today and not has_yesterday:
        return _make_range("today", local_now, local_now, offset)

    if has_yesterday and not has_today:
        yday = local_now - timedelta(days=1)
        return _make_range("yesterday", yday, yday, offset)

    if has_today:
        return _make_range("today", local_now, local_now, offset)

    if any(k in lower for k in ["last 7 days", "past 7 days", "last seven days", "7 days"]):
        start = local_now - timedelta(days=6)
        return _make_range("last 7 days", start, local_now, offset)

    if "this week" in lower:
        start = local_now - timedelta(days=local_now.weekday())
        return _make_range("this week", start, local_now, offset)

    if "last week" in lower or "previous week" in lower:
        this_monday = local_now - timedelta(days=local_now.weekday())
        last_monday = this_monday - timedelta(days=7)
        last_sunday = this_monday - timedelta(days=1)
        return _make_range("last week", last_monday, last_sunday, offset)

    if any(k in lower for k in ["last month", "previous month", "pona maasam"]):
        first_this_month = local_now.replace(day=1)
        last_day_prev = first_this_month - timedelta(days=1)
        first_prev = last_day_prev.replace(day=1)
        return _make_range("last month", first_prev, last_day_prev, offset)

    if any(k in lower for k in ["this month", "current month", "indha maasam"]):
        first_this = local_now.replace(day=1)
        return _make_range("this month", first_this, local_now, offset)

    # Single explicit date: e.g., "on 2026-11-12" or "for Nov 10" or "on 10 Nov 2026"
    single_match = re.search(
        r"(?:\bon\b|\bfor\b)\s+((?:\d{4}-\d{2}-\d{2})|(?:\d{1,2}[/-]\d{1,2}[/-]\d{2,4})|(?:[A-Za-z]+\s+\d{1,2}(?:[,\s]+\d{4})?)|(?:\d{1,2}\s+[A-Za-z]+(?:[,\s]+\d{4})?))",
        query,
        flags=re.IGNORECASE,
    )
    if single_match:
        dt = _parse_single_date_token(single_match.group(1), local_now.year)
        if dt:
            return _make_range(dt.strftime("%d %b %Y"), dt, dt, offset)

    return None


def _strip_wake_words(query: str) -> str:
    return re.sub(r"^(?:hey|ok|hi|hello)?\s*zeno[\s,:-]*", "", query.strip(), flags=re.IGNORECASE).strip()


def _extract_amount_from_text(text: str) -> Optional[float]:
    cleaned = re.sub(r"\b\d+(?:\.\d+)?\s*(?:%|percent|gst)\b", "", text, flags=re.IGNORECASE)
    matches = re.findall(r"(?:₹|rs\.?|inr)?\s*(\d[\d,]*(?:\.\d+)?)", cleaned, flags=re.IGNORECASE)
    vals: List[float] = []
    for m in matches:
        try:
            v = float(m.replace(",", ""))
            if v > 0:
                vals.append(v)
        except ValueError:
            pass
    return max(vals) if vals else None


def classify_intent(
    query: str,
    context: Optional[ConversationState] = None,
    has_date_range: bool = False,
) -> ZenoIntent:
    q = _strip_wake_words(query).lower()

    # Handle multi-turn pending creation slot first
    if context and context.pending_stage == "AWAITING_TRANSACTION_TYPE":
        if any(w in q for w in ["income", "sale", "sales", "receipt", "received", "revenue", "varavu"]):
            return ZenoIntent.CREATE_TRANSACTION
        if any(w in q for w in ["expense", "purchase", "bought", "payment", "paid", "spend", "selavu"]):
            return ZenoIntent.CREATE_TRANSACTION

    if context and context.pending_stage == "AWAITING_TRANSACTION_DETAILS":
        if _extract_amount_from_text(q) is not None and not _is_explicit_view_or_report(q):
            return ZenoIntent.CREATE_TRANSACTION

    # CRITICAL RULE: Never route a VIEW or REPORT command into CREATE_TRANSACTION
    if _is_explicit_view_or_report(q):
        return _classify_view_or_report_intent(q, context, has_date_range)

    # Direct creation phrasing (only if not a view/report question)
    create_verbs = [
        "create a transaction", "create transaction", "add transaction", "new transaction",
        "record transaction", "log transaction", "post transaction", "enter transaction",
        "sold", "purchased", "bought", "paid", "received", "vithom", "vaangunom",
        "kuduthen", "becha", "kharida", "diya", "mila",
    ]
    if any(k in q for k in create_verbs):
        return ZenoIntent.CREATE_TRANSACTION

    # If user mentions an amount alongside sales/purchase/expense without view words -> CREATE_TRANSACTION
    if _extract_amount_from_text(q) is not None and any(
        k in q for k in ["sale", "sales", "bill", "invoice", "purchase", "expense", "rent", "salary", "payment", "receipt"]
    ):
        return ZenoIntent.CREATE_TRANSACTION

    # Otherwise classify among view/report/date intents
    return _classify_view_or_report_intent(q, context, has_date_range)


def _is_explicit_view_or_report(q: str) -> bool:
    view_prefixes = (
        "show", "view", "list", "get", "give me", "tell me", "what is", "what are",
        "what were", "how much", "which", "check", "display", "fetch", "find",
        "filter", "report", "summary", "statement", "what about", "how about", "and ",
    )
    if q.startswith(view_prefixes):
        return True
    view_keywords = [
        "transactions", "transaction history", "profit and loss", "profit & loss", "p&l",
        "pnl", "current balance", "bank balance", "cash balance", "pending payments",
        "owe me", "receivables", "payables", "ledger", "total expenses", "total income",
        "what were my", "how much income", "did i receive", "did i spend",
    ]
    return any(k in q for k in view_keywords)


def _classify_view_or_report_intent(
    q: str,
    context: Optional[ConversationState],
    has_date_range: bool,
) -> ZenoIntent:
    if any(k in q for k in ["profit and loss", "profit & loss", "profit or loss", "p&l", "pnl", "net profit", "profitability", "loss report"]):
        return ZenoIntent.PROFIT_LOSS

    if any(k in q for k in ["pending payment", "owe me", "owes me", "receivable", "payable", "overdue", "due from", "unpaid", "who owes"]):
        return ZenoIntent.VIEW_PENDING_PAYMENTS

    if any(k in q for k in ["ledger", "account statement", "party statement"]):
        return ZenoIntent.VIEW_LEDGER

    if any(k in q for k in ["current balance", "cash balance", "bank balance", "my balance", "how much balance", "available balance", "cash and bank"]):
        return ZenoIntent.VIEW_BALANCE

    if any(k in q for k in ["expense", "expenses", "spent", "spend", "spending", "purchases", "purchase bill", "cost", "costs", "outflow"]):
        return ZenoIntent.VIEW_EXPENSES

    if any(k in q for k in ["income", "revenue", "earned", "earn", "earnings", "sales", "receipts", "inflow"]):
        return ZenoIntent.VIEW_INCOME

    if any(k in q for k in ["transaction", "transactions", "entries", "history", "activity", "records"]):
        return ZenoIntent.VIEW_TRANSACTIONS

    if has_date_range:
        return ZenoIntent.DATE_FILTER

    if context and context.last_intent and context.last_intent != ZenoIntent.CREATE_TRANSACTION:
        return context.last_intent

    return ZenoIntent.VIEW_TRANSACTIONS


def _parse_db_datetime(value: Any) -> Optional[datetime]:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value
    if isinstance(value, (int, float)):
        # Epoch ms or s
        sec = value / 1000.0 if value > 1e11 else value
        return datetime.utcfromtimestamp(sec)
    if isinstance(value, str):
        for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S.%f", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"):
            try:
                return datetime.strptime(value[:26], fmt)
            except ValueError:
                pass
    return None


def _in_range(dt: Optional[datetime], date_range: Optional[DateRange], offset: timedelta = timedelta(0)) -> bool:
    if date_range is None:
        return True
    if dt is None:
        return False
    # Check standard UTC range (for timestamps stored in UTC)
    if date_range.start_utc <= dt <= date_range.end_utc:
        return True
    # Check local range (for timestamps converted to local time or already in local time)
    local_dt = dt + offset
    if date_range.start_local <= local_dt <= date_range.end_local:
        return True
    # For date-only comparisons (when dt was saved as YYYY-MM-DD or without time)
    if dt.hour == 0 and dt.minute == 0 and dt.second == 0 and dt.microsecond == 0:
        if date_range.start_local.date() <= dt.date() <= date_range.end_local.date():
            return True
    return False


def _fetch_rows_from_db(db: Any) -> Dict[str, List[Dict[str, Any]]]:
    if isinstance(db, sqlite3.Connection):
        db.row_factory = sqlite3.Row
        cur = db.cursor()

        def safe_select(sql: str) -> List[Dict[str, Any]]:
            try:
                return [dict(r) for r in cur.execute(sql).fetchall()]
            except sqlite3.OperationalError:
                return []

        return {
            "sales_invoices": safe_select("SELECT * FROM sales_invoices ORDER BY date DESC, id DESC"),
            "purchase_bills": safe_select("SELECT * FROM purchase_bills ORDER BY date DESC, id DESC"),
            "payment_vouchers": safe_select("SELECT * FROM payment_vouchers ORDER BY date DESC, id DESC"),
            "journal_entries": safe_select("SELECT * FROM journal_entries ORDER BY date DESC, id DESC"),
            "accounts": safe_select("SELECT * FROM accounts ORDER BY code ASC"),
            "parties": safe_select("SELECT * FROM parties ORDER BY name ASC"),
            "payment_reminders": safe_select("SELECT * FROM payment_reminders ORDER BY due_date ASC"),
        }

    # SQLAlchemy Session
    import models

    def to_dicts(items: List[Any], cols: List[str]) -> List[Dict[str, Any]]:
        out = []
        for obj in items:
            out.append({c: getattr(obj, c, None) for c in cols})
        return out

    return {
        "sales_invoices": to_dicts(
            db.query(models.SalesInvoice).all(),
            ["id", "invoice_number", "customer_id", "customer_name", "date", "subtotal", "cgst", "sgst", "igst", "total_amount", "status", "payment_mode"],
        ),
        "purchase_bills": to_dicts(
            db.query(models.PurchaseBill).all(),
            ["id", "bill_number", "supplier_id", "supplier_name", "date", "subtotal", "cgst", "sgst", "igst", "total_amount", "status", "payment_mode"],
        ),
        "payment_vouchers": to_dicts(
            db.query(models.PaymentVoucher).all(),
            ["id", "voucher_number", "type", "party_id", "party_name", "account_name", "amount", "payment_mode", "date", "reference"],
        ),
        "journal_entries": to_dicts(
            db.query(models.JournalEntry).all(),
            ["id", "entry_number", "date", "reference", "narration", "total_debit", "total_credit", "source_module"],
        ),
        "accounts": to_dicts(
            db.query(models.Account).all(),
            ["id", "code", "name", "category", "subcategory", "opening_balance", "current_balance"],
        ),
        "parties": to_dicts(
            db.query(models.Party).all(),
            ["id", "name", "type", "phone", "email", "outstanding_amount", "is_active"],
        ),
        "payment_reminders": to_dicts(
            db.query(models.PaymentReminder).all(),
            ["id", "title", "party_name", "amount", "due_date", "type", "status"],
        ),
    }


def _collect_transactions(rows: Dict[str, List[Dict[str, Any]]], date_range: Optional[DateRange], offset: timedelta) -> Tuple[List[Dict[str, Any]], float, float]:
    txs: List[Dict[str, Any]] = []
    sales_income = 0.0
    receipt_inflow = 0.0
    expense_total = 0.0

    for s in rows["sales_invoices"]:
        dt = _parse_db_datetime(s.get("date"))
        if _in_range(dt, date_range, offset):
            amt = float(s.get("total_amount") or 0.0)
            sales_income += amt
            local_dt = (dt + offset) if dt else None
            txs.append({
                "id": f"INV-{s.get('id')}",
                "type": "SALES",
                "category": "INCOME",
                "reference": s.get("invoice_number") or "",
                "party": s.get("customer_name") or "Customer",
                "amount": amt,
                "payment_mode": s.get("payment_mode") or "CREDIT",
                "status": s.get("status") or "UNPAID",
                "date": local_dt.strftime("%d %b %Y") if local_dt else "",
                "_sort": dt.timestamp() if dt else 0.0,
            })

    for b in rows["purchase_bills"]:
        dt = _parse_db_datetime(b.get("date"))
        if _in_range(dt, date_range, offset):
            amt = float(b.get("total_amount") or 0.0)
            expense_total += amt
            local_dt = (dt + offset) if dt else None
            txs.append({
                "id": f"BILL-{b.get('id')}",
                "type": "PURCHASE",
                "category": "EXPENSE",
                "reference": b.get("bill_number") or "",
                "party": b.get("supplier_name") or "Supplier",
                "amount": amt,
                "payment_mode": b.get("payment_mode") or "CREDIT",
                "status": b.get("status") or "UNPAID",
                "date": local_dt.strftime("%d %b %Y") if local_dt else "",
                "_sort": dt.timestamp() if dt else 0.0,
            })

    for v in rows["payment_vouchers"]:
        dt = _parse_db_datetime(v.get("date"))
        if _in_range(dt, date_range, offset):
            amt = float(v.get("amount") or 0.0)
            vtype = str(v.get("type") or "PAYMENT").upper()
            is_receipt = vtype == "RECEIPT"
            if is_receipt:
                receipt_inflow += amt
            else:
                expense_total += amt
            local_dt = (dt + offset) if dt else None
            txs.append({
                "id": f"VO-{v.get('id')}",
                "type": vtype,
                "category": "INCOME" if is_receipt else "EXPENSE",
                "reference": v.get("voucher_number") or "",
                "party": v.get("party_name") or "Counter Party",
                "amount": amt,
                "payment_mode": v.get("payment_mode") or "BANK",
                "status": "SETTLED",
                "date": local_dt.strftime("%d %b %Y") if local_dt else "",
                "_sort": dt.timestamp() if dt else 0.0,
            })

    for j in rows["journal_entries"]:
        src = str(j.get("source_module") or "MANUAL").upper()
        if src not in ("MANUAL", "JOURNAL"):
            continue
        dt = _parse_db_datetime(j.get("date"))
        if _in_range(dt, date_range, offset):
            amt = float(j.get("total_debit") or 0.0)
            local_dt = (dt + offset) if dt else None
            txs.append({
                "id": f"JRN-{j.get('id')}",
                "type": "JOURNAL",
                "category": "JOURNAL",
                "reference": j.get("entry_number") or "",
                "party": j.get("narration") or "Manual Journal",
                "amount": amt,
                "payment_mode": "LEDGER",
                "status": "POSTED",
                "date": local_dt.strftime("%d %b %Y") if local_dt else "",
                "_sort": dt.timestamp() if dt else 0.0,
            })

    txs.sort(key=lambda x: x["_sort"], reverse=True)
    for t in txs:
        t.pop("_sort", None)

    total_income = sales_income if sales_income > 0 else receipt_inflow
    return txs, total_income, expense_total


def process_zeno_chat(
    query: str,
    db: Any,
    session_id: str = "default",
    now_utc: Optional[datetime] = None,
    tz_offset_minutes: Optional[int] = None,
    reset_context: bool = False,
) -> Dict[str, Any]:
    if reset_context:
        _CONVERSATION_STORE.pop(session_id, None)

    ctx = _CONVERSATION_STORE.setdefault(session_id, ConversationState())
    _, offset = get_local_now(now_utc, tz_offset_minutes)

    clean_query = _strip_wake_words(query)
    if not clean_query:
        return {
            "intent": ZenoIntent.VIEW_TRANSACTIONS.value,
            "response": "Hello, I am ZENO AI. Ask me for transactions, expenses, income, balances, profit & loss, pending payments, or say 'Create a transaction'.",
            "date_range": None,
            "summary": {},
            "transactions": [],
            "follow_up_question": None,
        }

    parsed_date = parse_date_range(clean_query, now_utc=now_utc, tz_offset_minutes=tz_offset_minutes)
    intent = classify_intent(clean_query, context=ctx, has_date_range=(parsed_date is not None))

    # Conversational slot-filling for CREATE_TRANSACTION
    if intent == ZenoIntent.CREATE_TRANSACTION:
        lower = clean_query.lower()
        amt = _extract_amount_from_text(clean_query)
        inferred_nature = ctx.pending_tx_nature
        if any(w in lower for w in ["income", "sale", "sales", "sold", "receipt", "received", "invoice"]):
            inferred_nature = "INCOME"
        elif any(w in lower for w in ["expense", "purchase", "purchased", "bought", "payment", "paid", "rent", "salary"]):
            inferred_nature = "EXPENSE"

        if inferred_nature is None and amt is None:
            ctx.pending_stage = "AWAITING_TRANSACTION_TYPE"
            ctx.last_intent = ZenoIntent.CREATE_TRANSACTION
            reply = "What type of transaction? Income or Expense?"
            ctx.history.append({"user": query, "assistant": reply})
            return {
                "intent": intent.value,
                "response": reply,
                "date_range": None,
                "summary": {},
                "transactions": [],
                "follow_up_question": reply,
                "needs_clarification": True,
            }

        if inferred_nature is not None and amt is None:
            ctx.pending_stage = "AWAITING_TRANSACTION_DETAILS"
            ctx.pending_tx_nature = inferred_nature
            ctx.last_intent = ZenoIntent.CREATE_TRANSACTION
            label = "Income" if inferred_nature == "INCOME" else "Expense"
            reply = f"Got it — {label}. What is the amount and party or description?"
            ctx.history.append({"user": query, "assistant": reply})
            return {
                "intent": intent.value,
                "response": reply,
                "date_range": None,
                "summary": {"transaction_nature": inferred_nature},
                "transactions": [],
                "follow_up_question": reply,
                "needs_clarification": True,
            }

        # Complete details present
        nature_label = "INCOME" if inferred_nature != "EXPENSE" else "EXPENSE"
        ctx.pending_stage = None
        ctx.pending_tx_nature = None
        ctx.last_intent = ZenoIntent.CREATE_TRANSACTION
        reply = f"Prepared {nature_label} transaction of ₹{amt:,.2f}. Please review and confirm to post."
        ctx.history.append({"user": query, "assistant": reply})
        return {
            "intent": intent.value,
            "response": reply,
            "date_range": None,
            "summary": {"amount": amt, "transaction_nature": nature_label},
            "transactions": [],
            "follow_up_question": None,
            "needs_clarification": False,
        }

    # Clear pending transaction creation when switching to a view/report query
    ctx.pending_stage = None
    ctx.pending_tx_nature = None

    effective_intent = intent
    if intent == ZenoIntent.DATE_FILTER:
        if ctx.last_intent and ctx.last_intent not in (ZenoIntent.CREATE_TRANSACTION, ZenoIntent.DATE_FILTER):
            effective_intent = ctx.last_intent
        else:
            effective_intent = ZenoIntent.VIEW_TRANSACTIONS

    effective_date = parsed_date or (
        ctx.last_date_range if effective_intent in (
            ZenoIntent.VIEW_TRANSACTIONS, ZenoIntent.VIEW_INCOME, ZenoIntent.VIEW_EXPENSES
        ) and parsed_date is None and any(
            w in clean_query.lower() for w in ["what about", "how about", "for that", "those", "same period", "and "]
        ) else None
    )

    rows = _fetch_rows_from_db(db)
    txs, total_income, total_expenses = _collect_transactions(rows, effective_date, offset)
    period_label = effective_date.label if effective_date else "all time"

    if effective_intent == ZenoIntent.VIEW_TRANSACTIONS:
        if not txs:
            reply = f"No transactions were found for {period_label}." if effective_date else "No transactions were found in your accounting books."
        else:
            reply = (
                f"Found {len(txs)} transaction(s) for {period_label}: "
                f"Total Income ₹{total_income:,.2f} and Total Expenses ₹{total_expenses:,.2f}."
            )
        ctx.last_intent = ZenoIntent.VIEW_TRANSACTIONS
        if effective_date:
            ctx.last_date_range = effective_date

    elif effective_intent == ZenoIntent.VIEW_INCOME:
        income_txs = [t for t in txs if t["category"] == "INCOME"]
        if not income_txs:
            reply = f"No income transactions were found for {period_label}."
        else:
            reply = f"Total income for {period_label} is ₹{total_income:,.2f} across {len(income_txs)} entry(ies)."
        txs = income_txs
        ctx.last_intent = ZenoIntent.VIEW_INCOME
        if effective_date:
            ctx.last_date_range = effective_date

    elif effective_intent == ZenoIntent.VIEW_EXPENSES:
        expense_txs = [t for t in txs if t["category"] == "EXPENSE"]
        if not expense_txs:
            reply = f"No expenses were found for {period_label}."
        else:
            reply = f"Total expenses for {period_label} were ₹{total_expenses:,.2f} across {len(expense_txs)} entry(ies)."
        txs = expense_txs
        ctx.last_intent = ZenoIntent.VIEW_EXPENSES
        if effective_date:
            ctx.last_date_range = effective_date

    elif effective_intent == ZenoIntent.VIEW_BALANCE:
        accounts = rows["accounts"]
        cash_bal = sum(float(a.get("current_balance") or 0.0) for a in accounts if str(a.get("code")) == "1010")
        bank_bal = sum(float(a.get("current_balance") or 0.0) for a in accounts if str(a.get("code")) in ("1020", "1030"))
        liquid = cash_bal + bank_bal
        reply = f"Your current Cash & Bank balance is ₹{liquid:,.2f} (Cash: ₹{cash_bal:,.2f}, Bank: ₹{bank_bal:,.2f})."
        ctx.last_intent = ZenoIntent.VIEW_BALANCE
        return {
            "intent": intent.value,
            "response": reply,
            "date_range": None,
            "summary": {"cash_balance": cash_bal, "bank_balance": bank_bal, "total_balance": liquid},
            "transactions": [],
            "follow_up_question": None,
        }

    elif effective_intent == ZenoIntent.PROFIT_LOSS:
        accounts = rows["accounts"]
        if effective_date is not None:
            inc = total_income
            exp = total_expenses
        else:
            acc_inc = sum(float(a.get("current_balance") or 0.0) for a in accounts if str(a.get("category")).upper() == "INCOME")
            acc_exp = sum(float(a.get("current_balance") or 0.0) for a in accounts if str(a.get("category")).upper() == "EXPENSE")
            inc = acc_inc if acc_inc > 0 else total_income
            exp = acc_exp if acc_exp > 0 else total_expenses
        net = inc - exp
        status_word = "Net Profit" if net >= 0 else "Net Loss"
        reply = f"Profit & Loss ({period_label}): Income ₹{inc:,.2f}, Expenses ₹{exp:,.2f}, {status_word} ₹{abs(net):,.2f}."
        ctx.last_intent = ZenoIntent.PROFIT_LOSS
        if effective_date:
            ctx.last_date_range = effective_date
        return {
            "intent": intent.value,
            "response": reply,
            "date_range": effective_date.to_dict() if effective_date else None,
            "summary": {"total_income": inc, "total_expenses": exp, "net_profit": net},
            "transactions": txs,
            "follow_up_question": None,
        }

    elif effective_intent == ZenoIntent.VIEW_PENDING_PAYMENTS:
        parties = rows["parties"]
        cust_due = [p for p in parties if str(p.get("type")).upper() == "CUSTOMER" and float(p.get("outstanding_amount") or 0.0) > 0]
        supp_due = [p for p in parties if str(p.get("type")).upper() == "SUPPLIER" and float(p.get("outstanding_amount") or 0.0) > 0]
        reminders = [r for r in rows["payment_reminders"] if str(r.get("status")).upper() in ("PENDING", "OVERDUE")]

        total_rec = sum(float(c.get("outstanding_amount") or 0.0) for c in cust_due)
        total_pay = sum(float(s.get("outstanding_amount") or 0.0) for s in supp_due)
        ask_customers_only = any(k in clean_query.lower() for k in ["customer", "owe me", "owes me", "receivable"])

        if ask_customers_only:
            if not cust_due:
                reply = "No customers currently owe you money."
            else:
                names = ", ".join(f"{c['name']} (₹{float(c['outstanding_amount']):,.2f})" for c in cust_due[:5])
                reply = f"{len(cust_due)} customer(s) owe you ₹{total_rec:,.2f} in total: {names}."
        else:
            if not cust_due and not supp_due and not reminders:
                reply = "You have no pending payments or overdue reminders."
            else:
                reply = (
                    f"Pending Payments: Customer receivables ₹{total_rec:,.2f} ({len(cust_due)} parties), "
                    f"Supplier payables ₹{total_pay:,.2f} ({len(supp_due)} parties), "
                    f"Active reminders: {len(reminders)}."
                )

        pending_tx = [
            {
                "id": f"CUST-{c.get('id')}",
                "type": "RECEIVABLE",
                "category": "INCOME",
                "party": c.get("name") or "",
                "amount": float(c.get("outstanding_amount") or 0.0),
                "payment_mode": "CREDIT",
                "status": "PENDING",
                "date": "",
            }
            for c in cust_due
        ] + (
            [] if ask_customers_only else [
                {
                    "id": f"SUPP-{s.get('id')}",
                    "type": "PAYABLE",
                    "category": "EXPENSE",
                    "party": s.get("name") or "",
                    "amount": float(s.get("outstanding_amount") or 0.0),
                    "payment_mode": "CREDIT",
                    "status": "PENDING",
                    "date": "",
                }
                for s in supp_due
            ]
        )
        ctx.last_intent = ZenoIntent.VIEW_PENDING_PAYMENTS
        return {
            "intent": intent.value,
            "response": reply,
            "date_range": None,
            "summary": {
                "total_receivables": total_rec,
                "total_payables": total_pay,
                "pending_reminders": len(reminders),
            },
            "transactions": pending_tx,
            "follow_up_question": None,
        }

    elif effective_intent == ZenoIntent.VIEW_LEDGER:
        accounts = rows["accounts"]
        q_lower = clean_query.lower()
        matched_party = next((p for p in rows["parties"] if str(p.get("name", "")).lower() in q_lower), None)
        if matched_party:
            pname = str(matched_party["name"])
            party_txs = [t for t in txs if pname.lower() in str(t.get("party", "")).lower()]
            reply = f"Ledger for {pname}: Outstanding ₹{float(matched_party.get('outstanding_amount') or 0.0):,.2f} across {len(party_txs)} transaction(s)."
            txs = party_txs
        else:
            matched_acc = next((a for a in accounts if str(a.get("name", "")).lower() in q_lower), None)
            if matched_acc:
                reply = f"Ledger for {matched_acc['name']} ({matched_acc['code']}): Current balance is ₹{float(matched_acc.get('current_balance') or 0.0):,.2f}."
            else:
                non_zero = [a for a in accounts if abs(float(a.get("current_balance") or 0.0)) > 0.0]
                reply = f"Found {len(accounts)} ledger accounts ({len(non_zero)} with active balances)."
        ctx.last_intent = ZenoIntent.VIEW_LEDGER

    ctx.history.append({"user": query, "assistant": reply})
    return {
        "intent": intent.value,
        "response": reply,
        "date_range": effective_date.to_dict() if effective_date else None,
        "summary": {
            "total_income": total_income,
            "total_expenses": total_expenses,
            "net_amount": total_income - total_expenses,
            "transaction_count": len(txs),
        },
        "transactions": txs,
        "follow_up_question": None,
    }
