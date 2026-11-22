package com.poultryprophet.sync;

import com.poultryprophet.common.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.UUID;

/** Do not manufacture an immutable receipt from a legacy mutable entity. */
@Component
public class LegacyCommandGuard {
    private final JdbcTemplate jdbc;
    public LegacyCommandGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void requireUnclaimed(UUID id, String entityType) {
        var sources = jdbc.query("""
                SELECT table_name, id_column, id_kind FROM (VALUES
                  ('batch_event','operation_id','UUID'), ('farm_input_log','operation_id','UUID'),
                  ('batch_selection_session','operation_id','UUID'), ('batch_sex_composition','operation_id','UUID'),
                  ('handler_task_status_history','operation_id','UUID'), ('batch_vaccination_plan_item','completion_operation_id','UUID'),
                  ('inventory_movement','operation_id','UUID'),
                  ('farm_financial_transaction','source_operation_id','TEXT'),
                  ('offline_sync_receipt','operation_id','UUID'), ('offline_daily_record_receipt','operation_id','UUID')
                ) AS source(table_name,id_column,id_kind) WHERE to_regclass(table_name) IS NOT NULL
                """, (rs, row) -> new String[]{rs.getString(1),rs.getString(2),rs.getString(3)});
        var parts = new ArrayList<String>(); var args = new ArrayList<Object>();
        for (var source : sources) {
            // V27 stored full daily scope/payload/instant, so its explicit daily
            // adapter can verify that evidence without replaying a domain write.
            if (entityType.equals("DAILY_RECORD") && source[0].equals("offline_daily_record_receipt")) continue;
            // Identifiers come only from the constant source list above.
            parts.add("SELECT 1 FROM " + source[0] + " WHERE " + source[1] + " = ?"); args.add(source[2].equals("TEXT") ? id.toString() : id);
        }
        if (!parts.isEmpty() && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (" + String.join(" UNION ALL ", parts) + ")", Boolean.class, args.toArray())))
            throw new ConflictException("This legacy command ID already has farm evidence but no shared receipt. Keep the original queued record for review; it was not reapplied.");
    }
}
