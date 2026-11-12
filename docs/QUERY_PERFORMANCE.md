# Input-history query check

Selection Review and Batch Comparison now send the farm, batch and half-open date range
to PostgreSQL instead of loading all input records for a batch and filtering them in Java.
The boundaries are midnight in the configured application time zone, with the day after the
end date excluded.

On a disposable local PostgreSQL 18 cluster, a synthetic temporary table with 100,000 hourly
records and an index on `(farm_id, batch_id, recorded_at DESC)` produced these results with
`EXPLAIN (ANALYZE, BUFFERS)`:

| Query | Returned rows | Database execution time |
| --- | ---: | ---: |
| All records for one batch | 100,000 | 19.590 ms |
| Seven-day date range | 168 | 0.101 ms |

This comparison excludes network transfer, object creation and application processing. It is
not a production benchmark or a guarantee for other data distributions. The legacy V20 script
defines a matching index; an operator must verify the actual database has it rather than
assuming that a legacy script ran.

The offline bootstrap still makes several calls per batch, and other queries need individual
measurement and review. Do not enable schema-changing initialization to add indexes to an
existing farm database; follow the reviewed migration process.
