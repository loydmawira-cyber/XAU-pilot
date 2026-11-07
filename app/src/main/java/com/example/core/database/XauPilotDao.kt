package com.example.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface XauPilotDao {

    // Broker Accounts
    @Query("SELECT * FROM broker_accounts ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<BrokerAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: BrokerAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<BrokerAccountEntity>)

    @Update
    suspend fun updateAccount(account: BrokerAccountEntity)

    @Query("DELETE FROM broker_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    // EA Installations
    @Query("SELECT * FROM ea_installations ORDER BY id ASC")
    fun getAllInstallations(): Flow<List<EaInstallationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallation(installation: EaInstallationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallations(installations: List<EaInstallationEntity>)

    @Update
    suspend fun updateInstallation(installation: EaInstallationEntity)

    @Query("DELETE FROM ea_installations WHERE id = :id")
    suspend fun deleteInstallationById(id: String)

    @Query("DELETE FROM ea_installations WHERE brokerAccountId = :accountId")
    suspend fun deleteInstallationsByAccount(accountId: String)

    // EA Licenses
    @Query("SELECT * FROM ea_licenses ORDER BY id ASC")
    fun getAllLicenses(): Flow<List<EaLicenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLicense(license: EaLicenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLicenses(licenses: List<EaLicenseEntity>)

    // Open Positions
    @Query("SELECT * FROM positions ORDER BY openTimeUtc DESC")
    fun getAllPositions(): Flow<List<PositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosition(position: PositionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPositions(positions: List<PositionEntity>)

    @Update
    suspend fun updatePosition(position: PositionEntity)

    @Query("DELETE FROM positions WHERE ticket = :ticket")
    suspend fun deletePositionByTicket(ticket: Long)

    @Query("DELETE FROM positions WHERE brokerAccountId = :accountId")
    suspend fun deletePositionsByAccount(accountId: String)

    // Closed Trades
    @Query("SELECT * FROM closed_trades ORDER BY closeTimeUtc DESC")
    fun getAllClosedTrades(): Flow<List<ClosedTradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClosedTrade(trade: ClosedTradeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClosedTrades(trades: List<ClosedTradeEntity>)

    // Presets
    @Query("SELECT * FROM presets ORDER BY isSystemDefault DESC, name ASC")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<PresetEntity>)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deletePresetById(id: String)

    // Config Audit
    @Query("SELECT * FROM config_audit ORDER BY id DESC")
    fun getAllConfigAudits(): Flow<List<ConfigAuditEntity>>

    @Insert
    suspend fun insertConfigAudit(audit: ConfigAuditEntity)

    // Risk Profiles
    @Query("SELECT * FROM risk_profiles")
    fun getAllRiskProfiles(): Flow<List<RiskProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRiskProfile(profile: RiskProfileEntity)
}
