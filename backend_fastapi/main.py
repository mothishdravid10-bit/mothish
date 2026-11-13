from fastapi import FastAPI, Depends, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import List, Optional
import os
import models
from auth_service import (
    AuthServiceError,
    get_current_user_from_token,
    login_user as auth_login_user,
    register_user as auth_register_user,
    request_password_reset as auth_request_password_reset,
    send_verification_otp as auth_send_otp,
    verify_email_otp as auth_verify_otp,
    verify_email_token as auth_verify_email_token,
)
from database import engine, get_db, run_safe_migrations
from email_service import is_smtp_configured
from pydantic import BaseModel
from datetime import datetime

run_safe_migrations(engine)

app = FastAPI(
    title="ZENO AI Accounting API",
    description="Backend API for ZENO AI with Indian GST and Double-Entry Engine",
    version="1.0.0"
)

cors_origins_raw = os.getenv("CORS_ALLOWED_ORIGINS", "*").strip()
cors_origins = [o.strip() for o in cors_origins_raw.split(",") if o.strip()] or ["*"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=cors_origins != ["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Schemas
class AccountCreate(BaseModel):
    code: str
    name: str
    category: str
    subcategory: str
    opening_balance: float = 0.0

class PartyCreate(BaseModel):
    name: str
    type: str  # CUSTOMER or SUPPLIER
    phone: str = ""
    email: str = ""
    address: str = ""
    gstin: str = ""
    state_code: str = "27"
    opening_balance: float = 0.0

class ProductCreate(BaseModel):
    name: str
    code_sku: str
    category: str
    purchase_price: float
    sales_price: float
    gst_rate: float = 18.0
    hsn_code: str = ""
    current_stock: float = 0.0
    min_stock_alert: float = 5.0

class JournalLineItem(BaseModel):
    account_id: int
    account_name: str
    is_debit: bool
    amount: float
    description: str = ""

class JournalEntryCreate(BaseModel):
    narration: str
    reference: str = ""
    lines: List[JournalLineItem]

class VoiceQueryRequest(BaseModel):
    query: str
    session_id: str = "default"
    tz_offset_minutes: Optional[int] = None
    reset_context: bool = False


@app.get("/")
def root():
    return {"status": "online", "system": "ZENO AI Accounting Engine", "version": "1.0.0"}


@app.get("/api/health")
def health_check(db: Session = Depends(get_db)):
    db.execute(text("SELECT 1"))
    return {
        "status": "ok",
        "database": "connected",
        "smtp_configured": is_smtp_configured(),
    }

# Dashboard
@app.get("/api/dashboard/metrics")
def get_metrics(db: Session = Depends(get_db)):
    sales = db.query(models.SalesInvoice).all()
    bills = db.query(models.PurchaseBill).all()
    accounts = db.query(models.Account).all()
    customers = db.query(models.Party).filter(models.Party.type == "CUSTOMER").all()
    suppliers = db.query(models.Party).filter(models.Party.type == "SUPPLIER").all()
    products = db.query(models.Product).all()

    total_sales = sum(s.total_amount for s in sales)
    total_purchases = sum(b.total_amount for b in bills)
    total_receivables = sum(c.outstanding_amount for c in customers)
    total_payables = sum(s.outstanding_amount for s in suppliers)
    inventory_val = sum(p.current_stock * p.purchase_price for p in products)

    output_gst = sum(s.cgst + s.sgst + s.igst for s in sales)
    input_gst = sum(b.cgst + b.sgst + b.igst for b in bills)
    net_gst = max(0.0, output_gst - input_gst)

    return {
        "total_sales": total_sales,
        "total_purchases": total_purchases,
        "total_receivables": total_receivables,
        "total_payables": total_payables,
        "inventory_valuation": inventory_val,
        "net_gst_payable": net_gst,
        "status": "Balanced Double-Entry Books"
    }

# Accounts
@app.get("/api/accounts")
def get_accounts(db: Session = Depends(get_db)):
    return db.query(models.Account).all()

@app.post("/api/accounts")
def create_account(acc: AccountCreate, db: Session = Depends(get_db)):
    db_acc = models.Account(
        code=acc.code,
        name=acc.name,
        category=acc.category,
        subcategory=acc.subcategory,
        opening_balance=acc.opening_balance,
        current_balance=acc.opening_balance
    )
    db.add(db_acc)
    db.commit()
    db.refresh(db_acc)
    return db_acc

# Parties
@app.get("/api/parties")
def get_parties(type: Optional[str] = None, db: Session = Depends(get_db)):
    q = db.query(models.Party)
    if type:
        q = q.filter(models.Party.type == type)
    return q.all()

@app.post("/api/parties")
def create_party(party: PartyCreate, db: Session = Depends(get_db)):
    db_p = models.Party(
        name=party.name,
        type=party.type,
        phone=party.phone,
        email=party.email,
        address=party.address,
        gstin=party.gstin,
        state_code=party.state_code,
        opening_balance=party.opening_balance,
        outstanding_amount=party.opening_balance
    )
    db.add(db_p)
    db.commit()
    db.refresh(db_p)
    return db_p

# Products
@app.get("/api/products")
def get_products(db: Session = Depends(get_db)):
    return db.query(models.Product).all()

@app.post("/api/products")
def create_product(p: ProductCreate, db: Session = Depends(get_db)):
    db_p = models.Product(**p.dict())
    db.add(db_p)
    db.commit()
    db.refresh(db_p)
    return db_p

# Journal Entries with Double-Entry Balance Check
@app.post("/api/journal")
def create_journal(entry: JournalEntryCreate, db: Session = Depends(get_db)):
    total_dr = sum(l.amount for l in entry.lines if l.is_debit)
    total_cr = sum(l.amount for l in entry.lines if not l.is_debit)

    if abs(total_dr - total_cr) > 0.01:
        raise HTTPException(
            status_code=400,
            detail=f"Double-entry balancing rule violated: Total Debit ({total_dr}) != Total Credit ({total_cr})"
        )

    db_entry = models.JournalEntry(
        entry_number=f"JRN-{int(datetime.utcnow().timestamp())}",
        reference=entry.reference,
        narration=entry.narration,
        total_debit=total_dr,
        total_credit=total_cr
    )
    db.add(db_entry)
    db.commit()
    db.refresh(db_entry)

    for line in entry.lines:
        db_line = models.JournalLine(
            journal_entry_id=db_entry.id,
            account_id=line.account_id,
            account_name=line.account_name,
            is_debit=line.is_debit,
            amount=line.amount,
            description=line.description
        )
        db.add(db_line)

        # Update account balance
        acc = db.query(models.Account).filter(models.Account.id == line.account_id).first()
        if acc:
            acc.current_balance += (line.amount if line.is_debit else -line.amount)

    db.commit()
    return {"status": "success", "journal_id": db_entry.id, "balanced": True}

# ZENO Voice Assistant Query Endpoint
@app.post("/api/ai/zeno/chat")
def zeno_chat(req: VoiceQueryRequest, db: Session = Depends(get_db)):
    from zeno_assistant import process_zeno_chat
    return process_zeno_chat(
        query=req.query,
        db=db,
        session_id=req.session_id,
        tz_offset_minutes=req.tz_offset_minutes,
        reset_context=req.reset_context,
    )


# === AUTHENTICATION & EMAIL OTP / TOKEN VERIFICATION ===
class SendOtpRequest(BaseModel):
    email: str


class VerifyOtpRequest(BaseModel):
    email: str
    otp: str


class VerifyTokenRequest(BaseModel):
    token: str


class RegisterRequest(BaseModel):
    name: str
    email: str
    password: str
    confirm_password: Optional[str] = None
    company_name: str = "My Enterprise"
    gstin: str = ""


class LoginRequest(BaseModel):
    email: str
    password: str


def _raise_http(err: AuthServiceError):
    raise HTTPException(status_code=err.status_code, detail=err.detail)


@app.post("/api/auth/send-otp")
@app.post("/api/auth/resend-verification")
def send_verification_otp(req: SendOtpRequest, db: Session = Depends(get_db)):
    try:
        return auth_send_otp(req.email, db)
    except AuthServiceError as err:
        _raise_http(err)


@app.post("/api/auth/verify-otp")
def verify_email_otp(req: VerifyOtpRequest, db: Session = Depends(get_db)):
    try:
        return auth_verify_otp(req.email, req.otp, db)
    except AuthServiceError as err:
        _raise_http(err)


@app.get("/api/auth/verify-email", response_class=HTMLResponse)
def verify_email_link(token: str = Query(...), db: Session = Depends(get_db)):
    try:
        res = auth_verify_email_token(token, db)
        return HTMLResponse(
            content=(
                "<html><body style='font-family:sans-serif;padding:32px;text-align:center;'>"
                "<h2 style='color:#059669;'>ZENO AI Email Verified</h2>"
                f"<p>{res['email']} has been verified. Return to the ZENO AI app to complete login.</p>"
                "</body></html>"
            ),
            status_code=200,
        )
    except AuthServiceError as err:
        return HTMLResponse(
            content=(
                "<html><body style='font-family:sans-serif;padding:32px;text-align:center;'>"
                "<h2 style='color:#DC2626;'>Verification Failed</h2>"
                f"<p>{err.detail}</p>"
                "</body></html>"
            ),
            status_code=err.status_code,
        )


@app.post("/api/auth/verify-email")
def verify_email_token_api(req: VerifyTokenRequest, db: Session = Depends(get_db)):
    try:
        return auth_verify_email_token(req.token, db)
    except AuthServiceError as err:
        _raise_http(err)


@app.post("/api/auth/register")
def register_user(req: RegisterRequest, db: Session = Depends(get_db)):
    try:
        return auth_register_user(
            name=req.name,
            email=req.email,
            password=req.password,
            confirm_password=req.confirm_password,
            company_name=req.company_name,
            gstin=req.gstin,
            db=db,
        )
    except AuthServiceError as err:
        _raise_http(err)


@app.post("/api/auth/login")
def login_user(req: LoginRequest, db: Session = Depends(get_db)):
    try:
        return auth_login_user(req.email, req.password, db)
    except AuthServiceError as err:
        _raise_http(err)


@app.get("/api/auth/me")
def current_user_profile(
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db),
):
    try:
        return get_current_user_from_token(authorization, db)
    except AuthServiceError as err:
        _raise_http(err)


@app.post("/api/auth/forgot-password")
def forgot_password(req: SendOtpRequest, db: Session = Depends(get_db)):
    try:
        return auth_request_password_reset(req.email, db)
    except AuthServiceError as err:
        _raise_http(err)


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=os.getenv("ENV", "dev") == "dev")
