# Rollout and rollback by image digest

`scripts/rollout.sh` builds two images and moves between them by digest, not by tag. A tag can move. A digest names one image.

## Expand and contract

| Step | Migration | What changes | Old image safe? |
|---|---|---|---|
| Expand | `V2__payments_external_ref.sql` | Adds a nullable column `external_ref`. | Yes. The old image never reads or writes the column. |
| Use | `V3__payments_external_ref_index.sql` | Adds a unique index for non-null values. The new image writes and reads the column. | Yes. The old image inserts rows with the column NULL, and NULL values do not clash in the index. |
| Contract (not done) | A later migration that makes the column NOT NULL or drops an old column | Removes the old shape. | No. See below. |

## Why rollback is safe after V3

Flyway does not undo migrations. The database keeps V2 and V3 after a rollback. The old image needs only two things. It must start with Flyway ready to accept migrations that it does not know, and it must work on the new schema. The setting `spring.flyway.ignore-migration-patterns: "*:future"` allows the first. The additive schema allows the second.

The first rollout across this change has a gap. The old image `v1` does not carry the setting, because the setting arrived with V2. The script sets it for every image through the environment variable `SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS`. In a real deployment, ship the setting one release before the first expand step.

## When rollback is not safe

A later NOT NULL step on `external_ref` breaks the old image, because the old image inserts NULL. Dropping a column that the old image reads breaks it too. Do that step only after the old image is out of use and the rollback window has closed.

## Run it

```
flock heavy.lock nice -n 10 scripts/rollout.sh | tee rollout.txt
```

The script cleans up only the container it created (`ledger-app-roll`), the compose stack, and its own Git worktree. Evidence: `evidence/rollout-rollback-digest.txt`. The run is local and measured on one laptop.
