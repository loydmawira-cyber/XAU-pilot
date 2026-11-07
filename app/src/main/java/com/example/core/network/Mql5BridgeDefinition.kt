package com.example.core.network

object Mql5BridgeDefinition {

    const val BRIDGE_FILE_NAME = "XauPilotBridge.mqh"
    const val BRIDGE_VERSION = "2.4.0"

    val BRIDGE_MQH_SOURCE: String = """
//+------------------------------------------------------------------+
//|                                              XauPilotBridge.mqh  |
//|                       XAU PILOT Automated MT5 Remote Control     |
//|                   Copyright 2026 XAU PILOT. All rights reserved. |
//+------------------------------------------------------------------+
#property copyright "Copyright 2026, XAU PILOT"
#property link      "https://xaupilot.com"
#property version   "2.40"
#property strict

#define XAUPILOT_BRIDGE_VER "2.4.0"
#define XAUPILOT_DEFAULT_TIMEOUT_MS 5000
#define XAUPILOT_MAX_BACKOFF_SEC 60

enum ENUM_XAUPILOT_STATE {
   XAUPILOT_STATE_UNINITIALIZED = 0,
   XAUPILOT_STATE_ACTIVE        = 1,
   XAUPILOT_STATE_PAUSED        = 2,
   XAUPILOT_STATE_STOPPED       = 3,
   XAUPILOT_STATE_EMERGENCY     = 4
};

struct SXauPilotConfig {
   string   backend_url;
   string   installation_id;
   string   auth_token;
   ulong    magic_number;
   int      polling_interval_sec;
   bool     allow_auto_trading;
   int      consecutive_failures;
   int      current_backoff_sec;
};

SXauPilotConfig     g_xau_config;
ENUM_XAUPILOT_STATE g_xau_state = XAUPILOT_STATE_UNINITIALIZED;
int                 g_xau_config_version = 1;
datetime            g_xau_last_heartbeat = 0;
datetime            g_xau_last_poll = 0;

// Host EA accessible dynamic variables
double   XauPilot_ActiveLotSize        = 0.10;
double   XauPilot_ActiveRiskPercent    = 1.5;
int      XauPilot_ActiveStopLossPoints = 120;
int      XauPilot_ActiveTakeProfitPoints = 240;
int      XauPilot_ActiveMaxTrades      = 3;
bool     XauPilot_ActiveTrailingEnabled = true;
int      XauPilot_ActiveTrailingStart  = 60;
int      XauPilot_ActiveTrailingStep   = 20;
bool     XauPilot_ActiveBreakEven      = true;
int      XauPilot_ActiveBreakEvenTrigger = 50;
int      XauPilot_ActiveMaxSpread      = 35;
string   XauPilot_ActiveSession        = "LONDON_NY";

bool XauPilot_Init(const string backend_url,
                   const string installation_id,
                   const string auth_token,
                   const ulong  magic_number,
                   const int    timer_sec = 15);
void XauPilot_OnTimer();
void XauPilot_ReportTradeEvent(const MqlTradeTransaction &trans,
                              const MqlTradeRequest &request,
                              const MqlTradeResult &result);
void XauPilot_Deinit(const int reason);
""".trimIndent()
}
