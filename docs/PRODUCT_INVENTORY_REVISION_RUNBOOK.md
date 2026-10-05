# Product Inventory Revision Runbook

## Before first use

1. Back up the validation database.
2. Apply `src/main/resources/db/migration/V8__lightweight_product_inventory.sql` to the validation database.
3. Start the backend and frontend. The application may create missing Hibernate structures, but the reviewed SQL migration is still required for constraints, indexes, and legacy status values.
4. Confirm the manager account can open `/inputs`.

Do not apply the migration directly to production until the validation database and adviser/stakeholder dry run pass.

## Manager workflow

1. Open `Product history`.
2. In `Farm products and stock`, choose `Add product`.
3. Enter the common farm product, such as `Baby Stag Booster`, its type, and practical unit such as `pack` or `sachet`.
4. Enter starting stock if known, or leave it at zero.
5. Use `Add stock` whenever new product is received.
6. Enter a cost only when the purchase should be recorded as an expense. Product use does not create another expense.
7. Choose a dedicated batch only when the purchase genuinely belongs to that batch. Shared purchases remain farm-wide.

## Handler workflow

1. Open the intended batch.
2. Choose `Product used`.
3. Select a listed product when available.
4. Enter the practical quantity used; the default is one unit.
5. Choose the purpose for medicine or vaccine records.
6. Save. The product-use record is queued offline when needed and the server deducts stock when the operation syncs.

If the product is not listed, enter it as a free-text product. The intervention is preserved, but it is marked untracked and does not deduct stock.

## Stock review

If stock is insufficient, the intervention is not discarded and stock is not made negative. It appears under `Stock review needed` for the manager. Add or correct stock, then choose `Apply now` to retry the deduction once.

## Batch report

The report shows current population, health-related deaths, grouped product use, and recorded batch finance. Finance is shown as **recorded net cash flow**, not profit. Farm-wide costs and unentered costs are not silently assigned to a batch.

## API smoke checks

- `GET /api/inventory/products`
- `POST /api/inventory/products`
- `POST /api/inventory/products/{productId}/stock-ins`
- `POST /api/inputs` with `farmProductId` and `quantity`
- `GET /api/inventory/movements`
- `GET /api/inventory/pending-review`
- `POST /api/inventory/pending-review/{inputId}/apply`

All endpoints remain farm-scoped through the authenticated user. Manager-only endpoints must reject handler credentials.
