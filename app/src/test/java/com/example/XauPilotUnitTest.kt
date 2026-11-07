package com.example

import com.example.core.data.XauPilotRepository
import com.example.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class XauPilotUnitTest {

    private lateinit var repository: XauPilotRepository

    @Before
    fun setUp() {
        repository = XauPilotRepository.getInstance()
    }

    @Test
    fun testInitialAccountsLoaded() {
        val accounts = repository.accounts.value
        assertTrue("Accounts should not be empty", accounts.isNotEmpty())
        val demoAccount = accounts.find { it.environment == AccountEnvironment.DEMO }
        assertNotNull("Demo account must exist", demoAccount)
    }

    @Test
    fun testAccountRiskProfileEnforcement() = runBlocking {
        val installation = repository.installations.value.first()

        // Attempting to set an excessive lot size (e.g. 5.0 lots when limit is 0.50 lots)
        val excessiveValues = mapOf("lot_size" to "5.0")
        val result = repository.updateEaConfiguration(installation.id, excessiveValues)

        // Must be rejected by account risk profile!
        assertFalse("Excessive lot size exceeding account risk profile must be rejected", result)

        val audit = repository.configAuditHistory.value.firstOrNull()
        assertNotNull("Audit entry must be recorded", audit)
        assertEquals("Config status must be REJECTED", ConfigStatus.REJECTED, audit?.status)
    }

    @Test
    fun testValidConfigurationDeployment() = runBlocking {
        val installation = repository.installations.value.first()
        val initialVersion = installation.currentConfigVersion

        val safeValues = mapOf("lot_size" to "0.15", "risk_percent" to "1.8")
        val result = repository.updateEaConfiguration(installation.id, safeValues)

        assertTrue("Safe configuration within account limits must be accepted", result)
    }

    @Test
    fun testEmergencyStopPolicy() = runBlocking {
        val account = repository.accounts.value.first()
        val result = repository.sendAccountCommand(
            account.id,
            CommandType.EMERGENCY_STOP,
            EmergencyStopPolicy.CLOSE_NEW_ENTRIES_ONLY
        )
        assertTrue("Emergency stop command must be dispatched successfully", result)
    }

    @Test
    fun testAddAccountAndEaAssignment() {
        val newAccount = repository.addAccount(
            label = "Scalp VIP Demo",
            brokerName = "IC Markets Global",
            serverName = "ICMarkets-Demo03",
            accountNumber = "99881122",
            environment = AccountEnvironment.DEMO,
            initialBalance = 50000.0
        )
        assertTrue(newAccount.label.contains("Scalp VIP Demo"))

        val ea = repository.eaProducts.value.first()
        val inst = repository.assignEaToAccount(ea, newAccount, 778899, "M15")
        assertEquals(ea.id, inst.eaProductId)
        assertEquals(newAccount.id, inst.brokerAccountId)
        assertEquals(778899L, inst.magicNumber)
    }

    @Test
    fun testPlaceOrderAndClosePosition() = runBlocking {
        val account = repository.accounts.value.first()

        val pos = repository.placeOrder(
            accountId = account.id,
            eaInstallationId = "inst_01_pro_acc1",
            eaName = "Gold Pro EA",
            direction = TradeDirection.BUY,
            lotSize = 0.10,
            stopLoss = 2640.0,
            takeProfit = 2680.0,
            magicNumber = 108821
        )
        assertNotNull(pos)
        assertTrue(repository.positions.value.any { it.ticket == pos.ticket })

        val closed = repository.closePosition(pos.ticket)
        assertTrue(closed)
        assertFalse(repository.positions.value.any { it.ticket == pos.ticket })
        assertTrue(repository.tradeHistory.value.any { it.ticket == pos.ticket })
    }
}
