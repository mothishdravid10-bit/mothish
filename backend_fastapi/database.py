import os
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import declarative_base, sessionmaker

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./zeno_accounting.db").strip() or "sqlite:///./zeno_accounting.db"

_engine_kwargs = {"pool_pre_ping": True}
if DATABASE_URL.startswith("sqlite"):
    _engine_kwargs["connect_args"] = {"check_same_thread": False}

engine = create_engine(DATABASE_URL, **_engine_kwargs)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def run_safe_migrations(target_engine=engine) -> None:
    """
    Safely creates missing tables and adds missing columns on existing tables
    without dropping tables or deleting existing user/accounting records.
    """
    Base.metadata.create_all(bind=target_engine)
    inspector = inspect(target_engine)

    desired_columns = {
        "users": {
            "company_name": "VARCHAR DEFAULT 'My Enterprise'",
            "gstin": "VARCHAR DEFAULT ''",
            "state_code": "VARCHAR DEFAULT '27'",
            "state_name": "VARCHAR DEFAULT 'Maharashtra'",
            "is_verified": "BOOLEAN NOT NULL DEFAULT 0",
            "created_at": "DATETIME",
        },
        "email_otps": {
            "token_hash": "VARCHAR",
            "purpose": "VARCHAR DEFAULT 'REGISTRATION'",
            "last_sent_at": "DATETIME",
            "attempts_left": "INTEGER DEFAULT 5",
            "is_used": "BOOLEAN DEFAULT 0",
            "is_verified": "BOOLEAN DEFAULT 0",
        },
    }

    with target_engine.begin() as conn:
        for table_name, columns_map in desired_columns.items():
            if not inspector.has_table(table_name):
                continue
            existing_cols = {col["name"] for col in inspector.get_columns(table_name)}
            for col_name, col_ddl in columns_map.items():
                if col_name not in existing_cols:
                    conn.execute(text(f"ALTER TABLE {table_name} ADD COLUMN {col_name} {col_ddl}"))
