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

// Bridge Runtime States
enum ENUM_XAUPILOT_STATE {
   XAUPILOT_STATE_UNINITIALIZED = 0,
   XAUPILOT_STATE_ACTIVE        = 1,
   XAUPILOT_STATE_PAUSED        = 2,
   XAUPILOT_STATE_STOPPED       = 3,
   XAUPILOT_STATE_EMERGENCY     = 4
};

// Internal Bridge Configuration
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

// Global Context
SXauPilotConfig     g_xau_config;
ENUM_XAUPILOT_STATE g_xau_state = XAUPILOT_STATE_UNINITIALIZED;
int                 g_xau_config_version = 1;
datetime            g_xau_last_heartbeat = 0;
datetime            g_xau_last_poll = 0;

// Dynamic EA Runtime Applied Parameters (Accessible by Host EA)
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

// Forward declarations
bool XauPilot_CheckLicense();
void XauPilot_StartTimer();
void XauPilot_SendStatus();
void XauPilot_ProcessCommands();
void XauPilot_AcknowledgeCommand(const string cmd_id, const string status, const string err_msg = "");
void XauPilot_LoadConfiguration();
void XauPilot_AcknowledgeConfiguration(const int version, const string status, const string reason = "");
void XauPilot_SendPositions();
bool XauPilot_ValidateParameters(double lot, int sl, int tp, string &out_error);
int  XauPilot_HttpPost(const string endpoint, const string json_body, string &out_response);
int  XauPilot_HttpGet(const string endpoint, string &out_response);
string XauPilot_JsonGetString(const string json, const string key);
double XauPilot_JsonGetDouble(const string json, const string key, const double def_val = 0.0);
int    XauPilot_JsonGetInt(const string json, const string key, const int def_val = 0);

//+------------------------------------------------------------------+
//| XauPilot_Init: Call inside Host EA OnInit()                      |
//+------------------------------------------------------------------+
bool XauPilot_Init(const string backend_url,
                   const string installation_id,
                   const string auth_token,
                   const ulong  magic_number,
                   const int    timer_sec = 15)
{
   PrintFormat("[XAU-PILOT] Initializing Bridge v%s for Installation %s (Magic: %I64u)",
               XAUPILOT_BRIDGE_VER, installation_id, magic_number);

   g_xau_config.backend_url = backend_url;
   g_xau_config.installation_id = installation_id;
   g_xau_config.auth_token = auth_token;
   g_xau_config.magic_number = magic_number;
   g_xau_config.polling_interval_sec = (timer_sec < 5) ? 5 : timer_sec;
   g_xau_config.allow_auto_trading = (bool)TerminalInfoInteger(TERMINAL_TRADE_ALLOWED);
   g_xau_config.consecutive_failures = 0;
   g_xau_config.current_backoff_sec = g_xau_config.polling_interval_sec;

   if(!XauPilot_CheckLicense()) {
      Print("[XAU-PILOT] [SECURITY] License validation failed during initialization. Halting EA.");
      g_xau_state = XAUPILOT_STATE_STOPPED;
      return false;
   }

   XauPilot_StartTimer();
   XauPilot_OnTimer(); // Immediate initial sync
   g_xau_state = XAUPILOT_STATE_ACTIVE;
   Print("[XAU-PILOT] Initialization successful. Active remote control established.");
   return true;
}

//+------------------------------------------------------------------+
//| XauPilot_StartTimer: Registers timer                             |
//+------------------------------------------------------------------+
void XauPilot_StartTimer() {
   EventSetTimer(g_xau_config.polling_interval_sec);
}

//+------------------------------------------------------------------+
//| XauPilot_OnTimer: Call inside Host EA OnTimer()                  |
//+------------------------------------------------------------------+
void XauPilot_OnTimer() {
   datetime now = TimeCurrent();
   if(now - g_xau_last_poll < g_xau_config.current_backoff_sec) return;
   g_xau_last_poll = now;

   // 1. Telemetry Status Heartbeat
   XauPilot_SendStatus();

   // 2. Fetch & Execute Remote Commands (PAUSE, RESUME, EMERGENCY_STOP)
   XauPilot_ProcessCommands();

   // 3. Check for Pending Desired Configuration Updates
   XauPilot_LoadConfiguration();

   // 4. Send Open Positions Snapshot
   XauPilot_SendPositions();
}

