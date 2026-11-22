package com.poultryprophet.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Shared PostgreSQL reservation and immutable outcome in the business transaction. */
@Service
public class CommandReceiptService {
    public record CommandIdentity(UUID operationId, int schemaVersion, Long farmId, Long userId,
                                  String entityType, String resourceType, Long resourceId) {}
    public record Execution<T>(T outcome, boolean replayed) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final LegacyCommandGuard legacy;

    public CommandReceiptService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactions, boolean enabled) {
        this(jdbc, mapper, transactions, enabled, new LegacyCommandGuard(jdbc));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CommandReceiptService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactions,
                                 @Value("${app.offline.command-receipts-enabled:false}") boolean enabled, LegacyCommandGuard legacy) {
        this.jdbc = jdbc; this.mapper = mapper; this.transaction = new TransactionTemplate(transactions); this.enabled = enabled; this.legacy = legacy;
    }
    public boolean isEnabled() { return enabled; }

    /** Fail closed unless the deployed table and an actual valid unique key agree. */
    public boolean isReady() {
        if (!enabled) return false;
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (
                      SELECT 1 FROM pg_index i JOIN pg_attribute a
                        ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
                      WHERE i.indrelid = to_regclass('offline_command_receipt')
                        AND i.indisunique AND i.indisvalid AND i.indisready AND i.indimmediate
                        AND i.indpred IS NULL AND i.indexprs IS NULL
                        AND i.indnkeyatts = 1 AND a.attname = 'operation_id'
                    ) AND EXISTS (
                      SELECT 1 FROM pg_class WHERE oid = to_regclass('offline_command_receipt') AND relrowsecurity
                    ) AND (
                      SELECT count(*) = 10 FROM pg_attribute a
                      JOIN (VALUES ('operation_id','uuid'), ('schema_version','integer'),
                        ('farm_id','bigint'), ('user_id','bigint'), ('entity_type','character varying(64)'),
                        ('resource_type','character varying(64)'), ('resource_id','bigint'),
                        ('payload','jsonb'), ('outcome','jsonb'), ('created_at','timestamp with time zone'))
                        AS required(name, type) ON a.attname = required.name
                        AND format_type(a.atttypid, a.atttypmod) = required.type
                      WHERE a.attrelid = to_regclass('offline_command_receipt') AND NOT a.attisdropped
                        AND (a.attnotnull OR a.attname = 'outcome')
                    )
                    """, Boolean.class));
        } catch (org.springframework.dao.DataAccessException ex) { return false; }
    }

    public <T> Execution<T> executeOnce(CommandIdentity id, JsonNode payload, Class<T> resultType, Supplier<T> apply) {
        if (!isReady()) throw new CommandReceiptsUnavailableException();
        if (id == null || id.operationId() == null || id.schemaVersion() < 1 || id.farmId() == null || id.farmId() <= 0
                || id.userId() == null || id.userId() <= 0 || id.resourceId() == null || id.resourceId() <= 0
                || id.entityType() == null || id.entityType().isBlank() || id.resourceType() == null || id.resourceType().isBlank()
                || payload == null || !payload.isObject()) throw new BadRequestException("Invalid command identity or payload");
        // Serialize once before invoking application code; callers cannot mutate the binding mid-flight.
        String json = payload.toString();
        return Objects.requireNonNull(transaction.execute(status -> {
            // ON CONFLICT waits for a competing transaction's decision. A rollback
            // frees the key; a commit makes its immutable outcome available below.
            int reserved = jdbc.update("INSERT INTO offline_command_receipt "
                            + "(operation_id, schema_version, farm_id, user_id, entity_type, resource_type, resource_id, payload) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb)) ON CONFLICT (operation_id) DO NOTHING",
                    id.operationId(), id.schemaVersion(), id.farmId(), id.userId(), id.entityType(), id.resourceType(), id.resourceId(), json);
            if (reserved == 0) {
                // Never fetch another actor's receipt body. JSONB provides numeric
                // equality (1 == 1.000), stable object-key ordering and exact arrays.
                var prior = jdbc.query("SELECT outcome::text FROM offline_command_receipt WHERE operation_id = ? "
                                + "AND schema_version = ? AND farm_id = ? AND user_id = ? AND entity_type = ? "
                                + "AND resource_type = ? AND resource_id = ? AND payload = CAST(? AS jsonb) AND outcome IS NOT NULL",
                        (rs, row) -> rs.getString(1), id.operationId(), id.schemaVersion(), id.farmId(), id.userId(),
                        id.entityType(), id.resourceType(), id.resourceId(), json);
                if (prior.size() != 1) throw new ConflictException("This command ID is already bound to different details or ownership.");
                try { return new Execution<>(mapper.readValue(prior.getFirst(), resultType), true); }
                catch (Exception ex) { throw new IllegalStateException("Stored command outcome could not be read", ex); }
            }
            legacy.requireUnclaimed(id.operationId(), id.entityType());
            T outcome = Objects.requireNonNull(apply.get(), "A command must have an outcome");
            try {
                jdbc.update("UPDATE offline_command_receipt SET outcome = CAST(? AS jsonb) WHERE operation_id = ?",
                        mapper.writeValueAsString(outcome), id.operationId());
            } catch (java.io.IOException ex) { throw new IllegalStateException("Command outcome could not be stored", ex); }
            return new Execution<>(outcome, false);
        }));
    }
}
