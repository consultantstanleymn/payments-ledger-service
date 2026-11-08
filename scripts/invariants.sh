#!/usr/bin/env bash
# SQL invariant check against the compose Postgres. Prints violation counts. Exit 1 if any is not 0.
set -euo pipefail
PSQL=("${CONTAINER_CLI:-podman}" exec -i "${CONTAINER:-ledger_postgres_1}" psql -U ledger -d ledger -At)
# wait (up to 60 s) for the Kafka consumer to catch up with the outbox
for _ in $(seq 1 60); do
  pending=$("${PSQL[@]}" -c "SELECT (SELECT count(*) FROM outbox_events WHERE sent_at IS NULL) + (SELECT count(*) FROM outbox_events o WHERE NOT EXISTS (SELECT 1 FROM processed_events p WHERE p.event_id = o.event_id))")
  [ "$pending" = "0" ] && break
  sleep 1
done
read -r unbalanced below_floor cache_diff proj_diff total_sum < <("${PSQL[@]}" -F ' ' -c "
SELECT
 (SELECT count(*) FROM (SELECT txn_id FROM journal_entries GROUP BY txn_id HAVING sum(amount_minor) <> 0) x),
 (SELECT count(*) FROM accounts WHERE posted_minor - held_minor < floor_minor),
 (SELECT count(*) FROM accounts a WHERE a.posted_minor <> COALESCE((SELECT sum(amount_minor) FROM journal_entries e WHERE e.account_id = a.id), 0)),
 (SELECT count(*) FROM accounts a LEFT JOIN balance_projection p ON p.account_id = a.id
    WHERE COALESCE(p.posted_minor, 0) <> a.posted_minor OR COALESCE(p.held_minor, 0) <> a.held_minor),
 (SELECT COALESCE(sum(posted_minor), 0) FROM accounts)")
echo "unbalanced_transactions=$unbalanced accounts_below_floor=$below_floor cache_vs_journal_diffs=$cache_diff projection_vs_ledger_diffs=$proj_diff sum_of_all_balances=$total_sum outbox_pending=$pending"
[ "$unbalanced$below_floor$cache_diff$proj_diff$total_sum" = "00000" ]