//+------------------------------------------------------------------+
//| XauPilot_CheckLicense: Verifies active entitlement               |
//+------------------------------------------------------------------+
bool XauPilot_CheckLicense() {
   string payload = StringFormat("{\"installation_id\":\"%s\",\"account\":%I64d}",
                                 g_xau_config.installation_id, AccountInfoInteger(ACCOUNT_LOGIN));
   string response;
   int code = XauPilot_HttpPost("/ea/v1/license/validate", payload, response);
   if(code == 200) {
      string status = XauPilot_JsonGetString(response, "license_status");
      return (status == "ACTIVE");
   }
   return false;
}

//+------------------------------------------------------------------+
//| XauPilot_SendStatus: Heartbeat reporting real terminal state     |
//+------------------------------------------------------------------+
void XauPilot_SendStatus() {
   string status_str = "RUNNING";
   if(g_xau_state == XAUPILOT_STATE_PAUSED) status_str = "PAUSED";
   else if(g_xau_state == XAUPILOT_STATE_STOPPED) status_str = "STOPPED";
   else if(g_xau_state == XAUPILOT_STATE_EMERGENCY) status_str = "EMERGENCY";

   string payload = StringFormat("{\"installation_id\":\"%s\",\"status\":\"%s\",\"balance\":%.2f,\"equity\":%.2f,\"free_margin\":%.2f,\"config_version\":%d}",
                                 g_xau_config.installation_id,
                                 status_str,
                                 AccountInfoDouble(ACCOUNT_BALANCE),
                                 AccountInfoDouble(ACCOUNT_EQUITY),
                                 AccountInfoDouble(ACCOUNT_MARGIN_FREE),
                                 g_xau_config_version);
   string resp;
   int code = XauPilot_HttpPost("/ea/v1/heartbeat", payload, resp);
   if(code == 200) {
      g_xau_config.consecutive_failures = 0;
      g_xau_config.current_backoff_sec = g_xau_config.polling_interval_sec;
   } else {
      g_xau_config.consecutive_failures++;
      g_xau_config.current_backoff_sec = MathMin(XAUPILOT_MAX_BACKOFF_SEC,
                                                 g_xau_config.polling_interval_sec * (1 << MathMin(g_xau_config.consecutive_failures, 4)));
      PrintFormat("[XAU-PILOT] [WARN] Connection failure #%d (Code %d). Backing off for %ds",
                  g_xau_config.consecutive_failures, code, g_xau_config.current_backoff_sec);
   }
}

//+------------------------------------------------------------------+
//| XauPilot_ProcessCommands: Reads and applies remote commands      |
//+------------------------------------------------------------------+
void XauPilot_ProcessCommands() {
   string resp;
   string endpoint = StringFormat("/ea/v1/commands/next?installation_id=%s", g_xau_config.installation_id);
   int code = XauPilot_HttpGet(endpoint, resp);
   if(code != 200) return;

   string cmd_id = XauPilot_JsonGetString(resp, "id");
   if(cmd_id == "") return;

   string cmd_type = XauPilot_JsonGetString(resp, "type");
   string policy = XauPilot_JsonGetString(resp, "policy");
   PrintFormat("[XAU-PILOT] Received remote command: %s (ID: %s, Policy: %s)", cmd_type, cmd_id, policy);

   if(cmd_type == "PAUSE") {
      g_xau_state = XAUPILOT_STATE_PAUSED;
      XauPilot_AcknowledgeCommand(cmd_id, "APPLIED");
   } else if(cmd_type == "RESUME") {
      g_xau_state = XAUPILOT_STATE_ACTIVE;
      XauPilot_AcknowledgeCommand(cmd_id, "APPLIED");
   } else if(cmd_type == "STOP") {
      g_xau_state = XAUPILOT_STATE_STOPPED;
      XauPilot_AcknowledgeCommand(cmd_id, "APPLIED");
   } else if(cmd_type == "EMERGENCY_STOP") {
      g_xau_state = XAUPILOT_STATE_EMERGENCY;
      if(policy == "CLOSE_ALL_MARKET_ORDERS") {
         // Close all positions matching this magic number
         for(int i = PositionsTotal() - 1; i >= 0; i--) {
            ulong ticket = PositionGetTicket(i);
            if(PositionGetInteger(POSITION_MAGIC) == (long)g_xau_config.magic_number) {
               MqlTradeRequest req;
               MqlTradeResult res;
               ZeroMemory(req);
               ZeroMemory(res);
               req.action = TRADE_ACTION_DEAL;
               req.position = ticket;
               req.symbol = PositionGetString(POSITION_SYMBOL);
               req.volume = PositionGetDouble(POSITION_VOLUME);
               req.deviation = 10;
               req.type = (PositionGetInteger(POSITION_TYPE) == POSITION_TYPE_BUY) ? ORDER_TYPE_SELL : ORDER_TYPE_BUY;
               req.price = (req.type == ORDER_TYPE_SELL) ? SymbolInfoDouble(req.symbol, SYMBOL_BID) : SymbolInfoDouble(req.symbol, SYMBOL_ASK);
               OrderSend(req, res);
            }
         }
      }
      XauPilot_AcknowledgeCommand(cmd_id, "APPLIED");
   } else {
      XauPilot_AcknowledgeCommand(cmd_id, "REJECTED", "Unknown command type");
   }
}

