"""
XAU PILOT Backend API Service
FastAPI • PostgreSQL • Redis • MT5 WebRequest Bridge
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from datetime import datetime
from typing import Dict, List

from backend.app.config import settings
from backend.app.database import engine, Base
from backend.app.routers import (
    auth, broker_accounts, ea_marketplace,
    ea_installations, ea_bridge, vps_telemetry, hosting,
    developer, ea_review, ea_packages, ea_performance, admin
)

# Initialize database schema tables if not present
try:
    Base.metadata.create_all(bind=engine)
    # Existing deployments may have ea_products from the initial schema but not
    # the developer-release columns added by migration 002. create_all does not
    # alter existing tables, so ensure these additive columns exist on PostgreSQL.
    if settings.DATABASE_URL.startswith(("postgresql://", "postgresql+")):
        from sqlalchemy import text
        with engine.begin() as connection:
            connection.execute(text(
                "ALTER TABLE ea_products "
                "ADD COLUMN IF NOT EXISTS developer_id UUID REFERENCES users(id)"
            ))
            connection.execute(text(
                "ALTER TABLE ea_products "
                "ADD COLUMN IF NOT EXISTS listing_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED'"
            ))
except Exception as e:
    print(f"[XAU-PILOT] Note: DB schema initialization deferred to migrations: {e}")

app = FastAPI(
    title=settings.PROJECT_NAME,
    description="Multi-EA MetaTrader 5 Remote Management, Licensing, and Configuration Engine",
    version=settings.VERSION,
    docs_url="/docs",
    redoc_url="/redoc"
)

# Strict CORS configuration (Fixes Wildcard CORS vulnerability)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "Accept", "X-Installation-Id"],
)

# Mount Routers
app.include_router(auth.router, prefix=settings.API_V1_STR)
app.include_router(broker_accounts.router, prefix=settings.API_V1_STR)
app.include_router(ea_marketplace.router, prefix=settings.API_V1_STR)
app.include_router(ea_installations.router, prefix=settings.API_V1_STR)
app.include_router(vps_telemetry.router, prefix=settings.API_V1_STR)
app.include_router(hosting.router, prefix=settings.API_V1_STR)
app.include_router(developer.router, prefix=settings.API_V1_STR)
app.include_router(ea_review.router, prefix=settings.API_V1_STR)
app.include_router(ea_packages.router, prefix=settings.API_V1_STR)
app.include_router(ea_performance.router)
app.include_router(admin.router, prefix=settings.API_V1_STR)
app.include_router(ea_bridge.router, prefix=settings.EA_V1_STR)

@app.get("/health")
def health_check():
    # Liveness only: this endpoint does not claim database or MT5 readiness.
    return {
        "status": "alive",
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "timestamp_utc": datetime.utcnow().isoformat()
    }

@app.get("/ready")
def readiness_check():
    from sqlalchemy import text
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
    except Exception as exc:
        from fastapi import HTTPException
        raise HTTPException(status_code=503, detail="Database is not ready") from exc
    return {"status": "ready", "database": "connected", "mt5": "not_integrated"}

# Real-time WebSocket connection manager
class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, List[WebSocket]] = {}

    async def connect(self, account_id: str, websocket: WebSocket):
        await websocket.accept()
        if account_id not in self.active_connections:
            self.active_connections[account_id] = []
        self.active_connections[account_id].append(websocket)

    def disconnect(self, account_id: str, websocket: WebSocket):
        if account_id in self.active_connections:
            self.active_connections[account_id].remove(websocket)

    async def broadcast_to_account(self, account_id: str, message: dict):
        if account_id in self.active_connections:
            for connection in self.active_connections[account_id]:
                try:
                    await connection.send_json(message)
                except Exception:
                    pass

ws_manager = ConnectionManager()

@app.websocket("/ws/telemetry/{account_id}")
async def telemetry_websocket(websocket: WebSocket, account_id: str):
    await ws_manager.connect(account_id, websocket)
    try:
        while True:
            data = await websocket.receive_text()
            # Echo or process incoming client commands
            await websocket.send_json({"type": "PONG", "received_at": datetime.utcnow().isoformat()})
    except WebSocketDisconnect:
        ws_manager.disconnect(account_id, websocket)
