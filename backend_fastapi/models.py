from sqlalchemy import Column, Integer, String, Float, Boolean, ForeignKey, DateTime
from sqlalchemy.orm import relationship
from datetime import datetime
from database import Base

class Account(Base):
    __tablename__ = "accounts"

    id = Column(Integer, primary_key=True, index=True)
    code = Column(String, unique=True, index=True)
    name = Column(String, index=True)
    category = Column(String)  # ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    subcategory = Column(String)
    opening_balance = Column(Float, default=0.0)
    current_balance = Column(Float, default=0.0)
    is_system = Column(Boolean, default=False)

class Party(Base):
    __tablename__ = "parties"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, index=True)
    type = Column(String)  # CUSTOMER, SUPPLIER
    phone = Column(String, default="")
    email = Column(String, default="")
    address = Column(String, default="")
    gstin = Column(String, default="")
    state_code = Column(String, default="27")
    opening_balance = Column(Float, default=0.0)
    outstanding_amount = Column(Float, default=0.0)
    is_active = Column(Boolean, default=True)

class Product(Base):
    __tablename__ = "products"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, index=True)
    code_sku = Column(String, unique=True, index=True)
    category = Column(String)
    purchase_price = Column(Float)
    sales_price = Column(Float)
    gst_rate = Column(Float, default=18.0)
    hsn_code = Column(String, default="")
    current_stock = Column(Float, default=0.0)
    min_stock_alert = Column(Float, default=5.0)
    unit = Column(String, default="Pcs")

class SalesInvoice(Base):
    __tablename__ = "sales_invoices"

    id = Column(Integer, primary_key=True, index=True)
    invoice_number = Column(String, unique=True, index=True)
    customer_id = Column(Integer, ForeignKey("parties.id"))
    customer_name = Column(String)
    date = Column(DateTime, default=datetime.utcnow)
    subtotal = Column(Float)
    cgst = Column(Float, default=0.0)
    sgst = Column(Float, default=0.0)
    igst = Column(Float, default=0.0)
    total_amount = Column(Float)
    is_interstate = Column(Boolean, default=False)
    status = Column(String, default="UNPAID")
    payment_mode = Column(String, default="CREDIT")

class PurchaseBill(Base):
    __tablename__ = "purchase_bills"

    id = Column(Integer, primary_key=True, index=True)
    bill_number = Column(String, unique=True, index=True)
    supplier_id = Column(Integer, ForeignKey("parties.id"))
    supplier_name = Column(String)
    date = Column(DateTime, default=datetime.utcnow)
    subtotal = Column(Float)
    cgst = Column(Float, default=0.0)
    sgst = Column(Float, default=0.0)
    igst = Column(Float, default=0.0)
    total_amount = Column(Float)
    is_interstate = Column(Boolean, default=False)
    status = Column(String, default="UNPAID")
    payment_mode = Column(String, default="CREDIT")

class JournalEntry(Base):
    __tablename__ = "journal_entries"

    id = Column(Integer, primary_key=True, index=True)
    entry_number = Column(String, unique=True, index=True)
    date = Column(DateTime, default=datetime.utcnow)
    reference = Column(String, default="")
    narration = Column(String)
    total_debit = Column(Float)
    total_credit = Column(Float)
    source_module = Column(String, default="MANUAL")

class JournalLine(Base):
    __tablename__ = "journal_lines"

    id = Column(Integer, primary_key=True, index=True)
    journal_entry_id = Column(Integer, ForeignKey("journal_entries.id"))
    account_id = Column(Integer, ForeignKey("accounts.id"))
    account_name = Column(String)
    is_debit = Column(Boolean)
    amount = Column(Float)
    description = Column(String, default="")

class PaymentVoucher(Base):
    __tablename__ = "payment_vouchers"

    id = Column(Integer, primary_key=True, index=True)
    voucher_number = Column(String, unique=True)
    type = Column(String)  # RECEIPT, PAYMENT
    party_id = Column(Integer, ForeignKey("parties.id"))
    party_name = Column(String)
    account_id = Column(Integer, ForeignKey("accounts.id"))
    account_name = Column(String)
    amount = Column(Float)
    payment_mode = Column(String, default="BANK")
    date = Column(DateTime, default=datetime.utcnow)
    reference = Column(String, default="")

class PaymentReminder(Base):
    __tablename__ = "payment_reminders"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String)
    party_id = Column(Integer, ForeignKey("parties.id"))
    party_name = Column(String)
    amount = Column(Float)
    due_date = Column(DateTime)
    type = Column(String)
    status = Column(String, default="PENDING")


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, nullable=False)
    email = Column(String, unique=True, index=True, nullable=False)
    password_hash = Column(String, nullable=False)
    company_name = Column(String, default="My Enterprise")
    gstin = Column(String, default="")
    state_code = Column(String, default="27")
    state_name = Column(String, default="Maharashtra")
    is_verified = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)


class EmailOtp(Base):
    __tablename__ = "email_otps"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String, index=True, nullable=False)
    otp_hash = Column(String, nullable=False)
    otp_salt = Column(String, nullable=False)
    token_hash = Column(String, index=True, nullable=True)
    purpose = Column(String, default="REGISTRATION")
    created_at = Column(DateTime, default=datetime.utcnow)
    expires_at = Column(DateTime, nullable=False)
    last_sent_at = Column(DateTime, default=datetime.utcnow)
    attempts_left = Column(Integer, default=5)
    is_used = Column(Boolean, default=False)
    is_verified = Column(Boolean, default=False)