//+------------------------------------------------------------------+
//| XauPilot_AcknowledgeCommand: Confirms command execution to cloud |
//+------------------------------------------------------------------+
void XauPilot_AcknowledgeCommand(const string cmd_id, const string status, const string err_msg = "") {
   string payload = StringFormat("{\"status\":\"%s\",\"timestamp\":%d,\"error_message\":\"%s\"}",
                                 status, (int)TimeCurrent(), err_msg);
   string resp;
   XauPilot_HttpPost("/ea/v1/commands/" + cmd_id + "/ack", payload, resp);
}

//+------------------------------------------------------------------+
//| XauPilot_LoadConfiguration: Syncs remote parameters dynamically  |
//+------------------------------------------------------------------+
void XauPilot_LoadConfiguration() {
   string resp;
   string endpoint = StringFormat("/ea/v1/configuration/next?installation_id=%s", g_xau_config.installation_id);
   int code = XauPilot_HttpGet(endpoint, resp);
   if(code != 200) return;

   bool has_update = (StringFind(resp, "\"has_update\":true") >= 0 || StringFind(resp, "\"has_update\": true") >= 0);
   if(!has_update) return;

   int new_version = XauPilot_JsonGetInt(resp, "version", g_xau_config_version);
   if(new_version <= g_xau_config_version) return;

   PrintFormat("[XAU-PILOT] New configuration version #%d detected. Validating against broker constraints...", new_version);

   double candidate_lot = XauPilot_JsonGetDouble(resp, "lot_size", XauPilot_ActiveLotSize);
   int candidate_sl = XauPilot_JsonGetInt(resp, "stop_loss_points", XauPilot_ActiveStopLossPoints);
   int candidate_tp = XauPilot_JsonGetInt(resp, "take_profit_points", XauPilot_ActiveTakeProfitPoints);

   string validation_err = "";
   if(!XauPilot_ValidateParameters(candidate_lot, candidate_sl, candidate_tp, validation_err)) {
      PrintFormat("[XAU-PILOT] [REJECTED] Config v#%d rejected: %s", new_version, validation_err);
      XauPilot_AcknowledgeConfiguration(new_version, "REJECTED", validation_err);
      return;
   }

   // Apply parameters to live runtime
   XauPilot_ActiveLotSize = candidate_lot;
   XauPilot_ActiveStopLossPoints = candidate_sl;
   XauPilot_ActiveTakeProfitPoints = candidate_tp;
   XauPilot_ActiveRiskPercent = XauPilot_JsonGetDouble(resp, "risk_percent", XauPilot_ActiveRiskPercent);
   XauPilot_ActiveMaxTrades = XauPilot_JsonGetInt(resp, "max_open_trades", XauPilot_ActiveMaxTrades);
   XauPilot_ActiveTrailingEnabled = (XauPilot_JsonGetString(resp, "trailing_stop_enabled") == "true");
   XauPilot_ActiveBreakEven = (XauPilot_JsonGetString(resp, "break_even_enabled") == "true");
   XauPilot_ActiveMaxSpread = XauPilot_JsonGetInt(resp, "max_spread_points", XauPilot_ActiveMaxSpread);
   XauPilot_ActiveSession = XauPilot_JsonGetString(resp, "trading_session");
   if(XauPilot_ActiveSession == "") XauPilot_ActiveSession = "LONDON_NY";

   g_xau_config_version = new_version;
   PrintFormat("[XAU-PILOT] [APPLIED] Configuration v#%d successfully activated: Lot=%.2f, SL=%d, TP=%d",
               new_version, XauPilot_ActiveLotSize, XauPilot_ActiveStopLossPoints, XauPilot_ActiveTakeProfitPoints);

   XauPilot_AcknowledgeConfiguration(new_version, "APPLIED");
}

//+------------------------------------------------------------------+
//| XauPilot_ValidateParameters: Checks broker symbol limitations    |
//+------------------------------------------------------------------+
bool XauPilot_ValidateParameters(double lot, int sl, int tp, string &out_error) {
   string sym = _Symbol;
   double min_lot = SymbolInfoDouble(sym, SYMBOL_VOLUME_MIN);
   double max_lot = SymbolInfoDouble(sym, SYMBOL_VOLUME_MAX);
   double lot_step = SymbolInfoDouble(sym, SYMBOL_VOLUME_STEP);

   if(lot < min_lot) {
      out_error = StringFormat("Lot size %.2f is below broker minimum of %.2f", lot, min_lot);
      return false;
   }
   if(lot > max_lot) {
      out_error = StringFormat("Lot size %.2f exceeds broker maximum of %.2f", lot, max_lot);
      return false;
   }
   if(sl < 10) {
      out_error = "Stop loss points must be at least 10 points (1 pip)";
      return false;
   }
   return true;
}

//+------------------------------------------------------------------+
//| XauPilot_AcknowledgeConfiguration: Confirms applied version      |
//+------------------------------------------------------------------+
void XauPilot_AcknowledgeConfiguration(const int version, const string status, const string reason = "") {
   string applied_json = StringFormat("{\"lot_size\":\"%.2f\",\"stop_loss_points\":\"%d\",\"take_profit_points\":\"%d\"}",
                                      XauPilot_ActiveLotSize, XauPilot_ActiveStopLossPoints, XauPilot_ActiveTakeProfitPoints);

   string payload = StringFormat("{\"installation_id\":\"%s\",\"version\":%d,\"status\":\"%s\",\"applied_values\":%s,\"rejection_reason\":\"%s\"}",
                                 g_xau_config.installation_id, version, status, applied_json, reason);
   string resp;
   XauPilot_HttpPost("/ea/v1/configuration/ack", payload, resp);
}

//+------------------------------------------------------------------+
//| XauPilot_SendPositions: Transmits open positions snapshot        |
//+------------------------------------------------------------------+
void XauPilot_SendPositions() {
   string pos_json = "[";
   int count = 0;

   for(int i = 0; i < PositionsTotal(); i++) {
      ulong ticket = PositionGetTicket(i);
      if(PositionGetInteger(POSITION_MAGIC) == (long)g_xau_config.magic_number) {
         if(count > 0) pos_json += ",";
         pos_json += StringFormat("{\"ticket\":%I64u,\"symbol\":\"%s\",\"direction\":\"%s\",\"lot_size\":%.2f,\"open_price\":%.2f,\"current_price\":%.2f,\"stop_loss\":%.2f,\"take_profit\":%.2f,\"floating_pl\":%.2f,\"swap\":%.2f,\"commission\":%.2f}",
                                  ticket,
                                  PositionGetString(POSITION_SYMBOL),
                                  (PositionGetInteger(POSITION_TYPE) == POSITION_TYPE_BUY ? "BUY" : "SELL"),
                                  PositionGetDouble(POSITION_VOLUME),
                                  PositionGetDouble(POSITION_PRICE_OPEN),
                                  PositionGetDouble(POSITION_PRICE_CURRENT),
                                  PositionGetDouble(POSITION_SL),
                                  PositionGetDouble(POSITION_TP),
                                  PositionGetDouble(POSITION_PROFIT),
                                  PositionGetDouble(POSITION_SWAP),
                                  0.0);
         count++;
      }
   }
   pos_json += "]";

   string payload = StringFormat("{\"installation_id\":\"%s\",\"positions\":%s}", g_xau_config.installation_id, pos_json);
   string resp;
   XauPilot_HttpPost("/ea/v1/positions/snapshot", payload, resp);
}

//+------------------------------------------------------------------+
//| XauPilot_ReportTradeEvent: Call inside Host EA OnTradeTransaction|
//+------------------------------------------------------------------+
void XauPilot_ReportTradeEvent(const MqlTradeTransaction &trans,
                              const MqlTradeRequest &request,
                              const MqlTradeResult &result)
{
   if(trans.type == TRADE_TRANSACTION_DEAL_ADD) {
      if(HistoryDealSelect(trans.deal)) {
         long magic = HistoryDealGetInteger(trans.deal, DEAL_MAGIC);
         if(magic == (long)g_xau_config.magic_number) {
            string payload = StringFormat("{\"installation_id\":\"%s\",\"magic_number\":%I64u,\"ticket\":%I64u,\"deal_type\":\"%s\",\"volume\":%.2f,\"price\":%.2f,\"profit\":%.2f,\"comment\":\"%s\"}",
                                          g_xau_config.installation_id,
                                          magic,
                                          trans.deal,
                                          (HistoryDealGetInteger(trans.deal, DEAL_TYPE) == DEAL_TYPE_BUY ? "BUY" : "SELL"),
                                          HistoryDealGetDouble(trans.deal, DEAL_VOLUME),
                                          HistoryDealGetDouble(trans.deal, DEAL_PRICE),
                                          HistoryDealGetDouble(trans.deal, DEAL_PROFIT),
                                          HistoryDealGetString(trans.deal, DEAL_COMMENT));
            string resp;
            XauPilot_HttpPost("/ea/v1/trades/event", payload, resp);
         }
      }
   }
}

//+------------------------------------------------------------------+
//| XauPilot_Deinit: Call inside Host EA OnDeinit()                  |
//+------------------------------------------------------------------+
void XauPilot_Deinit(const int reason) {
   EventKillTimer();
   g_xau_state = XAUPILOT_STATE_STOPPED;
   PrintFormat("[XAU-PILOT] Bridge deinitialized safely (Reason: %d).", reason);
}

// --- HTTP Helpers (WebRequest) ---
int XauPilot_HttpPost(const string endpoint, const string json_body, string &out_response) {
   char post_data[], result[];
   string result_headers;
   string headers = "Content-Type: application/json\r\nAuthorization: Bearer " + g_xau_config.auth_token + "\r\n";
   string url = g_xau_config.backend_url + endpoint;
   StringToCharArray(json_body, post_data, 0, WHOLE_ARRAY, CP_UTF8);

   ResetLastError();
   int code = WebRequest("POST", url, headers, XAUPILOT_DEFAULT_TIMEOUT_MS, post_data, result, result_headers);
   if(code == 200 || code == 201) {
      out_response = CharArrayToString(result, 0, WHOLE_ARRAY, CP_UTF8);
   }
   return code;
}

int XauPilot_HttpGet(const string endpoint, string &out_response) {
   char post_data[], result[];
   string result_headers;
   string headers = "Accept: application/json\r\nAuthorization: Bearer " + g_xau_config.auth_token + "\r\n";
   string url = g_xau_config.backend_url + endpoint;

   ResetLastError();
   int code = WebRequest("GET", url, headers, XAUPILOT_DEFAULT_TIMEOUT_MS, post_data, result, result_headers);
   if(code == 200) {
      out_response = CharArrayToString(result, 0, WHOLE_ARRAY, CP_UTF8);
   }
   return code;
}

// Simple JSON extraction helpers for MQL5
string XauPilot_JsonGetString(const string json, const string key) {
   string search = "\"" + key + "\":\"";
   int start = StringFind(json, search);
   if(start < 0) return "";
   start += StringLen(search);
   int end = StringFind(json, "\"", start);
   if(end < 0) return "";
   return StringSubstr(json, start, end - start);
}

double XauPilot_JsonGetDouble(const string json, const string key, const double def_val = 0.0) {
   string search = "\"" + key + "\":";
   int start = StringFind(json, search);
   if(start < 0) return def_val;
   start += StringLen(search);
   // Skip quote if any
   if(StringSubstr(json, start, 1) == "\"") start++;
   int end = StringFind(json, ",", start);
   if(end < 0) end = StringFind(json, "}", start);
   if(end < 0) return def_val;
   string val = StringSubstr(json, start, end - start);
   if(StringSubstr(val, StringLen(val) - 1, 1) == "\"") val = StringSubstr(val, 0, StringLen(val) - 1);
   return StringToDouble(val);
}

int XauPilot_JsonGetInt(const string json, const string key, const int def_val = 0) {
   return (int)XauPilot_JsonGetDouble(json, key, (double)def_val);
}
